package com.cw.im.monitoring.logging;

import com.cw.im.common.protocol.IMMessage;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

/**
 * 审计日志记录器
 *
 * <p>记录系统关键操作的审计日志</p>
 *
 * <h3>审计类型</h3>
 * <ul>
 *     <li>用户登录/登出</li>
 *     <li>消息发送/接收</li>
 *     <li>系统配置变更</li>
 *     <li>权限操作</li>
 *     <li>异常事件</li>
 * </ul>
 *
 * @author cw
 * @since 1.0.0
 */
@Slf4j
public class AuditLogger {

    /**
     * 记录用户登录
     *
     * @param userId   用户ID
     * @param deviceId 设备ID
     * @param gatewayId 网关ID
     */
    public static void logUserLogin(Long userId, String deviceId, String gatewayId) {
        AuditEvent event = AuditEvent.builder()
                .eventType(AuditEventType.USER_LOGIN)
                .userId(userId)
                .deviceId(deviceId)
                .gatewayId(gatewayId)
                .timestamp(LocalDateTime.now())
                .description("用户登录")
                .build();

        logAudit(event);
    }

    /**
     * 记录用户登出
     *
     * @param userId   用户ID
     * @param deviceId 设备ID
     * @param gatewayId 网关ID
     */
    public static void logUserLogout(Long userId, String deviceId, String gatewayId) {
        AuditEvent event = AuditEvent.builder()
                .eventType(AuditEventType.USER_LOGOUT)
                .userId(userId)
                .deviceId(deviceId)
                .gatewayId(gatewayId)
                .timestamp(LocalDateTime.now())
                .description("用户登出")
                .build();

        logAudit(event);
    }

    /**
     * 记录消息发送
     *
     * @param userId  用户ID
     * @param message 消息
     */
    public static void logMessageSend(Long userId, IMMessage message) {
        AuditEvent event = AuditEvent.builder()
                .eventType(AuditEventType.MESSAGE_SEND)
                .userId(userId)
                .messageId(message.getHeader().getMsgId())
                .detail(message.getHeader().getCmd().name())
                .timestamp(LocalDateTime.now())
                .description("消息发送")
                .build();

        logAudit(event);
    }

    /**
     * 记录消息接收
     *
     * @param userId  用户ID
     * @param message 消息
     */
    public static void logMessageReceive(Long userId, IMMessage message) {
        AuditEvent event = AuditEvent.builder()
                .eventType(AuditEventType.MESSAGE_RECEIVE)
                .userId(userId)
                .messageId(message.getHeader().getMsgId())
                .detail(message.getHeader().getCmd().name())
                .timestamp(LocalDateTime.now())
                .description("消息接收")
                .build();

        logAudit(event);
    }

    /**
     * 记录系统配置变更
     *
     * @param configKey 配置键
     * @param oldValue  旧值
     * @param newValue  新值
     */
    public static void logConfigChange(String configKey, String oldValue, String newValue) {
        AuditEvent event = AuditEvent.builder()
                .eventType(AuditEventType.CONFIG_CHANGE)
                .timestamp(LocalDateTime.now())
                .description("配置变更")
                .detail(String.format("key=%s, oldValue=%s, newValue=%s", configKey, oldValue, newValue))
                .build();

        logAudit(event);
    }

    /**
     * 记录异常事件
     *
     * @param eventType 事件类型
     * @param throwable 异常对象
     */
    public static void logException(AuditEventType eventType, Throwable throwable) {
        AuditEvent event = AuditEvent.builder()
                .eventType(eventType)
                .timestamp(LocalDateTime.now())
                .description("异常事件")
                .exception(throwable.getClass().getName())
                .detail(throwable.getMessage())
                .build();

        logAudit(event);
    }

    /**
     * 记录自定义审计事件
     *
     * @param event 审计事件
     */
    public static void logAudit(AuditEvent event) {
        // 使用JSON格式输出审计日志
        String auditLog = String.format(
                "[AUDIT] eventType=%s, userId=%s, deviceId=%s, messageId=%s, gatewayId=%s, description=%s, detail=%s, timestamp=%s",
                event.getEventType(),
                event.getUserId() != null ? event.getUserId() : "N/A",
                event.getDeviceId() != null ? event.getDeviceId() : "N/A",
                event.getMessageId() != null ? event.getMessageId() : "N/A",
                event.getGatewayId() != null ? event.getGatewayId() : "N/A",
                event.getDescription(),
                event.getDetail() != null ? event.getDetail() : "N/A",
                event.getTimestamp()
        );

        log.info(auditLog);
    }
}
