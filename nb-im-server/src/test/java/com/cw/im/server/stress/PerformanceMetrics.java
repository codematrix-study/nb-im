package com.cw.im.server.stress;

import lombok.Data;

import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 性能指标收集器
 *
 * <p>收集和计算性能测试指标，包括：
 * <ul>
 *     <li>吞吐量（TPS）</li>
 *     <li>延迟（P50/P95/P99）</li>
 *     <li>错误率</li>
 *     <li>资源使用情况</li>
 * </ul>
 *
 * @author cw
 * @since 1.0.0
 */
public class PerformanceMetrics {

    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong successfulRequests = new AtomicLong(0);
    private final AtomicLong failedRequests = new AtomicLong(0);

    private final ConcurrentSkipListMap<Long, Long> latencyRecords = new ConcurrentSkipListMap<>();

    private volatile long testStartTime;
    private volatile long testEndTime;

    /**
     * 开始测试
     */
    public void startTest() {
        testStartTime = System.currentTimeMillis();
    }

    /**
     * 结束测试
     */
    public void endTest() {
        testEndTime = System.currentTimeMillis();
    }

    /**
     * 记录请求
     */
    public void recordRequest() {
        totalRequests.incrementAndGet();
    }

    /**
     * 记录成功请求
     *
     * @param latency 延迟（毫秒）
     */
    public void recordSuccess(long latency) {
        successfulRequests.incrementAndGet();
        latencyRecords.put(latency, latency);
    }

    /**
     * 记录失败请求
     */
    public void recordFailure() {
        failedRequests.incrementAndGet();
    }

    /**
     * 获取总请求数
     */
    public long getTotalRequests() {
        return totalRequests.get();
    }

    /**
     * 获取成功请求数
     */
    public long getSuccessfulRequests() {
        return successfulRequests.get();
    }

    /**
     * 获取失败请求数
     */
    public long getFailedRequests() {
        return failedRequests.get();
    }

    /**
     * 获取成功率
     */
    public double getSuccessRate() {
        long total = totalRequests.get();
        if (total == 0) {
            return 0.0;
        }
        return (successfulRequests.get() * 100.0) / total;
    }

    /**
     * 获取错误率
     */
    public double getErrorRate() {
        long total = totalRequests.get();
        if (total == 0) {
            return 0.0;
        }
        return (failedRequests.get() * 100.0) / total;
    }

    /**
     * 获取平均延迟
     */
    public double getAverageLatency() {
        if (latencyRecords.isEmpty()) {
            return 0.0;
        }

        long sum = 0;
        for (Long latency : latencyRecords.keySet()) {
            sum += latency;
        }

        return (double) sum / latencyRecords.size();
    }

    /**
     * 获取最小延迟
     */
    public long getMinLatency() {
        if (latencyRecords.isEmpty()) {
            return 0;
        }
        return latencyRecords.firstKey();
    }

    /**
     * 获取最大延迟
     */
    public long getMaxLatency() {
        if (latencyRecords.isEmpty()) {
            return 0;
        }
        return latencyRecords.lastKey();
    }

    /**
     * 获取P50延迟（中位数）
     */
    public long getP50Latency() {
        return getPercentile(50);
    }

    /**
     * 获取P95延迟
     */
    public long getP95Latency() {
        return getPercentile(95);
    }

    /**
     * 获取P99延迟
     */
    public long getP99Latency() {
        return getPercentile(99);
    }

    /**
     * 获取P999延迟
     */
    public long getP999Latency() {
        return getPercentile(99.9);
    }

    /**
     * 获取指定百分位的延迟
     *
     * @param percentile 百分位（0-100）
     */
    public long getPercentile(double percentile) {
        if (latencyRecords.isEmpty()) {
            return 0;
        }

        int size = latencyRecords.size();
        int index = (int) Math.ceil(size * percentile / 100) - 1;
        index = Math.max(0, Math.min(index, size - 1));

        int i = 0;
        for (Long latency : latencyRecords.keySet()) {
            if (i == index) {
                return latency;
            }
            i++;
        }

        return 0;
    }

    /**
     * 获取TPS（每秒事务数）
     */
    public double getTPS() {
        long duration = getTestDuration();
        if (duration == 0) {
            return 0.0;
        }
        return (successfulRequests.get() * 1000.0) / duration;
    }

