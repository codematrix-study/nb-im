package com.cw.im.monitoring;

import com.cw.im.core.ReliabilityMetrics;
import com.cw.im.kafka.KafkaProducerManager;
import com.cw.im.kafka.KafkaConsumerService;
import com.cw.im.monitoring.admin.AdminApiServer;
import com.cw.im.monitoring.alerting.AlertingEngine;
import com.cw.im.monitoring.dashboard.MetricsExporter;
import com.cw.im.monitoring.health.*;
import com.cw.im.monitoring.performance.PerformanceMonitor;
import com.cw.im.redis.RedisManager;
import com.cw.im.server.channel.ChannelManager;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 监控管理器
 *
 * <p>统一管理和协调所有监控组件</p>
 *
 * <h3>核心功能</h3>
 * <ul>
 *     <li>初始化和启动所有监控组件</li>
 *     <li>协调各组件之间的数据流动</li>
 *     <li>提供统一的监控数据查询接口</li>
 *     <li>定期更新告警引擎的指标数据</li>
 * </ul>
 *
 * @author cw
 * @since 1.0.0
 */
@Slf4j
public class MonitoringManager {

    // 核心组件
    private final ChannelManager channelManager;
    private final RedisManager redisManager;
    private final KafkaProducerManager kafkaProducer;
    private final KafkaConsumerService kafkaConsumerService;
    private final ReliabilityMetrics reliabilityMetrics;

    // 监控组件
    private final List<MetricsCollector> metricsCollectors;
    private final HealthCheckService healthCheckService;
    private final PerformanceMonitor performanceMonitor;
    private final AlertingEngine alertingEngine;
    private final MetricsExporter metricsExporter;
    private AdminApiServer adminApiServer;

    // 定时任务
    private ScheduledExecutorService scheduler;

    /**
     * 构造函数
     */
    public MonitoringManager(
            ChannelManager channelManager,
            RedisManager redisManager,
            KafkaProducerManager kafkaProducer,
            KafkaConsumerService kafkaConsumerService,
            ReliabilityMetrics reliabilityMetrics,
            int adminApiPort) {
        this.channelManager = channelManager;
        this.redisManager = redisManager;
        this.kafkaProducer = kafkaProducer;
        this.kafkaConsumerService = kafkaConsumerService;
        this.reliabilityMetrics = reliabilityMetrics;

        // 初始化指标采集器
        this.metricsCollectors = new ArrayList<>();
        this.metricsCollectors.add(new JVMMetricsCollector());
        if (channelManager != null) {
            this.metricsCollectors.add(new NettyMetricsCollector(channelManager));
        }
        if (redisManager != null) {
            this.metricsCollectors.add(new RedisMetricsCollector(redisManager));
        }
        if (kafkaProducer != null || kafkaConsumerService != null) {
            this.metricsCollectors.add(new KafkaMetricsCollector(kafkaProducer, kafkaConsumerService));
        }

        // 初始化健康检查服务
        this.healthCheckService = new HealthCheckService(30);
        registerHealthCheckers();

        // 初始化性能监控器
        this.performanceMonitor = new PerformanceMonitor();

        // 初始化告警引擎
        this.alertingEngine = new AlertingEngine(60);
        this.alertingEngine.initDefaultRules();

        // 初始化指标导出器
        this.metricsExporter = new MetricsExporter(
                metricsCollectors,
                healthCheckService,
                performanceMonitor,
                alertingEngine
        );

        log.info("监控管理器初始化完成");
    }

    /**
     * 注册健康检查器
     */
    private void registerHealthCheckers() {
        if (redisManager != null) {
            healthCheckService.registerChecker(new RedisHealthChecker(redisManager));
        }
        if (kafkaProducer != null) {
            healthCheckService.registerChecker(new KafkaHealthChecker(kafkaProducer));
        }
        if (channelManager != null) {
            healthCheckService.registerChecker(new NettyHealthChecker(channelManager));
        }
    }

