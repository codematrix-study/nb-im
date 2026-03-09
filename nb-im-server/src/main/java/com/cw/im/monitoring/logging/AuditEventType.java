package com.cw.im.monitoring.logging;

/**
 * 审计事件类型
 *
 * @author cw
 * @since 1.0.0
 */
public enum AuditEventType {

    /**
     * 用户登录
     */
    USER_LOGIN,

    /**
     * 用户登出
     */
    USER_LOGOUT,

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
     * 消息重试
     */
    MESSAGE_RETRY,

    /**
     * 消息失败
     */
    MESSAGE_FAILURE,

    /**
     * 配置变更
     */
    CONFIG_CHANGE,

    /**
     * 系统启动
     */
    SYSTEM_STARTUP,

    /**
     * 系统关闭
     */
    SYSTEM_SHUTDOWN,

    /**
     * Redis异常
     */
    REDIS_ERROR,

    /**
     * Kafka异常
     */
    KAFKA_ERROR,

    /**
     * Netty异常
     */
    NETTY_ERROR,

    /**
     * 其他异常
     */
    OTHER_ERROR
}
