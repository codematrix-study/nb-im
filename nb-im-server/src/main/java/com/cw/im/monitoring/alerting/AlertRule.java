package com.cw.im.monitoring.alerting;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 告警规则
 *
 * <p>定义触发告警的条件和阈值</p>
 *
 * @author cw
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertRule {

    /**
     * 规则名称
     */
    private String name;

    /**
     * 规则描述
     */
    private String description;

    /**
     * 指标名称
     */
    private String metricName;

    /**
     * 比较操作符
     */
    private Operator operator;

    /**
     * 阈值
     */
    private double threshold;

    /**
     * 告警级别
     */
    private Alert.AlertLevel level;

    /**
     * 是否启用
     */
    private boolean enabled;

    /**
     * 持续时间（秒），超过此时间才触发告警
     */
    private long durationSeconds;

    /**
     * 比较操作符枚举
     */
    public enum Operator {
        /**
         * 大于
         */
        GREATER_THAN,

        /**
         * 小于
         */
        LESS_THAN,

        /**
         * 等于
         */
        EQUAL,

        /**
         * 大于等于
         */
        GREATER_THAN_OR_EQUAL,

        /**
         * 小于等于
         */
        LESS_THAN_OR_EQUAL,

        /**
         * 不等于
         */
        NOT_EQUAL
    }

    /**
     * 检查值是否满足告警条件
     *
     * @param value 当前值
     * @return true-满足条件, false-不满足
     */
    public boolean matches(double value) {
        if (!enabled) {
            return false;
        }

        switch (operator) {
            case GREATER_THAN:
                return value > threshold;
            case LESS_THAN:
                return value < threshold;
            case EQUAL:
                return value == threshold;
            case GREATER_THAN_OR_EQUAL:
                return value >= threshold;
            case LESS_THAN_OR_EQUAL:
                return value <= threshold;
            case NOT_EQUAL:
                return value != threshold;
            default:
                return false;
        }
    }
}
