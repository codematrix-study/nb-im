package com.cw.im.common.constants;

import java.util.concurrent.TimeUnit;

/**
 * IM通用常量定义
 *
 * <p>包含系统中使用的各种常量值</p>
 *
 * @author cw
 * @since 1.0.0
 */
public final class IMConstants {

    private IMConstants() {
        // 防止实例化
    }

    // ==================== 协议相关 ====================

    /**
     * 协议魔数（用于协议识别）
     */
    public static final int MAGIC_NUMBER = 0x1A2B3C4D;

    /**
     * 协议版本
     */
    public static final String PROTOCOL_VERSION = "1.0";

    /**
     * 最大消息长度（10MB）
     */
    public static final int MAX_MESSAGE_LENGTH = 10 * 1024 * 1024;

    /**
     * 消息长度字段字节数
     */
    public static final int MESSAGE_LENGTH_FIELD_SIZE = 4;

    // ==================== 编码相关 ====================

    /**
     * 默认字符集
     */
    public static final String DEFAULT_CHARSET = "UTF-8";

    /**
     * 字节数组与字符串转换时的字符集
     */
    public static final java.nio.charset.Charset CHARSET_UTF8 = java.nio.charset.StandardCharsets.UTF_8;

    // ==================== 心跳相关 ====================

    /**
     * 心跳间隔（秒）
     */
    public static final int HEARTBEAT_INTERVAL_SECONDS = 30;

    /**
     * 心跳超时时间（秒）
     * <p>超过此时间未收到心跳，判定为连接超时</p>
     */
    public static final int HEARTBEAT_TIMEOUT_SECONDS = 60;

    /**
     * 心跳读空闲时间（秒）
     */
    public static final int READER_IDLE_TIME_SECONDS = 60;

    /**
     * 心跳写空闲时间（秒）
     */
    public static final int WRITER_IDLE_TIME_SECONDS = 0;

    /**
     * 心跳读写空闲时间（秒）
     */
    public static final int ALL_IDLE_TIME_SECONDS = 0;

    // ==================== 线程池相关 ====================

    /**
     * Netty Boss线程数（默认1）
     */
    public static final int NETTY_BOSS_THREADS = 1;

    /**
     * Netty Worker线程数（默认CPU核心数*2）
     */
    public static final int NETTY_WORKER_THREADS = 0;

    /**
     * TCP连接队列大小
     */
    public static final int SO_BACKLOG = 128;

    /**
     * TCP接收缓冲区大小（32KB）
     */
    public static final int SO_RCVBUF = 32 * 1024;

    /**
     * TCP发送缓冲区大小（32KB）
     */
    public static final int SO_SNDBUF = 32 * 1024;

    // ==================== 连接相关 ====================

    /**
     * 单个用户最大连接数
     */
    public static final int MAX_CONNECTIONS_PER_USER = 10;

    /**
     * 网关最大连接数
     */
    public static final int MAX_CONNECTIONS = 100000;

    // ==================== 消息相关 ====================

    /**
     * 消息重试次数
     */
    public static final int MESSAGE_RETRY_TIMES = 3;

    /**
     * 消息重试间隔（毫秒）
     */
    public static final long MESSAGE_RETRY_INTERVAL_MS = 1000;

    /**
     * 消息ACK超时时间（秒）
     */
    public static final int MESSAGE_ACK_TIMEOUT_SECONDS = 30;

    /**
     * 离线消息保存天数
     */
    public static final int OFFLINE_MESSAGE_RETENTION_DAYS = 7;

    // ==================== 消息可靠性相关 ====================

    /**
     * 消息最大重试次数
     */
    public static final int MAX_MESSAGE_RETRY_COUNT = 3;

    /**
     * 消息重试初始延迟（毫秒）
     */
    public static final long RETRY_INITIAL_DELAY_MS = 1000;

    /**
     * 重试延迟倍数（指数退避）
     */
    public static final double RETRY_BACKOFF_MULTIPLIER = 2.0;

    /**
     * 最大重试延迟（毫秒，60秒）
     */
    public static final long MAX_RETRY_DELAY_MS = 60000;