    /**
     * 启动监控管理器
     */
    public void start() {
        log.info("正在启动监控管理器...");

        try {
            // 1. 启动指标采集器
            for (MetricsCollector collector : metricsCollectors) {
                collector.start();
                log.info("指标采集器已启动: name={}", collector.getName());
            }

            // 2. 启动健康检查服务
            healthCheckService.start();
            log.info("健康检查服务已启动");

            // 3. 启动告警引擎
            alertingEngine.start();
            log.info("告警引擎已启动");

            // 4. 启动Admin API服务器
            adminApiServer = new AdminApiServer(
                    8081, // 默认管理API端口
                    metricsCollectors,
                    healthCheckService,
                    performanceMonitor,
                    alertingEngine,
                    channelManager,
                    reliabilityMetrics
            );
            adminApiServer.start();
            log.info("管理API服务器已启动");

            // 5. 启动定期指标更新任务
            startMetricsUpdateTask();

            log.info("监控管理器启动完成");

        } catch (Exception e) {
            log.error("启动监控管理器失败", e);
            throw new RuntimeException("启动监控管理器失败", e);
        }
    }

    /**
     * 启动定期指标更新任务
     */
    private void startMetricsUpdateTask() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "monitoring-metrics-updater");
            thread.setDaemon(true);
            return thread;
        });

        // 每60秒更新一次告警引擎的指标数据
        scheduler.scheduleAtFixedRate(
                this::updateAlertMetrics,
                30,
                60,
                TimeUnit.SECONDS
        );

        log.info("定期指标更新任务已启动");
    }

    /**
     * 更新告警引擎的指标数据
     */
    private void updateAlertMetrics() {
        try {
            // 更新可靠性指标
            if (reliabilityMetrics != null) {
                var metricsData = reliabilityMetrics.collectMetrics();
                alertingEngine.updateMetric("success_rate", metricsData.getSendSuccessRate());
                alertingEngine.updateMetric("p99_latency", metricsData.getP99DeliveryLatency());
                alertingEngine.updateMetric("error_rate", 100.0 - metricsData.getDeliverySuccessRate());
                alertingEngine.updateMetric("dead_letter_count", metricsData.getDeadLetterCount());
            }

            // 更新性能指标
            if (performanceMonitor != null) {
                var perfMetrics = performanceMonitor.getMetrics(
                        com.cw.im.monitoring.performance.PerformanceMetrics.MetricType.MESSAGE_SEND
                );
                if (perfMetrics != null) {
                    alertingEngine.updateMetric("message_throughput", perfMetrics.getThroughput());
                    alertingEngine.updateMetric("message_avg_latency", perfMetrics.getAvgLatency());
                }
            }

            // 更新系统指标
            for (MetricsCollector collector : metricsCollectors) {
                if ("redis".equals(collector.getName())) {
                    var metrics = collector.collectMetrics();
                    Object latency = metrics.get("redis_ping_latency_ms");
                    if (latency instanceof Number) {
                        alertingEngine.updateMetric("redis_ping_latency_ms",
                                ((Number) latency).doubleValue());
                    }
                }
            }

            log.debug("告警引擎指标已更新");

        } catch (Exception e) {
            log.error("更新告警指标失败", e);
        }
    }

    /**
     * 停止监控管理器
     */
    public void stop() {
        log.info("正在停止监控管理器...");

        try {
            // 停止定时任务
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

            // 停止Admin API服务器
            if (adminApiServer != null) {
                adminApiServer.shutdown();
            }

            // 停止告警引擎
            alertingEngine.stop();

            // 停止健康检查服务
            healthCheckService.stop();

            // 停止指标采集器
            for (MetricsCollector collector : metricsCollectors) {
                collector.stop();
            }

            log.info("监控管理器已停止");

        } catch (Exception e) {
            log.error("停止监控管理器失败", e);
        }
    }

    // ==================== Getter方法 ====================

    public List<MetricsCollector> getMetricsCollectors() {
        return metricsCollectors;
    }

    public HealthCheckService getHealthCheckService() {
        return healthCheckService;
    }

    public PerformanceMonitor getPerformanceMonitor() {
        return performanceMonitor;
    }

    public AlertingEngine getAlertingEngine() {
        return alertingEngine;
    }

    public MetricsExporter getMetricsExporter() {
        return metricsExporter;
    }

    public AdminApiServer getAdminApiServer() {
        return adminApiServer;
    }
}
