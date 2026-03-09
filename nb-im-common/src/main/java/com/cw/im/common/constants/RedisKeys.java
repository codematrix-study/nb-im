package com.cw.im.common.constants;

import java.time.Duration;

/**
 * Redis Key 常量定义
 *
 * <p>统一管理所有Redis Key的命名规范和格式</p>
 *
 * <h3>Redis Key命名规范</h3>
 * <ul>
 *     <li>格式: im:{业务模块}:{具体标识}:{唯一ID}</li>
 *     <li>使用冒号(:)分隔层级</li>
 *     <li>统一使用小写字母</li>
 *     <li>使用合适的TTL避免内存泄漏</li>
 * </ul>
 *
 * @author cw
 * @since 1.0.0
 */
public final class RedisKeys {

    private RedisKeys() {
        // 防止实例化
    }

    // ==================== 用户在线状态相关 ====================

    /**
     * 用户在线网关
     * <p>Key: im:online:user:{userId}</p>
     * <p>Value: gatewayId</p>
     * <p>TTL: 无（用户下线时删除）</p>
     */
    public static final String ONLINE_USER = "im:online:user:%s";

    /**
     * 用户多端连接列表
     * <p>Key: im:user:channel:{userId}</p>
     * <p>Value: Set[channelId1, channelId2, ...]</p>
     * <p>TTL: 无（用户下线时删除）</p>
     */
    public static final String USER_CHANNELS = "im:user:channel:%s";

    /**
     * 用户心跳时间戳
     * <p>Key: im:heartbeat:user:{userId}:{channelId}</p>
     * <p>Value: timestamp (毫秒)</p>
     * <p>TTL: 120秒（心跳超时时间2倍）</p>
     */
    public static final String HEARTBEAT = "im:heartbeat:user:%s:%s";

    /**
     * 心跳超时时间（秒）
     */
    public static final int HEARTBEAT_TIMEOUT_SECONDS = 120;

    // ==================== 消息去重相关 ====================

    /**
     * 消息处理记录（去重）
     * <p>Key: im:msg:processed:{msgId}</p>
     * <p>Value: "1"</p>
     * <p>TTL: 24小时</p>
     */
    public static final String MSG_PROCESSED = "im:msg:processed:%s";

    /**
     * 消息去重TTL（24小时）
     */
    public static final Duration MSG_PROCESSED_TTL = Duration.ofHours(24);

    // ==================== 消息可靠性相关 ====================

    /**
     * 消息持久化存储
     * <p>Key: im:msg:persistence:{msgId}</p>
     * <p>Value: JSON序列化的消息对象</p>
     * <p>TTL: 24小时</p>
     */
    public static final String MESSAGE_PERSISTENCE = "im:msg:persistence:%s";

    /**
     * 消息持久化TTL
     */
    public static final Duration MESSAGE_PERSISTENCE_TTL = Duration.ofHours(24);

    /**
     * 消息状态跟踪
     * <p>Key: im:msg:status:{msgId}</p>
     * <p>Value: 状态JSON（SENDING/SENT/DELIVERED/READ/FAILED）</p>
     * <p>TTL: 24小时</p>
     */
    public static final String MESSAGE_STATUS = "im:msg:status:%s";

    /**
     * 消息状态TTL
     */
    public static final Duration MESSAGE_STATUS_TTL = Duration.ofHours(24);

    /**
     * 待确认消息集合（按用户分组）
     * <p>Key: im:pending:ack:{userId}</p>
     * <p>Member: msgId</p>
     * <p>TTL: 1小时</p>
     */
    public static final String PENDING_ACK_MESSAGES = "im:pending:ack:%s";

    /**
     * 待确认消息TTL
     */
    public static final Duration PENDING_ACK_TTL = Duration.ofHours(1);

    /**
     * 消息重试计数
     * <p>Key: im:msg:retry:{msgId}</p>
     * <p>Value: 重试次数</p>
     * <p>TTL: 24小时</p>
     */
    public static final String MESSAGE_RETRY_COUNT = "im:msg:retry:%s";

    /**
     * 消息重试计数TTL
     */
    public static final Duration MESSAGE_RETRY_COUNT_TTL = Duration.ofHours(24);

    /**
     * 死信队列
     * <p>Key: im:dlq:{msgId}</p>
     * <p>Value: JSON序列化的失败消息</p>
     * <p>TTL: 7天</p>
     */
    public static final String DEAD_LETTER_QUEUE = "im:dlq:%s";

    /**
     * 死信队列TTL
     */
    public static final Duration DEAD_LETTER_QUEUE_TTL = Duration.ofDays(7);

