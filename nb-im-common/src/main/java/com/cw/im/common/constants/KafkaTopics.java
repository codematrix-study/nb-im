package com.cw.im.common.constants;

/**
 * Kafka Topic 常量定义
 *
 * <p>统一管理所有Kafka Topic的命名规范</p>
 *
 * <h3>Topic命名规范</h3>
 * <ul>
 *     <li>格式: im-{业务模块}-{功能}</li>
 *     <li>使用连字符(-)分隔</li>
 *     <li>统一使用小写字母</li>
 *     <li>见名知意</li>
 * </ul>
 *
 * <h3>分区策略</h3>
 * <ul>
 *     <li>私聊消息: 使用conversationId（min(from, to) + max(from, to)）作为分区key</li>
 *     <li>群聊消息: 使用groupId作为分区key</li>
 *     <li>公屏消息: 使用固定或轮询分区</li>
 *     <li>ACK消息: 使用msgId作为分区key</li>
 * </ul>
 *
 * @author cw
 * @since 1.0.0
 */
public final class KafkaTopics {

    private KafkaTopics() {
        // 防止实例化
    }

    // ==================== 消息Topic ====================

    /**
     * 客户端发送消息Topic
     * <p>Topic: im-msg-send</p>
     * <p>用途: 接收客户端发送的消息，转发给业务层处理</p>
     * <p>Partitions: 32</p>
     * <p>Replication: 3</p>
     * <p>分区策略: 按conversationId分区</p>
     */
    public static final String MSG_SEND = "im-msg-send";

    /**
     * 消息推送Topic
     * <p>Topic: im-msg-push</p>
     * <p>用途: 业务层推送消息给Gateway，由Gateway推送给客户端</p>
     * <p>Partitions: 32</p>
     * <p>Replication: 3</p>
     * <p>分区策略: 按目标用户ID分区</p>
     */
    public static final String MSG_PUSH = "im-msg-push";

    /**
     * ACK确认消息Topic
     * <p>Topic: im-ack</p>
     * <p>用途: 客户端确认消息接收，用于消息可靠投递</p>
     * <p>Partitions: 16</p>
     * <p>Replication: 3</p>
     * <p>分区策略: 按msgId分区</p>
     */
    public static final String ACK = "im-ack";

    /**
     * 离线消息Topic
     * <p>Topic: im-msg-offline</p>
     * <p>用途: 用户离线时的消息存储</p>
     * <p>Partitions: 16</p>
     * <p>Replication: 3</p>
     */
    public static final String MSG_OFFLINE = "im-msg-offline";

    // ==================== 系统Topic ====================

    /**
     * 系统通知Topic
     * <p>Topic: im-system-notice</p>
     * <p>用途: 系统级通知、维护公告等</p>
     * <p>Partitions: 8</p>
     * <p>Replication: 3</p>
     */
    public static final String SYSTEM_NOTICE = "im-system-notice";

    /**
     * Gateway状态Topic
     * <p>Topic: im-gateway-status</p>
     * <p>用途: Gateway上下线通知、负载信息同步</p>
     * <p>Partitions: 8</p>
     * <p>Replication: 3</p>
     */
    public static final String GATEWAY_STATUS = "im-gateway-status";

    // ==================== Topic配置 ====================

    /**
     * 默认分区数
     */
    public static final int DEFAULT_PARTITIONS = 32;

    /**
     * 默认副本数
     */
    public static final short DEFAULT_REPLICATION_FACTOR = 3;

    // ==================== 工具方法 ====================

    /**
     * 获取消息发送Topic
     *
     * @return Topic名称
     */
    public static String getMsgSendTopic() {
        return MSG_SEND;
    }

    /**
     * 获取消息推送Topic
     *
     * @return Topic名称
     */
    public static String getMsgPushTopic() {
        return MSG_PUSH;
    }

    /**
     * 获取ACK确认Topic
     *
     * @return Topic名称
     */
    public static String getAckTopic() {
        return ACK;
    }

    /**
     * 获取离线消息Topic
     *
     * @return Topic名称
     */
    public static String getMsgOfflineTopic() {
        return MSG_OFFLINE;
    }

    /**
     * 获取系统通知Topic
     *
     * @return Topic名称
     */
    public static String getSystemNoticeTopic() {
        return SYSTEM_NOTICE;
    }

    /**
     * 获取网关状态Topic
     *
     * @return Topic名称
     */
    public static String getGatewayStatusTopic() {
        return GATEWAY_STATUS;
    }
}
