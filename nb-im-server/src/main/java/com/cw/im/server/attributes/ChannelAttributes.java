package com.cw.im.server.attributes;

import io.netty.util.AttributeKey;

/**
 * Channel 属性 Key 常量定义
 *
 * <p>统一定义所有绑定到 Netty Channel 上的属性 Key，用于在 Pipeline 中传递和存储数据</p>
 *
 * <h3>使用示例</h3>
 * <pre>
 * // 设置属性
 * channel.attr(ChannelAttributes.USER_ID).set(1001L);
 * channel.attr(ChannelAttributes.AUTHENTICATED).set(true);
 *
 * // 获取属性
 * Long userId = channel.attr(ChannelAttributes.USER_ID).get();
 * Boolean authenticated = channel.attr(ChannelAttributes.AUTHENTICATED).get();
 * </pre>
 *
 * @author cw
 * @since 1.0.0
 */
public final class ChannelAttributes {

    private ChannelAttributes() {
        // 防止实例化
    }

    /**
     * 用户ID
     * <p>类型: Long</p>
     * <p>用途: 标识Channel所属的用户</p>
     */
    public static final AttributeKey<Long> USER_ID = AttributeKey.valueOf("userId");

    /**
     * 设备ID
     * <p>类型: String</p>
     * <p>用途: 标识用户的设备唯一标识</p>
     */
    public static final AttributeKey<String> DEVICE_ID = AttributeKey.valueOf("deviceId");

    /**
     * 设备类型
     * <p>类型: String</p>
     * <p>用途: 标识设备类型（PC、MOBILE、WEB等）</p>
     */
    public static final AttributeKey<String> DEVICE_TYPE = AttributeKey.valueOf("deviceType");

    /**
     * 认证状态
     * <p>类型: Boolean</p>
     * <p>用途: 标识Channel是否已通过认证</p>
     */
    public static final AttributeKey<Boolean> AUTHENTICATED = AttributeKey.valueOf("authenticated");

    /**
     * 连接时间
     * <p>类型: Long</p>
     * <p>用途: 记录Channel建立连接的时间戳（毫秒）</p>
     */
    public static final AttributeKey<Long> CONNECT_TIME = AttributeKey.valueOf("connectTime");

    /**
     * 网关ID
     * <p>类型: String</p>
     * <p>用途: 标识Channel所属的网关ID</p>
     */
    public static final AttributeKey<String> GATEWAY_ID = AttributeKey.valueOf("gatewayId");

    /**
     * 最后心跳时间
     * <p>类型: Long</p>
     * <p>用途: 记录最后一次心跳的时间戳（毫秒）</p>
     */
    public static final AttributeKey<Long> LAST_HEARTBEAT_TIME = AttributeKey.valueOf("lastHeartbeatTime");

    /**
     * 远程地址
     * <p>类型: String</p>
     * <p>用途: 客户端连接的远程地址</p>
     */
    public static final AttributeKey<String> REMOTE_ADDRESS = AttributeKey.valueOf("remoteAddress");

    // ==================== 工具方法 ====================

    /**
     * 获取用户ID
     *
     * @param channel Channel对象
     * @return 用户ID，如果未设置则返回null
     */
    public static Long getUserId(io.netty.channel.Channel channel) {
        return channel.attr(USER_ID).get();
    }

    /**
     * 设置用户ID
     *
     * @param channel Channel对象
     * @param userId  用户ID
     */
    public static void setUserId(io.netty.channel.Channel channel, Long userId) {
        channel.attr(USER_ID).set(userId);
    }

    /**
     * 获取设备ID
     *
     * @param channel Channel对象
     * @return 设备ID，如果未设置则返回null
     */
    public static String getDeviceId(io.netty.channel.Channel channel) {
        return channel.attr(DEVICE_ID).get();
    }

    /**
     * 设置设备ID
     *
     * @param channel  Channel对象
     * @param deviceId 设备ID
     */
    public static void setDeviceId(io.netty.channel.Channel channel, String deviceId) {
        channel.attr(DEVICE_ID).set(deviceId);
    }

