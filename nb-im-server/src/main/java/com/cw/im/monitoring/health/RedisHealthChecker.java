package com.cw.im.monitoring.health;

import com.cw.im.redis.RedisManager;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;

/**
 * Redis健康检查器
 *
 * <p>检查Redis连接状态和响应时间</p>
 *
 * @author cw
 * @since 1.0.0
 */
@Slf4j
public class RedisHealthChecker implements HealthChecker {

    private final RedisManager redisManager;
    private final long timeout;

    /**
     * 构造函数
     *
     * @param redisManager Redis管理器
     */
    public RedisHealthChecker(RedisManager redisManager) {
        this(redisManager, 5000); // 默认5秒超时
    }

    /**
     * 构造函数
     *
     * @param redisManager Redis管理器
     * @param timeout      超时时间（毫秒）
     */
    public RedisHealthChecker(RedisManager redisManager, long timeout) {
        this.redisManager = redisManager;
        this.timeout = timeout;
    }

    @Override
    public HealthCheck check() {
        if (redisManager == null) {
            return HealthCheck.down(getName(), "RedisManager未初始化");
        }

        try {
            Instant start = Instant.now();

            // 执行PING命令
            String response = redisManager.ping();

            long responseTime = Duration.between(start, Instant.now()).toMillis();

            if ("PONG".equals(response)) {
                if (responseTime > 1000) {
                    // 响应时间超过1秒，返回降级状态
                    return HealthCheck.builder()
                            .name(getName())
                            .status(HealthCheck.HealthStatus.DEGRADED)
                            .description("Redis响应慢")
                            .details("响应时间: " + responseTime + "ms")
                            .responseTime(responseTime)
                            .build();
                }

                return HealthCheck.builder()
                        .name(getName())
                        .status(HealthCheck.HealthStatus.UP)
                        .description("Redis连接正常")
                        .responseTime(responseTime)
                        .build();
            } else {
                return HealthCheck.down(getName(), "PING响应异常: " + response);
            }

        } catch (Exception e) {
            log.error("Redis健康检查失败", e);
            return HealthCheck.builder()
                    .name(getName())
                    .status(HealthCheck.HealthStatus.DOWN)
                    .description("Redis连接失败")
                    .exception(e)
                    .build();
        }
    }

    @Override
    public String getName() {
        return "redis";
    }
}
