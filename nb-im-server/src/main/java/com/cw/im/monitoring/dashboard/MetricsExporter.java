package com.cw.im.monitoring.dashboard;

import com.cw.im.monitoring.MetricsCollector;
import com.cw.im.monitoring.alerting.Alert;
import com.cw.im.monitoring.alerting.AlertingEngine;
import com.cw.im.monitoring.health.HealthCheckService;
import com.cw.im.monitoring.performance.PerformanceMonitor;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 指标导出器
 *
 * <p>支持将监控指标导出为不同格式（JSON、Prometheus等）</p>
 *
 * @author cw
 * @since 1.0.0
 */
@Slf4j
public class MetricsExporter {

    private final List<MetricsCollector> metricsCollectors;
    private final HealthCheckService healthCheckService;
    private final PerformanceMonitor performanceMonitor;
    private final AlertingEngine alertingEngine;
    private final ObjectMapper objectMapper;

    public MetricsExporter(
            List<MetricsCollector> metricsCollectors,
            HealthCheckService healthCheckService,
            PerformanceMonitor performanceMonitor,
            AlertingEngine alertingEngine) {
        this.metricsCollectors = metricsCollectors;
        this.healthCheckService = healthCheckService;
        this.performanceMonitor = performanceMonitor;
        this.alertingEngine = alertingEngine;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 导出所有指标为JSON
     */
    public String exportToJson() {
        try {
            Map<String, Object> allData = Map.of(
                    "timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    "metrics", collectAllMetrics(),
                    "health", collectHealthStatus(),
                    "performance", collectPerformanceMetrics(),
                    "alerts", collectAlerts()
            );

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(allData);

        } catch (Exception e) {
            log.error("导出JSON格式指标失败", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    /**
     * 导出为Prometheus格式
     */
    public String exportToPrometheus() {
        StringBuilder prometheus = new StringBuilder();

        // 添加帮助信息
        prometheus.append("# NB-IM Metrics Export\n");
        prometheus.append("# Generated at: ").append(LocalDateTime.now()).append("\n\n");

        // 收集所有指标
        for (MetricsCollector collector : metricsCollectors) {
            try {
                Map<String, Object> metrics = collector.collectMetrics();
                for (Map.Entry<String, Object> entry : metrics.entrySet()) {
                    prometheus.append(formatPrometheusMetric(
                            collector.getName(),
                            entry.getKey(),
                            entry.getValue()
                    ));
                }
            } catch (Exception e) {
                log.error("导出Prometheus指标失败: collector={}", collector.getName(), e);
            }
        }

        return prometheus.toString();
    }

    /**
     * 保存指标到文件
     *
     * @param format 导出格式（json/prometheus）
     * @param filePath 文件路径
     */
    public void saveToFile(String format, String filePath) {
        try {
            String content;
            if ("prometheus".equalsIgnoreCase(format)) {
                content = exportToPrometheus();
            } else {
                content = exportToJson();
            }

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
                writer.write(content);
            }

            log.info("指标已保存到文件: format={}, file={}", format, filePath);

        } catch (IOException e) {
            log.error("保存指标到文件失败: file={}", filePath, e);
        }
    }

    /**
     * 收集所有指标
     */
    private Map<String, Object> collectAllMetrics() {
        Map<String, Object> allMetrics = new java.util.LinkedHashMap<>();

        for (MetricsCollector collector : metricsCollectors) {
            try {
                Map<String, Object> metrics = collector.collectMetrics();
                allMetrics.put(collector.getName(), metrics);
            } catch (Exception e) {
                log.error("采集指标失败: collector={}", collector.getName(), e);
            }
        }

        return allMetrics;
    }

    /**
     * 收集健康状态
     */
    private Map<String, Object> collectHealthStatus() {
        if (healthCheckService == null) {
            return Map.of("status", "UNKNOWN");
        }

        return Map.of(
                "status", healthCheckService.getOverallStatus().name(),
                "isHealthy", healthCheckService.isHealthy(),
                "checks", healthCheckService.getAllResults()
        );
    }

    /**
     * 收集性能指标
     */
    private Map<String, Object> collectPerformanceMetrics() {
        if (performanceMonitor == null) {
            return Map.of();
        }

        Map<String, Object> perfData = new java.util.LinkedHashMap<>();
        for (com.cw.im.monitoring.performance.PerformanceMetrics metrics :
                performanceMonitor.getAllMetrics()) {
            if (metrics != null) {
                perfData.put(metrics.getType().name(), Map.of(
                        "throughput", metrics.getThroughput(),
                        "avgLatency", metrics.getAvgLatency(),
                        "p99Latency", metrics.getP99Latency(),
                        "errorRate", metrics.getErrorRate()
                ));
            }
        }

        return perfData;
    }

    /**
     * 收集告警
     */
    private List<Alert> collectAlerts() {
        if (alertingEngine == null) {
            return List.of();
        }

        return alertingEngine.getActiveAlerts();
    }

    /**
     * 格式化为Prometheus指标格式
     */
    private String formatPrometheusMetric(String collector, String metricName, Object value) {
        // 转换为Prometheus命名规范
        String prometheusName = "nbim_" + collector + "_" +
                metricName.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();

        // 格式化值
        String strValue;
        if (value instanceof Number) {
            strValue = String.valueOf(((Number) value).doubleValue());
        } else {
            strValue = "0";
        }

        return prometheusName + " " + strValue + "\n";
    }
}