    /**
     * 获取设备类型
     *
     * @param channel Channel对象
     * @return 设备类型，如果未设置则返回null
     */
    public static String getDeviceType(io.netty.channel.Channel channel) {
        return channel.attr(DEVICE_TYPE).get();
    }

    /**
     * 设置设备类型
     *
     * @param channel    Channel对象
     * @param deviceType 设备类型
     */
    public static void setDeviceType(io.netty.channel.Channel channel, String deviceType) {
        channel.attr(DEVICE_TYPE).set(deviceType);
    }

    /**
     * 检查是否已认证
     *
     * @param channel Channel对象
     * @return true-已认证, false-未认证
     */
    public static boolean isAuthenticated(io.netty.channel.Channel channel) {
        Boolean authenticated = channel.attr(AUTHENTICATED).get();
        return Boolean.TRUE.equals(authenticated);
    }

    /**
     * 设置认证状态
     *
     * @param channel      Channel对象
     * @param authenticated 认证状态
     */
    public static void setAuthenticated(io.netty.channel.Channel channel, boolean authenticated) {
        channel.attr(AUTHENTICATED).set(authenticated);
    }

    /**
     * 获取连接时间
     *
     * @param channel Channel对象
     * @return 连接时间戳（毫秒），如果未设置则返回null
     */
    public static Long getConnectTime(io.netty.channel.Channel channel) {
        return channel.attr(CONNECT_TIME).get();
    }

    /**
     * 设置连接时间
     *
     * @param channel     Channel对象
     * @param connectTime 连接时间戳（毫秒）
     */
    public static void setConnectTime(io.netty.channel.Channel channel, Long connectTime) {
        channel.attr(CONNECT_TIME).set(connectTime);
    }

    /**
     * 获取网关ID
     *
     * @param channel Channel对象
     * @return 网关ID，如果未设置则返回null
     */
    public static String getGatewayId(io.netty.channel.Channel channel) {
        return channel.attr(GATEWAY_ID).get();
    }

    /**
     * 设置网关ID
     *
     * @param channel   Channel对象
     * @param gatewayId 网关ID
     */
    public static void setGatewayId(io.netty.channel.Channel channel, String gatewayId) {
        channel.attr(GATEWAY_ID).set(gatewayId);
    }

    /**
     * 获取最后心跳时间
     *
     * @param channel Channel对象
     * @return 最后心跳时间戳（毫秒），如果未设置则返回null
     */
    public static Long getLastHeartbeatTime(io.netty.channel.Channel channel) {
        return channel.attr(LAST_HEARTBEAT_TIME).get();
    }

    /**
     * 更新最后心跳时间
     *
     * @param channel Channel对象
     */
    public static void updateHeartbeatTime(io.netty.channel.Channel channel) {
        channel.attr(LAST_HEARTBEAT_TIME).set(System.currentTimeMillis());
    }

    /**
     * 获取远程地址
     *
     * @param channel Channel对象
     * @return 远程地址，如果未设置则返回null
     */
    public static String getRemoteAddress(io.netty.channel.Channel channel) {
        return channel.attr(REMOTE_ADDRESS).get();
    }

    /**
     * 设置远程地址
     *
     * @param channel       Channel对象
     * @param remoteAddress 远程地址
     */
    public static void setRemoteAddress(io.netty.channel.Channel channel, String remoteAddress) {
        channel.attr(REMOTE_ADDRESS).set(remoteAddress);
    }

    /**
     * 清理所有属性
     *
     * @param channel Channel对象
     */
    public static void clearAll(io.netty.channel.Channel channel) {
        channel.attr(USER_ID).set(null);
        channel.attr(DEVICE_ID).set(null);
        channel.attr(DEVICE_TYPE).set(null);
        channel.attr(AUTHENTICATED).set(null);
        channel.attr(CONNECT_TIME).set(null);
        channel.attr(GATEWAY_ID).set(null);
        channel.attr(LAST_HEARTBEAT_TIME).set(null);
        channel.attr(REMOTE_ADDRESS).set(null);
    }
}
