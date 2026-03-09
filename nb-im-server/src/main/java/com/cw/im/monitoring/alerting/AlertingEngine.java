package com.cw.im.monitoring.alerting;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 告警引擎
 *
 * <p>负责管理和执行告警规则，当指标满足告警条件时触发告警</p>
 *
 * <h3>核心功能</h3>
 * <ul>
 *     <li>注册和管理告警规则</li>
 *     <li>定期检查指标是否触发告警</li>
 *     <li>支持多个告警处理器</li>
 *     <li>告警去重和告警恢复检测</li>
 *     <li>可配置的告警阈值和级别</li>
 * </ul>
 *
 * @author cw
 * @since 1.0.0
 */
@Slf4j
public class AlertingEngine {

    /**
     * 告警规则列表
     */
    private final List<AlertRule> rules = new CopyOnWriteArrayList<>();

    /**
     * 告警处理器列表
     */
    private final List<AlertHandler> handlers = new CopyOnWriteArrayList<>();

    /**
     * 活跃的告警（按规则名称分组）
     */
    private final Map<String, Alert> activeAlerts = new ConcurrentHashMap<>();

    /**
     * 规则首次触发时间（用于持续时间判断）
     */
    private final Map<String, LocalDateTime> ruleFirstTriggerTime = new ConcurrentHashMap<>();

    /**
     * 指标值缓存（用于告警恢复检测）
     */
    private final Map<String, Double> metricValues = new ConcurrentHashMap<>();

    /**
     * 定时任务执行器
     */
    private ScheduledExecutorService scheduler;

    /**
     * 是否正在运行
     */
    private volatile boolean running = false;

    /**
     * 检查间隔（秒）
     */
    private final long checkIntervalSeconds;

    /**
     * 构造函数
     *
     * @param checkIntervalSeconds 检查间隔（秒）
     */
    public AlertingEngine(long checkIntervalSeconds) {
        this.checkIntervalSeconds = checkIntervalSeconds;
    }

    /**
     * 构造函数（默认60秒检查间隔）
     */
    public AlertingEngine() {
        this(60);
    }

    /**
     * 添加告警规则
     *
     * @param rule 告警规则
     */
    public void addRule(AlertRule rule) {
        if (rule != null) {
            rules.add(rule);
            log.info("添加告警规则: name={}, metric={}, operator={}, threshold={}, level={}",
                    rule.getName(), rule.getMetricName(), rule.getOperator(),
                    rule.getThreshold(), rule.getLevel());
        }
    }

    /**
     * 移除告警规则
     *
     * @param ruleName 规则名称
     */
    public void removeRule(String ruleName) {
        rules.removeIf(rule -> rule.getName().equals(ruleName));
        ruleFirstTriggerTime.remove(ruleName);
        activeAlerts.remove(ruleName);
        log.info("移除告警规则: name={}", ruleName);
    }

    /**
     * 添加告警处理器
     *
     * @param handler 告警处理器
     */
    public void addHandler(AlertHandler handler) {
        if (handler != null && handler.isEnabled()) {
            handlers.add(handler);
            log.info("添加告警处理器: name={}", handler.getName());
        }
    }

    /**
     * 移除告警处理器
     *
     * @param handlerName 处理器名称
     */
    public void removeHandler(String handlerName) {
        handlers.removeIf(handler -> handler.getName().equals(handlerName));
        log.info("移除告警处理器: name={}", handlerName);
    }

