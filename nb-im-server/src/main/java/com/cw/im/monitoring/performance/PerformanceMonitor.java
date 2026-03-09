package com.cw.im.monitoring.performance;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 性能监控器
 *
 * <p>负责监控系统各项性能指标，包括吞吐量、延迟、错误率等</p>
 *
 * <h3>核心功能</h3>
 * <ul>
 *     <li>实时统计：使用原子类保证线程安全的实时统计</li>
 *     <li>延迟统计：支持P50/P95/P99延迟计算</li>
 *     <li>吞吐量统计：计算每秒请求数</li>
 *     <li>错误率统计：计算请求失败率</li>
 *     <li>多维度监控：支持按类型分别统计</li>
 * </ul>
 *
 * @author cw
 * @since 1.0.0
 */
@Slf4j
public class PerformanceMonitor {

    /**
     * 指标数据存储
     */
    private final ConcurrentHashMap<PerformanceMetrics.MetricType, MetricData> metricsMap = new ConcurrentHashMap<>();

    /**
     * 启动时间
     */
    private final LocalDateTime startTime;

    /**
     * 构造函数
     */
    public PerformanceMonitor() {
        this.startTime = LocalDateTime.now();

        // 初始化所有指标类型的数据结构
        for (PerformanceMetrics.MetricType type : PerformanceMetrics.MetricType.values()) {
            metricsMap.put(type, new MetricData(type));
        }
    }

    /**
     * 记录请求开始
     *
     * @param type 指标类型
     * @return 请求ID
     */
    public long recordRequestStart(PerformanceMetrics.MetricType type) {
        MetricData data = metricsMap.get(type);
        if (data != null) {
            return data.recordStart();
        }
        return System.currentTimeMillis();
    }

    /**
     * 记录请求成功
     *
     * @param type     指标类型
     * @param requestId 请求ID
     */
    public void recordRequestSuccess(PerformanceMetrics.MetricType type, long requestId) {
        MetricData data = metricsMap.get(type);
        if (data != null) {
            data.recordSuccess(requestId);
        }
    }

    /**
     * 记录请求失败
     *
     * @param type     指标类型
     * @param requestId 请求ID
     */
    public void recordRequestFailure(PerformanceMetrics.MetricType type, long requestId) {
        MetricData data = metricsMap.get(type);
        if (data != null) {
            data.recordFailure(requestId);
        }
    }

    /**
     * 获取指定类型的性能指标
     *
     * @param type 指标类型
     * @return 性能指标
     */
    public PerformanceMetrics getMetrics(PerformanceMetrics.MetricType type) {
        MetricData data = metricsMap.get(type);
        if (data == null) {
            return null;
        }
        return data.collectMetrics();
    }

    /**
     * 获取所有性能指标
     *
     * @return 所有性能指标
     */
    public PerformanceMetrics[] getAllMetrics() {
        return metricsMap.values().stream()
                .map(MetricData::collectMetrics)
                .toArray(PerformanceMetrics[]::new);
    }

    /**
     * 重置指定类型的指标
     *
     * @param type 指标类型
     */
    public void resetMetrics(PerformanceMetrics.MetricType type) {
        MetricData data = metricsMap.get(type);
        if (data != null) {
            data.reset();
        }
    }

    /**
     * 重置所有指标
     */
    public void resetAllMetrics() {
        metricsMap.values().forEach(MetricData::reset);
    }

    /**
     * 获取统计摘要
     *
     * @return 摘要字符串
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("PerformanceMonitor{\n");
        sb.append("  startTime=").append(startTime).append("\n");

        for (PerformanceMetrics.MetricType type : PerformanceMetrics.MetricType.values()) {
            PerformanceMetrics metrics = getMetrics(type);
            if (metrics != null) {
                sb.append("  ").append(type.name()).append("={\n");
                sb.append("    count=").append(metrics.getTotalCount()).append("\n");
                sb.append("    throughput=").append(String.format("%.2f", metrics.getThroughput())).append("/s\n");
                sb.append("    avgLatency=").append(String.format("%.2f", metrics.getAvgLatency())).append("ms\n");
                sb.append("    p99Latency=").append(metrics.getP99Latency()).append("ms\n");
                sb.append("    errorRate=").append(String.format("%.2f", metrics.getErrorRate())).append("%\n");
                sb.append("  }\n");
            }
        }

        sb.append("}");
        return sb.toString();
    }

    /**
     * 指标数据类
     */
    private static class MetricData {
        private final PerformanceMetrics.MetricType type;
        private final LongAdder totalCount = new LongAdder();
        private final LongAdder successCount = new LongAdder();
        private final LongAdder failureCount = new LongAdder();
        private final LongAdder totalLatency = new LongAdder();
        private final AtomicLong maxLatency = new AtomicLong(0);
        private final AtomicLong minLatency = new AtomicLong(Long.MAX_VALUE);
        private final LatencyHistogram latencyHistogram = new LatencyHistogram(1000);
        private final ReentrantLock lock = new ReentrantLock();

