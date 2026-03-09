package com.cw.im.monitoring.logging;

import com.cw.im.common.protocol.IMMessage;
import io.netty.channel.Channel;
import org.slf4j.MDC;

import java.util.UUID;

/**
 * MDC（Mapped Diagnostic Context）工具类
 *
 * <p>用于在日志中添加上下文信息，便于日志追踪和问题定位</p>
 *
 * <h3>MDC Keys</h3>
 * <ul>
 *     <li>traceId: 全链路追踪ID</li>
 *     <li>userId: 用户ID</li>
 *     <li>deviceId: 设备ID</li>
 *     <li>msgId: 消息ID</li>
 *     <li>gatewayId: 网关ID</li>
 *     <li>channelId: 通道ID</li>
 * </ul>
 *
 * @author cw
 * @since 1.0.0
 */
public class MDCUtils {

    /**
     * MDC Key常量
     */
    public static final String TRACE_ID = "traceId";
    public static final String USER_ID = "userId";
    public static final String DEVICE_ID = "deviceId";
    public static final String MSG_ID = "msgId";
    public static final String GATEWAY_ID = "gatewayId";
    public static final String CHANNEL_ID = "channelId";
    public static final String MESSAGE_TYPE = "messageType";

    /**
     * 设置traceId
     *
     * @param traceId 追踪ID
     */
    public static void setTraceId(String traceId) {
        if (traceId != null) {
            MDC.put(TRACE_ID, traceId);
        }
    }

    /**
     * 生成并设置新的traceId
     *
     * @return 生成的traceId
     */
    public static String generateAndSetTraceId() {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        MDC.put(TRACE_ID, traceId);
        return traceId;
    }

    /**
     * 设置用户ID
     *
     * @param userId 用户ID
     */
    public static void setUserId(Long userId) {
        if (userId != null) {
            MDC.put(USER_ID, String.valueOf(userId));
        }
    }

    /**
     * 设置设备ID
     *
     * @param deviceId 设备ID
     */
    public static void setDeviceId(String deviceId) {
        if (deviceId != null) {
            MDC.put(DEVICE_ID, deviceId);
        }
    }

    /**
     * 设置消息ID
     *
     * @param msgId 消息ID
     */
    public static void setMsgId(String msgId) {
        if (msgId != null) {
            MDC.put(MSG_ID, msgId);
        }
    }

    /**
     * 设置网关ID
     *
     * @param gatewayId 网关ID
     */
    public static void setGatewayId(String gatewayId) {
        if (gatewayId != null) {
            MDC.put(GATEWAY_ID, gatewayId);
        }
    }

    /**
     * 设置通道ID
     *
     * @param channel Netty通道
     */
    public static void setChannelId(Channel channel) {
        if (channel != null) {
            MDC.put(CHANNEL_ID, channel.id().asLongText());
        }
    }

    /**
     * 设置消息类型
     *
     * @param messageType 消息类型
     */
    public static void setMessageType(String messageType) {
        if (messageType != null) {
            MDC.put(MESSAGE_TYPE, messageType);
        }
    }

    /**
     * 从消息设置MDC
     *
     * @param message IM消息
     */
    public static void setFromMessage(IMMessage message) {
        if (message != null && message.getHeader() != null) {
            setMsgId(message.getHeader().getMsgId());
            setUserId(message.getHeader().getFrom());
            if (message.getHeader().getCmd() != null) {
                setMessageType(message.getHeader().getCmd().name());
            }
        }
    }

    /**
     * 从通道设置MDC
     *
     * @param channel Netty通道
     */
    public static void setFromChannel(Channel channel) {
        if (channel != null) {
            setChannelId(channel);

            // 从Channel属性获取用户ID和设备ID
            Long userId = channel.attr(ChannelAttributes.USER_ID).get();
            if (userId != null) {
                setUserId(userId);
            }

            String deviceId = channel.attr(ChannelAttributes.DEVICE_ID).get();
            if (deviceId != null) {
                setDeviceId(deviceId);
            }

            String gatewayId = channel.attr(ChannelAttributes.GATEWAY_ID).get();
            if (gatewayId != null) {
                setGatewayId(gatewayId);
            }
        }
    }

    /**
     * 清除MDC
     */
    public static void clear() {
        MDC.clear();
    }

    /**
     * 移除指定的MDC key
     *
     * @param key MDC key
     */
    public static void remove(String key) {
        MDC.remove(key);
    }

    /**
     * 获取traceId
     *
     * @return traceId
     */
    public static String getTraceId() {
        return MDC.get(TRACE_ID);
    }

    /**
     * 获取用户ID
     *
     * @return 用户ID字符串
     */
    public static String getUserId() {
        return MDC.get(USER_ID);
    }

    /**
     * 执行带有MDC上下文的Runnable
     *
     * @param runnable Runnable任务
     * @return 包装后的Runnable
     */
    public static Runnable wrapRunnable(Runnable runnable) {
        return new MDCCopyRunnable(runnable, MDC.getCopyOfContextMap());
    }

    /**
     * MDC上下文复制Runnable
     */
    private static class MDCCopyRunnable implements Runnable {
        private final Runnable runnable;
        private final java.util.Map<String, String> contextMap;

        MDCCopyRunnable(Runnable runnable, java.util.Map<String, String> contextMap) {
            this.runnable = runnable;
            this.contextMap = contextMap;
        }

        @Override
        public void run() {
            if (contextMap != null) {
                MDC.setContextMap(contextMap);
            }
            try {
                runnable.run();
            } finally {
                MDC.clear();
            }
        }
    }
}