    /**
     * 消息发送时间戳
     * <p>Key: im:msg:sendtime:{msgId}</p>
     * <p>Value: 发送时间戳（毫秒）</p>
     * <p>TTL: 24小时</p>
     */
    public static final String MESSAGE_SEND_TIME = "im:msg:sendtime:%s";

    /**
     * 消息发送时间戳TTL
     */
    public static final Duration MESSAGE_SEND_TIME_TTL = Duration.ofHours(24);

    /**
     * 消息投递统计
     * <p>Key: im:stats:delivery</p>
     * <p>Value: JSON统计信息</p>
     * <p>TTL: 永久</p>
     */
    public static final String MESSAGE_DELIVERY_STATS = "im:stats:delivery";

    /**
     * 消息可靠性指标
     * <p>Key: im:metrics:reliability</p>
     * <p>Value: JSON指标信息</p>
     * <p>TTL: 永久</p>
     */
    public static final String MESSAGE_RELIABILITY_METRICS = "im:metrics:reliability";

    // ==================== 网关相关 ====================

    /**
     * 网关信息
     * <p>Key: im:gateway:info:{gatewayId}</p>
     * <p>Value: JSON格式的网关信息（IP、端口、连接数等）</p>
     * <p>TTL: 无（网关下线时删除）</p>
     */
    public static final String GATEWAY_INFO = "im:gateway:info:%s";

    /**
     * 网关连接数统计
     * <p>Key: im:gateway:connections:{gatewayId}</p>
     * <p>Value: 当前连接数</p>
     * <p>TTL: 无</p>
     */
    public static final String GATEWAY_CONNECTIONS = "im:gateway:connections:%s";

    // ==================== 工具方法 ====================

    /**
     * 构建用户在线网关Key
     *
     * @param userId 用户ID
     * @return Redis Key
     */
    public static String buildOnlineUserKey(Long userId) {
        return String.format(ONLINE_USER, userId);
    }

    /**
     * 构建用户多端连接Key
     *
     * @param userId 用户ID
     * @return Redis Key
     */
    public static String buildUserChannelsKey(Long userId) {
        return String.format(USER_CHANNELS, userId);
    }

    /**
     * 构建用户心跳Key
     *
     * @param userId    用户ID
     * @param channelId 连接ID
     * @return Redis Key
     */
    public static String buildHeartbeatKey(Long userId, String channelId) {
        return String.format(HEARTBEAT, userId, channelId);
    }

    /**
     * 构建消息去重Key
     *
     * @param msgId 消息ID
     * @return Redis Key
     */
    public static String buildMsgProcessedKey(String msgId) {
        return String.format(MSG_PROCESSED, msgId);
    }

    /**
     * 构建网关信息Key
     *
     * @param gatewayId 网关ID
     * @return Redis Key
     */
    public static String buildGatewayInfoKey(String gatewayId) {
        return String.format(GATEWAY_INFO, gatewayId);
    }

    /**
     * 构建网关连接数Key
     *
     * @param gatewayId 网关ID
     * @return Redis Key
     */
    public static String buildGatewayConnectionsKey(String gatewayId) {
        return String.format(GATEWAY_CONNECTIONS, gatewayId);
    }

    // ==================== 消息可靠性相关工具方法 ====================

    /**
     * 构建消息持久化Key
     *
     * @param msgId 消息ID
     * @return Redis Key
     */
    public static String buildMessagePersistenceKey(String msgId) {
        return String.format(MESSAGE_PERSISTENCE, msgId);
    }

    /**
     * 构建消息状态Key
     *
     * @param msgId 消息ID
     * @return Redis Key
     */
    public static String buildMessageStatusKey(String msgId) {
        return String.format(MESSAGE_STATUS, msgId);
    }

    /**
     * 构建待确认消息集合Key
     *
     * @param userId 用户ID
     * @return Redis Key
     */
    public static String buildPendingAckMessagesKey(Long userId) {
        return String.format(PENDING_ACK_MESSAGES, userId);
    }

    /**
     * 构建消息重试计数Key
     *
     * @param msgId 消息ID
     * @return Redis Key
     */
    public static String buildMessageRetryCountKey(String msgId) {
        return String.format(MESSAGE_RETRY_COUNT, msgId);
    }

    /**
     * 构建死信队列Key
     *
     * @param msgId 消息ID
     * @return Redis Key
     */
    public static String buildDeadLetterQueueKey(String msgId) {
        return String.format(DEAD_LETTER_QUEUE, msgId);
    }

    /**
     * 构建消息发送时间戳Key
     *
     * @param msgId 消息ID
     * @return Redis Key
     */
    public static String buildMessageSendTimeKey(String msgId) {
        return String.format(MESSAGE_SEND_TIME, msgId);
    }
}
