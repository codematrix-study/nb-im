package com.cw.im.core;

import com.cw.im.common.constants.RedisKeys;
import com.cw.im.redis.RedisManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 消息可靠性指标监控
 *
 * <p>负责收集、统计和上报消息可靠性的各项指标</p>
 *
 * <h3>核心指标</h3>
 * <ul>
 *     <li>发送成功率：成功发送的消息数 / 总发送消息数</li>
 *     <li>投递成功率：成功投递的消息数 / 总投递消息数</li>
 *     <li>重试成功率：重试成功的消息数 / 总重试消息数</li>
 *     <li>平均投递延迟：从发送到投递的平均时间</li>
 *     <li>P99投递延迟：99%的消息投递延迟</li>
 * </ul>
 *
 * <h3>功能特性</h3>
 * <ul>
 *     <li>实时统计：使用原子类保证线程安全的实时统计</li>
 *     <li>滑动窗口：使用滑动窗口计算成功率</li>
 *     <li>持久化：定期将指标持久化到Redis</li>
 *     <li>监控告警：支持指标超阈值告警</li>
 * </ul>
 *
 * @author cw
 * @since 1.0.0
 */
@Slf4j
public class ReliabilityMetrics {

    private final RedisManager redisManager;
    private final ObjectMapper objectMapper;

    // ==================== 计数器 ====================

    /**
     * 总发送消息数
     */
    private final AtomicLong totalSent = new AtomicLong(0);

    /**
     * 发送成功数
     */
    private final AtomicLong sendSuccess = new AtomicLong(0);

    /**
     * 发送失败数
     */
    private final AtomicLong sendFailed = new AtomicLong(0);

    /**
     * 总投递消息数
     */
    private final AtomicLong totalDelivered = new AtomicLong(0);

    /**
     * 投递成功数
     */
    private final AtomicLong deliverySuccess = new AtomicLong(0);

    /**
     * 投递失败数
     */
    private final AtomicLong deliveryFailed = new AtomicLong(0);

    /**
     * 总重试次数
     */
    private final AtomicLong totalRetry = new AtomicLong(0);

    /**
     * 重试成功次数
     */
    private final AtomicLong retrySuccess = new AtomicLong(0);

    /**
     * 重试失败次数
     */
    private final AtomicLong retryFailed = new AtomicLong(0);

    /**
     * 死信队列消息数
     */
    private final AtomicLong deadLetterCount = new AtomicLong(0);

    // ==================== 延迟统计 ====================

    /**
     * 投递延迟总和（用于计算平均延迟）
     */
    private final LongAdder totalDeliveryLatency = new LongAdder();

    /**
     * 最大投递延迟
     */
    private final AtomicLong maxDeliveryLatency = new AtomicLong(0);

    /**
     * 最小投递延迟
     */
    private final AtomicLong minDeliveryLatency = new AtomicLong(Long.MAX_VALUE);

    /**
     * 投递延迟采样窗口（用于计算P99延迟）
     */
    private final SlidingWindow latencyWindow;

    /**
     * 指标数据类
     */
    @Data
    public static class MetricsData {
        private Long timestamp;
        private Long totalSent;
        private Long sendSuccess;
        private Long sendFailed;
        private Double sendSuccessRate;
        private Long totalDelivered;
        private Long deliverySuccess;
        private Long deliveryFailed;
        private Double deliverySuccessRate;
        private Long totalRetry;
        private Long retrySuccess;
        private Long retryFailed;
        private Double retrySuccessRate;
        private Long deadLetterCount;
        private Double avgDeliveryLatency;
        private Long maxDeliveryLatency;
        private Long minDeliveryLatency;
        private Long p99DeliveryLatency;
        private Long p95DeliveryLatency;
    }

    /**
     * 滑动窗口类
     */
    private static class SlidingWindow {
        private final int windowSize;
        private final long[] window;
        private int index = 0;
        private boolean filled = false;

