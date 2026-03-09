package com.cw.im.monitoring.admin;

import com.cw.im.core.ReliabilityMetrics;
import com.cw.im.monitoring.MetricsCollector;
import com.cw.im.monitoring.alerting.Alert;
import com.cw.im.monitoring.alerting.AlertingEngine;
import com.cw.im.monitoring.health.HealthCheck;
import com.cw.im.monitoring.health.HealthCheckService;
import com.cw.im.monitoring.performance.PerformanceMetrics;
import com.cw.im.monitoring.performance.PerformanceMonitor;
import com.cw.im.server.channel.ChannelManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 监控服务
 *
 * <p>聚合所有监控数据，提供统一的查询接口</p>
 *
 * @author cw
 * @since 1.0.0
 */
@Slf4j
public class MonitoringService {

    private final List<MetricsCollector> metricsCollectors;
    private final HealthCheckService healthCheckService;
    private final PerformanceMonitor performanceMonitor;
    private final AlertingEngine alertingEngine;
    private final ChannelManager channelManager;
    private final ReliabilityMetrics reliabilityMetrics;
    private final ObjectMapper objectMapper;

    public MonitoringService(
            List<MetricsCollector> metricsCollectors,
            HealthCheckService healthCheckService,
            PerformanceMonitor performanceMonitor,
            AlertingEngine alertingEngine,
            ChannelManager channelManager,
            ReliabilityMetrics reliabilityMetrics) {
        this.metricsCollectors = metricsCollectors;
        this.healthCheckService = healthCheckService;
        this.performanceMonitor = performanceMonitor;
        this.alertingEngine = alertingEngine;
        this.channelManager = channelManager;
        this.reliabilityMetrics = reliabilityMetrics;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 获取系统概览
     */
    public Map<String, Object> getSystemOverview() {
        Map<String, Object> overview = new LinkedHashMap<>();

        // 1. 健康状态
        overview.put("health", getHealthStatus());

        // 2. 连接信息
        if (channelManager != null) {
            Map<String, Object> connections = new LinkedHashMap<>();
            connections.put("current", channelManager.getConnectionCount());
            connections.put("peak", channelManager.getPeakConnections());
            connections.put("onlineUsers", channelManager.getOnlineUserCount());
            overview.put("connections", connections);
        }

        // 3. 性能指标摘要
        if (performanceMonitor != null) {
            PerformanceMetrics[] allMetrics = performanceMonitor.getAllMetrics();
            Map<String, Object> perfSummary = new LinkedHashMap<>();
            for (PerformanceMetrics metrics : allMetrics) {
                if (metrics != null) {
                    Map<String, Object> metricData = new LinkedHashMap<>();
                    metricData.put("throughput", String.format("%.2f", metrics.getThroughput()));
                    metricData.put("avgLatency", String.format("%.2fms", metrics.getAvgLatency()));
                    metricData.put("p99Latency", metrics.getP99Latency() + "ms");
                    metricData.put("errorRate", String.format("%.2f%%", metrics.getErrorRate()));
                    perfSummary.put(metrics.getType().name(), metricData);
                }
            }
            overview.put("performance", perfSummary);
        }

        // 4. 活跃告警
        if (alertingEngine != null) {
            List<Alert> activeAlerts = alertingEngine.getActiveAlerts();
            overview.put("activeAlerts", activeAlerts.size());
        }

        return overview;
    }

    /**
     * 获取健康状态
     */
    public Map<String, Object> getHealthStatus() {
        Map<String, Object> health = new LinkedHashMap<>();

        if (healthCheckService != null) {
            health.put("status", healthCheckService.getOverallStatus().name());
            health.put("isHealthy", healthCheckService.isHealthy());

            List<Map<String, Object>> checks = new ArrayList<>();
            for (HealthCheck check : healthCheckService.getAllResults()) {
                Map<String, Object> checkData = new LinkedHashMap<>();
                checkData.put("name", check.getName());
                checkData.put("status", check.getStatus().name());
                checkData.put("description", check.getDescription());
                checks.add(checkData);
            }
            health.put("checks", checks);
        } else {
            health.put("status", "UNKNOWN");
            health.put("isHealthy", false);
        }

        return health;
    }

    /**
     * 获取所有指标
     */
    public Map<String, Object> getAllMetrics() {
        Map<String, Object> allMetrics = new LinkedHashMap<>();

        for (MetricsCollector collector : metricsCollectors) {
            try {
                Map<String, Object> metrics = collector.collectMetrics();
                allMetrics.put(collector.getName(), metrics);
            } catch (Exception e) {
                log.error("采集指标失败: collector={}", collector.getName(), e);
                allMetrics.put(collector.getName(), Map.of("error", e.getMessage()));
            }
        }

        return allMetrics;
    }

    /**
     * 获取性能指标
     */
    public Map<String, Object> getPerformanceMetrics() {
        Map<String, Object> perfMetrics = new LinkedHashMap<>();

        if (performanceMonitor != null) {
            PerformanceMetrics[] allMetrics = performanceMonitor.getAllMetrics();
            for (PerformanceMetrics metrics : allMetrics) {
                if (metrics != null) {
                    perfMetrics.put(metrics.getType().name(), convertMetricsToMap(metrics));
                }
            }
        }

        return perfMetrics;
    }

    /**
     * 获取可靠性指标
     */
    public Map<String, Object> getReliabilityMetrics() {
        Map<String, Object> reliabilityMetrics = new LinkedHashMap<>();

        if (this.reliabilityMetrics != null) {
            try {
                String json = this.reliabilityMetrics.toJson();
                // 将JSON字符串转换为Map
                ObjectMapper mapper = new ObjectMapper();
                return mapper.readValue(json, Map.class);
            } catch (Exception e) {
                log.error("获取可靠性指标失败", e);
                reliabilityMetrics.put("error", e.getMessage());
            }
        }

        return reliabilityMetrics;
    }

    /**
     * 获取活跃告警
     */
    public List<Map<String, Object>> getActiveAlerts() {
        List<Map<String, Object>> alerts = new ArrayList<>();

        if (alertingEngine != null) {
            for (Alert alert : alertingEngine.getActiveAlerts()) {
                alerts.add(convertAlertToMap(alert));
            }
        }

        return alerts;
    }

    /**
     * 获取统计摘要
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();

        if (channelManager != null) {
            stats.put("channelManager", channelManager.getStats());
        }

        if (performanceMonitor != null) {
            stats.put("performanceMonitor", "PerformanceMonitor{" +
                    "metrics=" + performanceMonitor.getAllMetrics().length + "}");
        }

        if (alertingEngine != null) {
            stats.put("alertingEngine", alertingEngine.getStats());
        }

        if (healthCheckService != null) {
            stats.put("healthCheck", healthCheckService.getStatusSummary());
        }

        if (reliabilityMetrics != null) {
            stats.put("reliabilityMetrics", reliabilityMetrics.getStats());
        }

        return stats;
    }

    /**
     * 重置性能指标
     */
    public void resetPerformanceMetrics(String type) {
        if (performanceMonitor != null && type != null) {
            try {
                PerformanceMetrics.MetricType metricType =
                        PerformanceMetrics.MetricType.valueOf(type.toUpperCase());
                performanceMonitor.resetMetrics(metricType);
                log.info("重置性能指标: type={}", type);
            } catch (IllegalArgumentException e) {
                log.warn("无效的性能指标类型: {}", type);
            }
        }
    }

    /**
     * 转换性能指标为Map
     */
    private Map<String, Object> convertMetricsToMap(PerformanceMetrics metrics) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", metrics.getName());
        map.put("type", metrics.getType().name());
        map.put("timestamp", metrics.getTimestamp());
        map.put("totalCount", metrics.getTotalCount());
        map.put("successCount", metrics.getSuccessCount());
        map.put("failureCount", metrics.getFailureCount());
        map.put("throughput", metrics.getThroughput());
        map.put("avgLatency", metrics.getAvgLatency());
        map.put("p50Latency", metrics.getP50Latency());
        map.put("p95Latency", metrics.getP95Latency());
        map.put("p99Latency", metrics.getP99Latency());
        map.put("maxLatency", metrics.getMaxLatency());
        map.put("minLatency", metrics.getMinLatency());
        map.put("errorRate", metrics.getErrorRate());
        map.put("successRate", metrics.getSuccessRate());
        return map;
    }

    /**
     * 转换告警为Map
     */
    private Map<String, Object> convertAlertToMap(Alert alert) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("alertId", alert.getAlertId());
        map.put("ruleName", alert.getRuleName());
        map.put("level", alert.getLevel().name());
        map.put("title", alert.getTitle());
        map.put("description", alert.getDescription());
        map.put("details", alert.getDetails());
        map.put("metricName", alert.getMetricName());
        map.put("currentValue", alert.getCurrentValue());
        map.put("threshold", alert.getThreshold());
        map.put("timestamp", alert.getTimestamp());
        map.put("resolved", alert.isResolved());
        map.put("resolvedAt", alert.getResolvedAt());
        return map;
    }
}
