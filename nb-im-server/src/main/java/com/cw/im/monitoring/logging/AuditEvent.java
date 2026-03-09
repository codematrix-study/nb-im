package com.cw.im.monitoring.logging;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 审计事件
 *
 * <p>封装审计日志的事件信息</p>
 *
 * @author cw
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEvent {

    /**
     * 事件类型
     */
    private AuditEventType eventType;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 设备ID
     */
    private String deviceId;

    /**
     * 消息ID
     */
    private String messageId;

    /**
     * 网关ID
     */
    private String gatewayId;

    /**
     * 事件描述
     */
    private String description;

    /**
     * 详细信息
     */
    private String detail;

    /**
     * 异常类名
     */
    private String exception;

    /**
     * 事件时间戳
     */
    private LocalDateTime timestamp;

    /**
     * 操作结果（成功/失败）
     */
    private String result;
}
