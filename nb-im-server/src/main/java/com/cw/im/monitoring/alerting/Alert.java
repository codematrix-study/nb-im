package com.cw.im.monitoring.alerting;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 告警信息
 *
 * <p>封装告警的详细信息</p>
 *
 * @author cw
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Alert {

    /**
     * 告警ID
     */
    private String alertId;

    /**
     * 告警规则名称
     */
    private String ruleName;

    /**
     * 告警级别
     */
    private AlertLevel level;

    /**
     * 告警标题
     */
    private String title;

    /**
     * 告警描述
     */
    private String description;

    /**
     * 告警详情
     */
    private String details;

    /**
     * 指标名称
     */
    private String metricName;

    /**
     * 当前值
     */
    private double currentValue;

    /**
     * 阈值
     */
    private double threshold;

    /**
     * 告警时间
     */
    private LocalDateTime timestamp;

    /**
     * 是否已恢复
     */
    private boolean resolved;

    /**
     * 恢复时间
     */
    private LocalDateTime resolvedAt;

    /**
     * 告警级别枚举
     */
    public enum AlertLevel {
        /**
         * 严重
         */
        CRITICAL,

        /**
         * 警告
         */
        WARNING,

        /**
         * 信息
         */
        INFO
    }
}
