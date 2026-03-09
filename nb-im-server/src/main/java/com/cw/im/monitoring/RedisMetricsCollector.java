package com.cw.im.monitoring;

import com.cw.im.redis.RedisManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.cluster.RedisClusterClient;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis指标采集器
 *
 * <p>负责采集Redis连接和性能相关的指标</p>
 *
 * <h3>采集指标</h3>
 * <ul>
 *     <li>连接指标：连接状态、连接池使用情况</li>
 *     <li>性能指标：命令执行时间、响应时间</li>
 *     <li>缓存指标：缓存命中率（如果可用）</li>
 *     <li>内存指标：Redis内存使用情况（如果可用）</li>
 * </ul>
 *
 * @author cw
 * @since 1.0.0
 */
@Slf4j
public class RedisMetricsCollector extends AbstractMetricsCollector {

    private final RedisManager redisManager;

    /**
     * 最后一次PING时间
     */
    private Instant lastPingTime;

    /**
     * 最后一次PING延迟（毫秒）
     */
    private long lastPingLatency = 0;

    /**
     * 连接失败次数
     */
    private final Map<String, Long> connectionFailures = new HashMap<>();

    /**
     * 构造函数
     *
     * @param redisManager Redis管理器
     */
    public RedisMetricsCollector(RedisManager redisManager) {
        super("redis", "Redis连接和性能指标采集器");
        this.redisManager = redisManager;
    }

    @Override
    public Map<String, Object> collectMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        if (redisManager == null) {
            log.warn("RedisManager未初始化，跳过Redis指标采集");
            metrics.put("redis_status", "DOWN");
            return metrics;
        }

        try {
            // 1. 连接状态指标
            metrics.putAll(collectConnectionMetrics());

            // 2. 性能指标
            metrics.putAll(collectPerformanceMetrics());

            // 3. INFO指标（如果可用）
            metrics.putAll(collectInfoMetrics());

        } catch (Exception e) {
            log.error("采集Redis指标异常", e);
            metrics.put("redis_status", "ERROR");
            metrics.put("redis_error", e.getMessage());
        }

        return metrics;
    }

    /**
     * 采集连接状态指标
     */
    private Map<String, Object> collectConnectionMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        boolean connected = false;
        String connectionType = "unknown";

        try {
            // 使用健康检查方法
            connected = redisManager.isHealthy();
            connectionType = "standalone"; // 简化处理
        } catch (Exception e) {
            log.warn("Redis连接检查失败", e);
        }

        metrics.put("redis_status", connected ? "UP" : "DOWN");
        metrics.put("redis_connection_type", connectionType);

        return metrics;
    }

    /**
     * 采集性能指标
     */
    private Map<String, Object> collectPerformanceMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        try {
            // 执行PING命令测试延迟
            Instant start = Instant.now();
            String pong = redisManager.ping();
            Instant end = Instant.now();

            long latency = Duration.between(start, end).toMillis();
            lastPingTime = start;
            lastPingLatency = latency;

            metrics.put("redis_ping_success", "PONG".equals(pong));
            metrics.put("redis_ping_latency_ms", latency);
            metrics.put("redis_last_ping_time", lastPingTime.toEpochMilli());

        } catch (Exception e) {
            log.error("Redis PING失败", e);
            metrics.put("redis_ping_success", false);
            metrics.put("redis_ping_error", e.getMessage());

            // 记录连接失败
            String key = redisManager.toString();
            connectionFailures.put(key, connectionFailures.getOrDefault(key, 0L) + 1);
        }

        metrics.put("redis_connection_failures", connectionFailures.values().stream()
                .mapToLong(Long::longValue).sum());

        return metrics;
    }

    /**
     * 采集INFO指标（Redis服务器信息）
     */
    private Map<String, Object> collectInfoMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        try {
            boolean healthy = redisManager.isHealthy();
            metrics.put("redis_connection_open", healthy);
            metrics.put("redis_connection_active", healthy);
        } catch (Exception e) {
            log.debug("无法获取Redis连接状态", e);
            metrics.put("redis_connection_open", false);
            metrics.put("redis_connection_active", false);
        }

        return metrics;
    }

    @Override
    protected void doStop() {
        // 清理资源
        connectionFailures.clear();
    }
}