    /**
     * 获取测试时长（毫秒）
     */
    public long getTestDuration() {
        if (testStartTime == 0) {
            return 0;
        }
        long end = testEndTime == 0 ? System.currentTimeMillis() : testEndTime;
        return end - testStartTime;
    }

    /**
     * 重置统计信息
     */
    public void reset() {
        totalRequests.set(0);
        successfulRequests.set(0);
        failedRequests.set(0);
        latencyRecords.clear();
        testStartTime = 0;
        testEndTime = 0;
    }

    /**
     * 生成性能报告
     */
    public PerformanceReport generateReport() {
        PerformanceReport report = new PerformanceReport();

        report.setTotalRequests(getTotalRequests());
        report.setSuccessfulRequests(getSuccessfulRequests());
        report.setFailedRequests(getFailedRequests());
        report.setSuccessRate(getSuccessRate());
        report.setErrorRate(getErrorRate());

        report.setAverageLatency(getAverageLatency());
        report.setMinLatency(getMinLatency());
        report.setMaxLatency(getMaxLatency());
        report.setP50Latency(getP50Latency());
        report.setP95Latency(getP95Latency());
        report.setP99Latency(getP99Latency());
        report.setP999Latency(getP999Latency());

        report.setTps(getTPS());
        report.setTestDuration(getTestDuration());

        return report;
    }

    /**
     * 打印性能报告
     */
    public void printReport() {
        PerformanceReport report = generateReport();
        report.print();
    }

    /**
     * 性能报告
     */
    @Data
    public static class PerformanceReport {

        private long totalRequests;
        private long successfulRequests;
        private long failedRequests;
        private double successRate;
        private double errorRate;

        private double averageLatency;
        private long minLatency;
        private long maxLatency;
        private long p50Latency;
        private long p95Latency;
        private long p99Latency;
        private long p999Latency;

        private double tps;
        private long testDuration;

        /**
         * 打印报告
         */
        public void print() {
            System.out.println("\n========================================");
            System.out.println("性能测试报告");
            System.out.println("========================================");
            System.out.println("请求统计:");
            System.out.println("  总请求数: " + totalRequests);
            System.out.println("  成功请求: " + successfulRequests);
            System.out.println("  失败请求: " + failedRequests);
            System.out.println("  成功率: " + String.format("%.2f%%", successRate));
            System.out.println("  错误率: " + String.format("%.4f%%", errorRate));
            System.out.println("");
            System.out.println("延迟统计:");
            System.out.println("  平均延迟: " + String.format("%.2fms", averageLatency));
            System.out.println("  最小延迟: " + minLatency + "ms");
            System.out.println("  最大延迟: " + maxLatency + "ms");
            System.out.println("  P50延迟: " + p50Latency + "ms");
            System.out.println("  P95延迟: " + p95Latency + "ms");
            System.out.println("  P99延迟: " + p99Latency + "ms");
            System.out.println("  P999延迟: " + p999Latency + "ms");
            System.out.println("");
            System.out.println("性能指标:");
            System.out.println("  TPS: " + String.format("%.2f", tps));
            System.out.println("  测试时长: " + (testDuration / 1000.0) + "秒");
            System.out.println("========================================\n");
        }

        /**
         * 生成JSON格式报告
         */
        public String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            sb.append("  \"totalRequests\": ").append(totalRequests).append(",\n");
            sb.append("  \"successfulRequests\": ").append(successfulRequests).append(",\n");
            sb.append("  \"failedRequests\": ").append(failedRequests).append(",\n");
            sb.append("  \"successRate\": ").append(String.format("%.2f", successRate)).append(",\n");
            sb.append("  \"errorRate\": ").append(String.format("%.4f", errorRate)).append(",\n");
            sb.append("  \"averageLatency\": ").append(String.format("%.2f", averageLatency)).append(",\n");
            sb.append("  \"minLatency\": ").append(minLatency).append(",\n");
            sb.append("  \"maxLatency\": ").append(maxLatency).append(",\n");
            sb.append("  \"p50Latency\": ").append(p50Latency).append(",\n");
            sb.append("  \"p95Latency\": ").append(p95Latency).append(",\n");
            sb.append("  \"p99Latency\": ").append(p99Latency).append(",\n");
            sb.append("  \"p999Latency\": ").append(p999Latency).append(",\n");
            sb.append("  \"tps\": ").append(String.format("%.2f", tps)).append(",\n");
            sb.append("  \"testDuration\": ").append(testDuration).append("\n");
            sb.append("}");
            return sb.toString();
        }
    }
}
