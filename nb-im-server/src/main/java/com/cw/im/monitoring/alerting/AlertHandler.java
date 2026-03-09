package com.cw.im.monitoring.alerting;

/**
 * 告警处理器接口
 *
 * <p>定义告警处理的统一接口</p>
 *
 * @author cw
 * @since 1.0.0
 */
public interface AlertHandler {

    /**
     * 处理告警
     *
     * @param alert 告警信息
     */
    void handle(Alert alert);

    /**
     * 获取处理器名称
     *
     * @return 处理器名称
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
