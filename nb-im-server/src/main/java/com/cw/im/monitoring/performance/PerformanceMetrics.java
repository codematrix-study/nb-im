package com.cw.im.monitoring.performance;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 性能指标数据
 *
 * <p>封装系统性能相关的指标数据</p>
 *
 * @author cw
 * @since 1.0.0
 */
@Data
public class PerformanceMetrics {

    /**
     * 指标名称
     */
    private String name;

    /**
     * 指标类型
     */
    private MetricType type;

    /**
     * 时间戳
     */
    private LocalDateTime timestamp;

    /**
     * 总请求数
     */
    private long totalCount;

    /**
     * 成功请求数
     */
    private long successCount;

    /**
     * 失败请求数
     */
    private long failureCount;

    /**
     * 吞吐量（请求/秒）
     */
    private double throughput;

    /**
     * 平均延迟（毫秒）
     */
    private double avgLatency;

    /**
     * P50延迟（毫秒）
     */
    private long p50Latency;

    /**
     * P95延迟（毫秒）
     */
    private long p95Latency;

    /**
     * P99延迟（毫秒）
     */
    private long p99Latency;

    /**
     * 最大延迟（毫秒）
     */
    private long maxLatency;

    /**
     * 最小延迟（毫秒）
     */
    private long minLatency;

    /**
     * 错误率（百分比）
     */
    private double errorRate;

    /**
     * 成功率（百分比）
     */
    private double successRate;

    /**
     * 指标类型枚举
     */
    public enum MetricType {
        /**
         * 消息发送
         */
        MESSAGE_SEND,

        /**
         * 消息接收
         */
        MESSAGE_RECEIVE,

        /**
         * 消息投递
         */
        MESSAGE_DELIVERY,

        /**
         * Redis操作
         */
        REDIS_OPERATION,

        /**
         * Kafka操作
         */
        KAFKA_OPERATION,

        /**
         * 系统综合
         */
        SYSTEM_OVERALL
    }
}
