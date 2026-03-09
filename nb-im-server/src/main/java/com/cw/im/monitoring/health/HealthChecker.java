package com.cw.im.monitoring.health;

/**
 * 健康检查器接口
 *
 * <p>定义组件健康检查的统一接口</p>
 *
 * @author cw
 * @since 1.0.0
 */
public interface HealthChecker {

    /**
     * 执行健康检查
     *
     * @return 健康检查结果
     */
    HealthCheck check();

    /**
     * 获取检查器名称
     *
     * @return 检查器名称
     */
    String getName();

    /**
     * 检查是否启用
     *
     * @return true-启用, false-禁用
     */
    default boolean isEnabled() {
        return true;
    }
}