        private long startTimestamp = System.currentTimeMillis();

        public MetricData(PerformanceMetrics.MetricType type) {
            this.type = type;
        }

        public long recordStart() {
            totalCount.increment();
            return System.currentTimeMillis();
        }

        public void recordSuccess(long requestId) {
            long latency = System.currentTimeMillis() - requestId;
            successCount.increment();
            totalLatency.add(latency);
            updateLatencyStats(latency);
        }

        public void recordFailure(long requestId) {
            failureCount.increment();
        }

        private void updateLatencyStats(long latency) {
            // 更新最大延迟
            maxLatency.updateAndGet(current -> Math.max(current, latency));

            // 更新最小延迟
            minLatency.updateAndGet(current -> Math.min(current, latency));

            // 添加到延迟直方图
            latencyHistogram.add(latency);
        }

        public PerformanceMetrics collectMetrics() {
            PerformanceMetrics metrics = new PerformanceMetrics();
            metrics.setName(type.name());
            metrics.setType(type);
            metrics.setTimestamp(LocalDateTime.now());

            long total = totalCount.sum();
            long success = successCount.sum();
            long failure = failureCount.sum();

            metrics.setTotalCount(total);
            metrics.setSuccessCount(success);
            metrics.setFailureCount(failure);

            // 计算吞吐量
            long elapsedSeconds = (System.currentTimeMillis() - startTimestamp) / 1000;
            if (elapsedSeconds > 0) {
                metrics.setThroughput((double) total / elapsedSeconds);
            }

            // 计算平均延迟
            if (success > 0) {
                metrics.setAvgLatency((double) totalLatency.sum() / success);
            }

            // 计算延迟百分位
            metrics.setP50Latency(latencyHistogram.getPercentile(50));
            metrics.setP95Latency(latencyHistogram.getPercentile(95));
            metrics.setP99Latency(latencyHistogram.getPercentile(99));
            metrics.setMaxLatency(maxLatency.get());
            metrics.setMinLatency(minLatency.get() == Long.MAX_VALUE ? 0 : minLatency.get());

            // 计算错误率和成功率
            if (total > 0) {
                metrics.setErrorRate((double) failure / total * 100);
                metrics.setSuccessRate((double) success / total * 100);
            }

            return metrics;
        }

        public void reset() {
            totalCount.reset();
            successCount.reset();
            failureCount.reset();
            totalLatency.reset();
            maxLatency.set(0);
            minLatency.set(Long.MAX_VALUE);
            latencyHistogram.clear();
            startTimestamp = System.currentTimeMillis();
        }
    }

    /**
     * 延迟直方图（用于计算百分位）
     */
    private static class LatencyHistogram {
        private final int windowSize;
        private final long[] window;
        private int index = 0;
        private boolean filled = false;

        public LatencyHistogram(int windowSize) {
            this.windowSize = windowSize;
            this.window = new long[windowSize];
        }

        public synchronized void add(long value) {
            window[index] = value;
            index = (index + 1) % windowSize;
            if (index == 0) {
                filled = true;
            }
        }

        public synchronized long getPercentile(double percentile) {
            int size = filled ? windowSize : index;
            if (size == 0) {
                return 0;
            }

            // 复制数组进行排序
            long[] sorted = new long[size];
            System.arraycopy(window, 0, sorted, 0, size);
            java.util.Arrays.sort(sorted);

            int rank = (int) Math.ceil(size * percentile / 100) - 1;
            return sorted[Math.max(0, rank)];
        }

        public synchronized void clear() {
            index = 0;
            filled = false;
            java.util.Arrays.fill(window, 0);
        }
    }
}
