package com.cw.im.monitoring.health;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 健康检查结果
 *
 * <p>封装组件健康检查的结果信息</p>
 *
 * @author cw
 * @since 1.0.0
 */
@Data
@Builder
public class HealthCheck {

    /**
     * 组件名称
     */
    private String name;

    /**
     * 健康状态
     */
    private HealthStatus status;

    /**
     * 状态描述
     */
    private String description;

    /**
     * 检查时间
     */
    private LocalDateTime timestamp;

    /**
     * 额外信息
     */
    private String details;

    /**
     * 异常信息（如果检查失败）
     */
    private Throwable exception;

    /**
     * 响应时间（毫秒）
     */
    private long responseTime;

    /**
     * 健康状态枚举
     */
    public enum HealthStatus {
        /**
         * 健康
         */
        UP,

        /**
         * 不健康
         */
        DOWN,

        /**
         * 降级服务
         */
        DEGRADED,

        /**
         * 未知
         */
        UNKNOWN
    }

    /**
     * 创建健康状态
     */
    public static HealthCheck up(String name) {
        return HealthCheck.builder()
                .name(name)
                .status(HealthStatus.UP)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 创建不健康状态
     */
    public static HealthCheck down(String name, String description) {
        return HealthCheck.builder()
                .name(name)
                .status(HealthStatus.DOWN)
                .description(description)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 创建降级状态
     */
    public static HealthCheck degraded(String name, String description) {
        return HealthCheck.builder()
                .name(name)
                .status(HealthStatus.DEGRADED)
                .description(description)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 创建未知状态
     */
    public static HealthCheck unknown(String name) {
        return HealthCheck.builder()
                .name(name)
                .status(HealthStatus.UNKNOWN)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 判断是否健康
     */
    public boolean isHealthy() {
        return status == HealthStatus.UP;
    }

    /**
     * 判断是否可用（包括降级状态）
     */
    public boolean isAvailable() {
        return status == HealthStatus.UP || status == HealthStatus.DEGRADED;
    }
}
