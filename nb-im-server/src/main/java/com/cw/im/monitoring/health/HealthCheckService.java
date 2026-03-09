package com.cw.im.monitoring.health;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 健康检查服务
 *
 * <p>统一管理和执行所有组件的健康检查</p>
 *
 * <h3>功能特性</h3>
 * <ul>
 *     <li>注册和管理多个健康检查器</li>
 *     <li>定期执行健康检查</li>
 *     <li>提供整体健康状态</li>
 *     <li>支持单独检查指定组件</li>
 * </ul>
 *
 * @author cw
 * @since 1.0.0
 */
@Slf4j
public class HealthCheckService {

    /**
     * 健康检查器注册表
     */
    private final Map<String, HealthChecker> checkers = new ConcurrentHashMap<>();

    /**
     * 最后一次检查结果
     */
    private final Map<String, HealthCheck> lastResults = new ConcurrentHashMap<>();

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
    public HealthCheckService(long checkIntervalSeconds) {
        this.checkIntervalSeconds = checkIntervalSeconds;
    }

    /**
     * 构造函数（默认30秒检查间隔）
     */
    public HealthCheckService() {
        this(30);
    }

    /**
     * 注册健康检查器
     *
     * @param checker 健康检查器
     */
    public void registerChecker(HealthChecker checker) {
        if (checker != null && checker.isEnabled()) {
            checkers.put(checker.getName(), checker);
            log.info("注册健康检查器: name={}", checker.getName());
        }
    }

    /**
     * 注销健康检查器
     *
     * @param name 检查器名称
     */
    public void unregisterChecker(String name) {
        HealthChecker removed = checkers.remove(name);
        if (removed != null) {
            log.info("注销健康检查器: name={}", name);
        }
    }

    /**
     * 启动健康检查服务
     */
    public void start() {
        if (running) {
            log.warn("健康检查服务已在运行中");
            return;
        }

        running = true;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "health-check-scheduler");
            thread.setDaemon(true);
            return thread;
        });

        // 定期执行健康检查
        scheduler.scheduleAtFixedRate(
                this::performHealthChecks,
                0,
                checkIntervalSeconds,
                TimeUnit.SECONDS
        );

        log.info("健康检查服务已启动: 检查间隔={}s", checkIntervalSeconds);
    }

    /**
     * 停止健康检查服务
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

        log.info("健康检查服务已停止");
    }

    /**
     * 执行所有健康检查
     */
    private void performHealthChecks() {
        for (HealthChecker checker : checkers.values()) {
            try {
                HealthCheck result = checker.check();
                lastResults.put(checker.getName(), result);

                // 记录不健康的组件
                if (!result.isHealthy()) {
                    log.warn("组件健康检查失败: name={}, status={}, description={}",
                            checker.getName(), result.getStatus(), result.getDescription());
                }

            } catch (Exception e) {
                log.error("健康检查异常: name={}", checker.getName(), e);
                HealthCheck failed = HealthCheck.down(
                        checker.getName(),
                        "健康检查异常: " + e.getMessage()
                );
                lastResults.put(checker.getName(), failed);
            }
        }
    }

    /**
     * 获取整体健康状态
     *
     * @return 整体健康状态
     */
    public HealthStatus getOverallStatus() {
        if (lastResults.isEmpty()) {
            return HealthStatus.UNKNOWN;
        }

        boolean allUp = lastResults.values().stream()
                .allMatch(HealthCheck::isHealthy);

        boolean anyDown = lastResults.values().stream()
                .anyMatch(check -> check.getStatus() == HealthCheck.HealthStatus.DOWN);

        boolean anyDegraded = lastResults.values().stream()
                .anyMatch(check -> check.getStatus() == HealthCheck.HealthStatus.DEGRADED);

        if (anyDown) {
            return HealthStatus.DOWN;
        } else if (anyDegraded) {
            return HealthStatus.DEGRADED;
        } else if (allUp) {
            return HealthStatus.UP;
        } else {
            return HealthStatus.UNKNOWN;
        }
    }

    /**
     * 检查服务是否健康
     *
     * @return true-健康, false-不健康
     */
    public boolean isHealthy() {
        return getOverallStatus() == HealthStatus.UP;
    }

    /**
     * 获取所有健康检查结果
     *
     * @return 健康检查结果列表
     */
    public List<HealthCheck> getAllResults() {
        return new ArrayList<>(lastResults.values());
    }

    /**
     * 获取指定组件的健康检查结果
     *
     * @param name 组件名称
     * @return 健康检查结果，如果不存在则返回null
     */
    public HealthCheck getResult(String name) {
        return lastResults.get(name);
    }

    /**
     * 立即执行一次健康检查
     *
     * @return 所有检查结果
     */
    public List<HealthCheck> checkNow() {
        performHealthChecks();
        return getAllResults();
    }

    /**
     * 获取健康状态摘要
     *
     * @return 状态摘要字符串
     */
    public String getStatusSummary() {
        HealthStatus overall = getOverallStatus();
        long upCount = lastResults.values().stream()
                .filter(check -> check.getStatus() == HealthCheck.HealthStatus.UP)
                .count();
        long downCount = lastResults.values().stream()
                .filter(check -> check.getStatus() == HealthCheck.HealthStatus.DOWN)
                .count();
        long degradedCount = lastResults.values().stream()
                .filter(check -> check.getStatus() == HealthCheck.HealthStatus.DEGRADED)
                .count();

        return String.format("HealthCheckService{status=%s, up=%d, degraded=%d, down=%d, total=%d}",
                overall, upCount, degradedCount, downCount, lastResults.size());
    }

    /**
     * 健康状态枚举
     */
    public enum HealthStatus {
        UP, DOWN, DEGRADED, UNKNOWN
    }
}
