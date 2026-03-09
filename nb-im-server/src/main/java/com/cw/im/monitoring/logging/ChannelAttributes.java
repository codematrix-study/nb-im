package com.cw.im.monitoring.logging;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

/**
 * Channel属性键定义
 *
 * <p>定义Netty Channel中使用的属性键常量</p>
 *
 * @author cw
 * @since 1.0.0
 */
public class ChannelAttributes {

    /**
     * 用户ID属性键
     */
    public static final AttributeKey<Long> USER_ID = AttributeKey.valueOf("userId");

    /**
     * 设备ID属性键
     */
    public static final AttributeKey<String> DEVICE_ID = AttributeKey.valueOf("deviceId");

    /**
     * 网关ID属性键
     */
    public static final AttributeKey<String> GATEWAY_ID = AttributeKey.valueOf("gatewayId");

    /**
     * 认证状态属性键
     */
    public static final AttributeKey<Boolean> AUTHENTICATED = AttributeKey.valueOf("authenticated");

    /**
     * TraceId属性键
     */
    public static final AttributeKey<String> TRACE_ID = AttributeKey.valueOf("traceId");

    /**
     * 私有构造函数，防止实例化
     */
    private ChannelAttributes() {
    }
}