    /**
     * 消息持久化TTL（小时，24小时）
     */
    public static final int MESSAGE_PERSISTENCE_TTL_HOURS = 24;

    /**
     * 死信队列消息TTL（天，7天）
     */
    public static final int DEAD_LETTER_TTL_DAYS = 7;

    /**
     * 消息状态检查间隔（秒）
     */
    public static final int MESSAGE_STATUS_CHECK_INTERVAL_SECONDS = 10;

    /**
     * 待确认消息批量处理大小
     */
    public static final int PENDING_ACK_BATCH_SIZE = 100;

    /**
     * 消息发送成功率统计窗口大小
     */
    public static final int SUCCESS_RATE_WINDOW_SIZE = 1000;

    // ==================== 时间相关 ====================

    /**
     * 默认超时时间（秒）
     */
    public static final int DEFAULT_TIMEOUT_SECONDS = 5;

    /**
     * 长连接超时时间（秒）
     */
    public static final int LONG_CONNECTION_TIMEOUT_SECONDS = 30;

    /**
     * 1分钟的毫秒数
     */
    public static final long ONE_MINUTE_MS = 60 * 1000;

    /**
     * 1小时的毫秒数
     */
    public static final long ONE_HOUR_MS = 60 * 60 * 1000;

    /**
     * 1天的毫秒数
     */
    public static final long ONE_DAY_MS = 24 * ONE_HOUR_MS;

    // ==================== 设备类型 ====================

    /**
     * 设备类型 - Web
     */
    public static final String DEVICE_TYPE_WEB = "WEB";

    /**
     * 设备类型 - iOS
     */
    public static final String DEVICE_TYPE_IOS = "IOS";

    /**
     * 设备类型 - Android
     */
    public static final String DEVICE_TYPE_ANDROID = "ANDROID";

    /**
     * 设备类型 - PC
     */
    public static final String DEVICE_TYPE_PC = "PC";

    // ==================== 分隔符 ====================

    /**
     * 会话ID分隔符
     */
    public static final String CONVERSATION_ID_SEPARATOR = "_";

    /**
     * 网关ID分隔符
     */
    public static final String GATEWAY_ID_SEPARATOR = "-";

    // ==================== 工具方法 ====================

    /**
     * 构建会话ID
     * <p>规则：较小用户ID_较大用户ID</p>
     *
     * @param userId1 用户1 ID
     * @param userId2 用户2 ID
     * @return 会话ID
     */
    public static String buildConversationId(Long userId1, Long userId2) {
        if (userId1 == null || userId2 == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        long min = Math.min(userId1, userId2);
        long max = Math.max(userId1, userId2);
        return min + CONVERSATION_ID_SEPARATOR + max;
    }

    /**
     * 生成消息ID
     * <p>格式: {timestamp}-{randomUUID}</p>
     *
     * @return 消息ID
     */
    public static String generateMessageId() {
        return System.currentTimeMillis() + "-" + java.util.UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 生成网关ID
     * <p>格式: {IP}-{PORT}</p>
     *
     * @param ip   IP地址
     * @param port 端口
     * @return 网关ID
     */
    public static String generateGatewayId(String ip, int port) {
        return ip.replace(".", "-") + GATEWAY_ID_SEPARATOR + port;
    }

    /**
     * 判断是否为心跳超时
     *
     * @param lastHeartbeatTime 上次心跳时间
     * @param currentTime       当前时间
     * @return true-超时, false-未超时
     */
    public static boolean isHeartbeatTimeout(long lastHeartbeatTime, long currentTime) {
        return (currentTime - lastHeartbeatTime) > TimeUnit.SECONDS.toMillis(HEARTBEAT_TIMEOUT_SECONDS);
    }

    /**
     * 获取当前时间戳（毫秒）
     *
     * @return 时间戳
     */
    public static long getCurrentTimestamp() {
        return System.currentTimeMillis();
    }

    /**
     * 获取当前时间戳（秒）
     *
     * @return 时间戳
     */
    public static long getCurrentTimestampSeconds() {
        return System.currentTimeMillis() / 1000;
    }
}