    /**
     * 启动告警引擎
     */
    public void start() {
        if (running) {
            log.warn("告警引擎已在运行中");
            return;
        }

        running = true;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "alerting-engine");
            thread.setDaemon(true);
            return thread;
        });

        // 定期检查告警规则
        scheduler.scheduleAtFixedRate(
                this::checkAlerts,
                0,
                checkIntervalSeconds,
                TimeUnit.SECONDS
        );

        log.info("告警引擎已启动: 检查间隔={}s", checkIntervalSeconds);
    }

    /**
     * 停止告警引擎
     */
    public void stop() {
        if (!running) {
            return;
        }

        running = false;

        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        log.info("告警引擎已停止");
    }

    /**
     * 更新指标值
     *
     * @param metricName 指标名称
     * @param value      指标值
     */
    public void updateMetric(String metricName, double value) {
        metricValues.put(metricName, value);
    }

    /**
     * 检查告警规则
     */
    private void checkAlerts() {
        for (AlertRule rule : rules) {
            try {
                checkRule(rule);
            } catch (Exception e) {
                log.error("检查告警规则异常: rule={}", rule.getName(), e);
            }
        }
    }

    /**
     * 检查单个告警规则
     *
     * @param rule 告警规则
     */
    private void checkRule(AlertRule rule) {
        // 获取指标值
        Double value = metricValues.get(rule.getMetricName());
        if (value == null) {
            log.debug("指标值不存在，跳过检查: metric={}", rule.getMetricName());
            return;
        }

        boolean matches = rule.matches(value);

        if (matches) {
            // 满足告警条件
            handleTriggeredRule(rule, value);
        } else {
            // 不满足告警条件，检查是否需要恢复告警
            handleResolvedRule(rule, value);
        }
    }

    /**
     * 处理触发的告警规则
     *
     * @param rule  告警规则
     * @param value 当前值
     */
    private void handleTriggeredRule(AlertRule rule, double value) {
        LocalDateTime now = LocalDateTime.now();

        // 记录首次触发时间
        ruleFirstTriggerTime.putIfAbsent(rule.getName(), now);
        LocalDateTime firstTriggerTime = ruleFirstTriggerTime.get(rule.getName());

        // 检查是否满足持续时间要求
        long durationSeconds = java.time.Duration.between(firstTriggerTime, now).getSeconds();
        if (durationSeconds < rule.getDurationSeconds()) {
            log.debug("告警条件已满足，但未达到持续时间要求: rule={}, duration={}/{}s",
                    rule.getName(), durationSeconds, rule.getDurationSeconds());
            return;
        }

        // 检查是否已有活跃告警
        if (activeAlerts.containsKey(rule.getName())) {
            // 告警已存在，不需要重复触发
            return;
        }

        // 创建并触发告警
        Alert alert = createAlert(rule, value);
        activeAlerts.put(rule.getName(), alert);

        // 调用所有处理器
        for (AlertHandler handler : handlers) {
            try {
                handler.handle(alert);
            } catch (Exception e) {
                log.error("告警处理异常: handler={}, alert={}", handler.getName(), alert.getAlertId(), e);
            }
        }

        log.warn("告警触发: rule={}, level={}, metric={}, currentValue={}, threshold={}",
                rule.getName(), rule.getLevel(), rule.getMetricName(), value, rule.getThreshold());
    }

    /**
     * 处理已恢复的告警规则
     *
     * @param rule  告警规则
     * @param value 当前值
     */
    private void handleResolvedRule(AlertRule rule, double value) {
        // 清除首次触发时间
        ruleFirstTriggerTime.remove(rule.getName());

        // 检查是否有活跃告警
        Alert alert = activeAlerts.get(rule.getName());
        if (alert != null) {
            // 标记告警已恢复
            alert.setResolved(true);
            alert.setResolvedAt(LocalDateTime.now());
            activeAlerts.remove(rule.getName());

            log.info("告警已恢复: rule={}, metric={}, currentValue={}, threshold={}",
                    rule.getName(), rule.getMetricName(), value, rule.getThreshold());
        }
    }

    /**
     * 创建告警信息
     *
     * @param rule  告警规则
     * @param value 当前值
     * @return 告警信息
     */
    private Alert createAlert(AlertRule rule, double value) {
        return Alert.builder()
                .alertId(UUID.randomUUID().toString())
                .ruleName(rule.getName())
                .level(rule.getLevel())
                .title(rule.getName() + "告警")
                .description(rule.getDescription())
                .details(String.format("指标 %s 当前值 %.2f %s 阈值 %.2f",
                        rule.getMetricName(), value,
                        operatorToString(rule.getOperator()), rule.getThreshold()))
                .metricName(rule.getMetricName())
                .currentValue(value)
                .threshold(rule.getThreshold())
                .timestamp(LocalDateTime.now())
                .resolved(false)
                .build();
    }

    /**
     * 转换操作符为字符串
     */
    private String operatorToString(AlertRule.Operator operator) {
        switch (operator) {
            case GREATER_THAN:
                return ">";
            case LESS_THAN:
                return "<";
            case EQUAL:
                return "==";
            case GREATER_THAN_OR_EQUAL:
                return ">=";
            case LESS_THAN_OR_EQUAL:
                return "<=";
            case NOT_EQUAL:
                return "!=";
            default:
                return "?";
        }
    }

    /**
     * 获取所有活跃告警
     *
     * @return 活跃告警列表
     */
    public List<Alert> getActiveAlerts() {
        return new ArrayList<>(activeAlerts.values());
    }

    /**
     * 获取所有规则
     *
     * @return 规则列表
     */
    public List<AlertRule> getRules() {
        return new ArrayList<>(rules);
    }

    /**
     * 获取告警统计
     *
     * @return 统计信息
     */
    public String getStats() {
        return String.format("AlertingEngine{rules=%d, handlers=%d, activeAlerts=%d}",
                rules.size(), handlers.size(), activeAlerts.size());
    }

    /**
     * 初始化默认规则
     */
    public void initDefaultRules() {
        // 成功率低于95%告警
        addRule(AlertRule.builder()
                .name("success_rate_low")
                .description("消息成功率过低")
                .metricName("success_rate")
                .operator(AlertRule.Operator.LESS_THAN)
                .threshold(95.0)
                .level(Alert.AlertLevel.WARNING)
                .enabled(true)
                .durationSeconds(60)
                .build());

        // P99延迟超过100ms告警
        addRule(AlertRule.builder()
                .name("p99_latency_high")
                .description("P99延迟过高")
                .metricName("p99_latency")
                .operator(AlertRule.Operator.GREATER_THAN)
                .threshold(100.0)
                .level(Alert.AlertLevel.WARNING)
                .enabled(true)
                .durationSeconds(60)
                .build());

        // 错误率超过5%告警
        addRule(AlertRule.builder()
                .name("error_rate_high")
                .description("错误率过高")
                .metricName("error_rate")
                .operator(AlertRule.Operator.GREATER_THAN)
                .threshold(5.0)
                .level(Alert.AlertLevel.CRITICAL)
                .enabled(true)
                .durationSeconds(30)
                .build());

        // 死信队列过大告警
        addRule(AlertRule.builder()
                .name("dead_letter_queue_large")
                .description("死信队列消息过多")
                .metricName("dead_letter_count")
                .operator(AlertRule.Operator.GREATER_THAN)
                .threshold(1000.0)
                .level(Alert.AlertLevel.WARNING)
                .enabled(true)
                .durationSeconds(60)
                .build());

        // Redis响应慢告警
        addRule(AlertRule.builder()
                .name("redis_latency_high")
                .description("Redis响应延迟过高")
                .metricName("redis_ping_latency_ms")
                .operator(AlertRule.Operator.GREATER_THAN)
                .threshold(1000.0)
                .level(Alert.AlertLevel.WARNING)
                .enabled(true)
                .durationSeconds(60)
                .build());

        // 添加默认的日志处理器
        addHandler(new LogAlertHandler());

        log.info("默认告警规则已初始化");
    }
}