        public SlidingWindow(int windowSize) {
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

    /**
     * 构造函数
     *
     * @param redisManager Redis管理器
     */
    public ReliabilityMetrics(RedisManager redisManager) {
        this(redisManager, 1000); // 默认1000的滑动窗口
    }

    /**
     * 构造函数
     *
     * @param redisManager Redis管理器
     * @param windowSize   滑动窗口大小
     */
    public ReliabilityMetrics(RedisManager redisManager, int windowSize) {
        this.redisManager = redisManager;
        this.objectMapper = new ObjectMapper();
        this.latencyWindow = new SlidingWindow(windowSize);
        log.info("可靠性指标监控初始化完成: windowSize={}", windowSize);
    }

    // ==================== 记录方法 ====================

    /**
     * 记录发送消息
     */
    public void recordSent() {
        totalSent.incrementAndGet();
    }

    /**
     * 记录发送成功
     */
    public void recordSendSuccess() {
        sendSuccess.incrementAndGet();
    }

    /**
     * 记录发送失败
     */
    public void recordSendFailed() {
        sendFailed.incrementAndGet();
    }

    /**
     * 记录投递消息
     */
    public void recordDelivered() {
        totalDelivered.incrementAndGet();
    }

    /**
     * 记录投递成功
     *
     * @param latency 投递延迟（毫秒）
     */
    public void recordDeliverySuccess(long latency) {
        deliverySuccess.incrementAndGet();
        recordDeliveryLatency(latency);
    }

    /**
     * 记录投递失败
     */
    public void recordDeliveryFailed() {
        deliveryFailed.incrementAndGet();
    }

    /**
     * 记录重试
     */
    public void recordRetry() {
        totalRetry.incrementAndGet();
    }

    /**
     * 记录重试成功
     */
    public void recordRetrySuccess() {
        retrySuccess.incrementAndGet();
    }

    /**
     * 记录重试失败
     */
    public void recordRetryFailed() {
        retryFailed.incrementAndGet();
    }

    /**
     * 记录死信消息
     */
    public void recordDeadLetter() {
        deadLetterCount.incrementAndGet();
    }

    /**
     * 记录投递延迟
     *
     * @param latency 延迟时间（毫秒）
     */
    private void recordDeliveryLatency(long latency) {
        totalDeliveryLatency.add(latency);

        // 更新最大延迟
        maxDeliveryLatency.updateAndGet(current -> Math.max(current, latency));

        // 更新最小延迟
        minDeliveryLatency.updateAndGet(current -> Math.min(current, latency));

        // 添加到滑动窗口
        latencyWindow.add(latency);
    }

    // ==================== 计算方法 ====================

    /**
     * 计算发送成功率
     *
     * @return 成功率（百分比）
     */
    public double calculateSendSuccessRate() {
        long total = totalSent.get();
        if (total == 0) {
            return 0.0;
        }
        return (double) sendSuccess.get() / total * 100;
    }

    /**
     * 计算投递成功率
     *
     * @return 成功率（百分比）
     */
    public double calculateDeliverySuccessRate() {
        long total = totalDelivered.get();
        if (total == 0) {
            return 0.0;
        }
        return (double) deliverySuccess.get() / total * 100;
    }

    /**
     * 计算重试成功率
     *
     * @return 成功率（百分比）
     */
    public double calculateRetrySuccessRate() {
        long total = totalRetry.get();
        if (total == 0) {
            return 0.0;
        }
        return (double) retrySuccess.get() / total * 100;
    }

    /**
     * 计算平均投递延迟
     *
     * @return 平均延迟（毫秒）
     */
    public double calculateAvgDeliveryLatency() {
        long count = deliverySuccess.get();
        if (count == 0) {
            return 0.0;
        }
        return (double) totalDeliveryLatency.sum() / count;
    }

    /**
     * 获取P99投递延迟
     *
     * @return P99延迟（毫秒）
     */
    public long getP99DeliveryLatency() {
        return latencyWindow.getPercentile(99);
    }

    /**
     * 获取P95投递延迟
     *
     * @return P95延迟（毫秒）
     */
    public long getP95DeliveryLatency() {
        return latencyWindow.getPercentile(95);
    }

    /**
     * 获取最大投递延迟
     *
     * @return 最大延迟（毫秒）
     */
    public long getMaxDeliveryLatency() {
        long max = maxDeliveryLatency.get();
        return max == Long.MIN_VALUE ? 0 : max;
    }

    /**
     * 获取最小投递延迟
     *
     * @return 最小延迟（毫秒）
     */
    public long getMinDeliveryLatency() {
        long min = minDeliveryLatency.get();
        return min == Long.MAX_VALUE ? 0 : min;
    }

    // ==================== 持久化方法 ====================

    /**
     * 保存指标到Redis
     */
    public void saveToRedis() {
        try {
            MetricsData data = collectMetrics();
            String json = objectMapper.writeValueAsString(data);
            redisManager.set(RedisKeys.MESSAGE_RELIABILITY_METRICS, json);
            log.debug("可靠性指标已保存到Redis");
        } catch (Exception e) {
            log.error("保存可靠性指标到Redis异常", e);
        }
    }

    /**
     * 从Redis加载指标
     *
     * @return 指标数据，如果不存在则返回null
     */
    public MetricsData loadFromRedis() {
        try {
            String json = redisManager.get(RedisKeys.MESSAGE_RELIABILITY_METRICS);
            if (json == null || json.isEmpty()) {
                return null;
            }
            return objectMapper.readValue(json, MetricsData.class);
        } catch (Exception e) {
            log.error("从Redis加载可靠性指标异常", e);
            return null;
        }
    }

    /**
     * 收集当前指标
     *
     * @return 指标数据
     */
    public MetricsData collectMetrics() {
        MetricsData data = new MetricsData();
        data.setTimestamp(System.currentTimeMillis());
        data.setTotalSent(totalSent.get());
        data.setSendSuccess(sendSuccess.get());
        data.setSendFailed(sendFailed.get());
        data.setSendSuccessRate(calculateSendSuccessRate());
        data.setTotalDelivered(totalDelivered.get());
        data.setDeliverySuccess(deliverySuccess.get());
        data.setDeliveryFailed(deliveryFailed.get());
        data.setDeliverySuccessRate(calculateDeliverySuccessRate());
        data.setTotalRetry(totalRetry.get());
        data.setRetrySuccess(retrySuccess.get());
        data.setRetryFailed(retryFailed.get());
        data.setRetrySuccessRate(calculateRetrySuccessRate());
        data.setDeadLetterCount(deadLetterCount.get());
        data.setAvgDeliveryLatency(calculateAvgDeliveryLatency());
        data.setMaxDeliveryLatency(getMaxDeliveryLatency());
        data.setMinDeliveryLatency(getMinDeliveryLatency());
        data.setP99DeliveryLatency(getP99DeliveryLatency());
        data.setP95DeliveryLatency(getP95DeliveryLatency());
        return data;
    }

    // ==================== 统计方法 ====================

    /**
     * 获取统计信息
     *
     * @return 统计信息字符串
     */
    public String getStats() {
        MetricsData data = collectMetrics();
        return String.format(
                "ReliabilityMetrics{" +
                        "sent=%d(success=%.2f%%), " +
                        "delivered=%d(success=%.2f%%), " +
                        "retry=%d(success=%.2f%%), " +
                        "deadLetter=%d, " +
                        "latency(avg=%.2fms, p99=%dms, p95=%dms, max=%dms, min=%dms)}",
                data.getTotalSent(), data.getSendSuccessRate(),
                data.getTotalDelivered(), data.getDeliverySuccessRate(),
                data.getTotalRetry(), data.getRetrySuccessRate(),
                data.getDeadLetterCount(),
                data.getAvgDeliveryLatency(), data.getP99DeliveryLatency(),
                data.getP95DeliveryLatency(), data.getMaxDeliveryLatency(), data.getMinDeliveryLatency()
        );
    }

    /**
     * 重置统计信息
     */
    public void resetStats() {
        totalSent.set(0);
        sendSuccess.set(0);
        sendFailed.set(0);
        totalDelivered.set(0);
        deliverySuccess.set(0);
        deliveryFailed.set(0);
        totalRetry.set(0);
        retrySuccess.set(0);
        retryFailed.set(0);
        deadLetterCount.set(0);
        totalDeliveryLatency.reset();
        maxDeliveryLatency.set(0);
        minDeliveryLatency.set(Long.MAX_VALUE);
        latencyWindow.clear();
        log.info("可靠性指标统计信息已重置");
    }

    /**
     * 格式化指标为JSON
     *
     * @return JSON字符串
     */
    public String toJson() {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(collectMetrics());
        } catch (Exception e) {
            log.error("格式化指标为JSON异常", e);
            return "{}";
        }
    }
}
