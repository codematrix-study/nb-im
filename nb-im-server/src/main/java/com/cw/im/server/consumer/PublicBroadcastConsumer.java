package com.cw.im.server.consumer;

import com.cw.im.common.protocol.IMMessage;
import com.cw.im.kafka.MessageListener;
import com.cw.im.server.channel.ChannelManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;

/**
 * 公屏消息广播消费监听器
 *
 * <p>消费公屏消息Topic，广播到所有在线用户</p>
 *
 * <h3>消费逻辑</h3>
 * <ol>
 *     <li>从Kafka拉取公屏消息</li>
 *     <li>反序列化消息为IMMessage对象</li>
 *     <li>获取所有在线用户</li>
 *     <li>广播到所有在线用户的所有设备</li>
 *     <li>记录广播统计</li>
 * </ol>
 *
 * <h3>注意事项</h3>
 * <ul>
 *     <li>公屏消息会广播到所有在线用户，数量可能很大</li>
 *     <li>需要考虑性能和消息量控制</li>
 *     <li>建议实现频率限制或权限验证</li>
 * </ul>
 *
 * @author cw
 * @since 1.0.0
 */
@Slf4j
public class PublicBroadcastConsumer implements MessageListener {

    private final ChannelManager channelManager;
    private final ObjectMapper objectMapper;

    /**
     * 广播成功计数
     */
    private long broadcastSuccessCount = 0;

    /**
     * 广播失败计数
     */
    private long broadcastFailureCount = 0;

    /**
     * 总用户数计数
     */
    private long totalUserCount = 0;

    /**
     * 总设备数计数
     */
    private long totalDeviceCount = 0;

    /**
     * 构造函数
     *
     * @param channelManager Channel管理器
     */
    public PublicBroadcastConsumer(ChannelManager channelManager) {
        this.channelManager = channelManager;
        this.objectMapper = new ObjectMapper();
        log.info("PublicBroadcastConsumer 初始化完成");
    }

    @Override
    public void onMessage(ConsumerRecord<String, String> record) {
        try {
            // 1. 反序列化消息
            IMMessage message = parseMessage(record.value());
            if (message == null || !message.isValid()) {
                log.warn("无效公屏消息，跳过广播: topic={}, partition={}, offset={}",
                        record.topic(), record.partition(), record.offset());
                broadcastFailureCount++;
                return;
            }

            String msgId = message.getMsgId();
            Long fromUserId = message.getFrom();

            log.info("开始处理公屏广播消息: msgId={}, from={}, content={}, topic={}, partition={}, offset={}",
                    msgId, fromUserId, message.getContent(), record.topic(), record.partition(), record.offset());

            // 2. 广播到所有在线用户
            broadcastToAllOnlineUsers(message);

            broadcastSuccessCount++;
            log.info("公屏消息广播完成: msgId={}", msgId);

        } catch (Exception e) {
            log.error("处理公屏广播消息异常: topic={}, partition={}, offset={}",
                    record.topic(), record.partition(), record.offset(), e);
            broadcastFailureCount++;
        }
    }

    @Override
    public void onException(Exception exception) {
        log.error("公屏广播消息消费异常", exception);
        broadcastFailureCount++;
    }

    @Override
    public void onComplete() {
        // 批量处理完成后可以做一些清理工作
        if (log.isDebugEnabled()) {
            log.debug("公屏广播消息批量处理完成: success={}, failure={}",
                    broadcastSuccessCount, broadcastFailureCount);
        }
    }

    /**
     * 广播消息到所有在线用户
     *
     * @param message IM消息
     */
    private void broadcastToAllOnlineUsers(IMMessage message) {
        try {
            // 1. 获取所有在线用户ID
            java.util.Set<Long> onlineUserIds = channelManager.getAllOnlineUsers();

            if (onlineUserIds == null || onlineUserIds.isEmpty()) {
                log.warn("当前无在线用户，跳过广播: msgId={}", message.getMsgId());
                return;
            }

            log.info("开始广播公屏消息: msgId={}, onlineUsers={}",
                    message.getMsgId(), onlineUserIds.size());

            // 2. 遍历所有在线用户，推送给每个用户的所有设备
            int userCount = 0;
            int deviceCount = 0;

            for (Long userId : onlineUserIds) {
                try {
                    // 推送给该用户的所有设备
                    int devices = channelManager.broadcastToUser(userId, message);

                    if (devices > 0) {
                        userCount++;
                        deviceCount += devices;
                    }

                } catch (Exception e) {
                    log.error("广播公屏消息给用户失败: userId={}, msgId={}",
                            userId, message.getMsgId(), e);
                }
            }

            totalUserCount += userCount;
            totalDeviceCount += deviceCount;

            log.info("公屏消息广播成功: msgId={}, users={}, devices={}",
                    message.getMsgId(), userCount, deviceCount);

        } catch (Exception e) {
            log.error("广播公屏消息失败: msgId={}", message.getMsgId(), e);
            throw e;
        }
    }

    /**
     * 解析消息
     *
     * @param jsonStr JSON字符串
     * @return IMMessage对象
     */
    private IMMessage parseMessage(String jsonStr) {
        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            return null;
        }

        try {
            return objectMapper.readValue(jsonStr, IMMessage.class);
        } catch (Exception e) {
            log.error("解析公屏消息失败: json={}", jsonStr, e);
            return null;
        }
    }

    // ==================== 统计方法 ====================

    /**
     * 获取广播成功数
     *
     * @return 成功数量
     */
    public long getBroadcastSuccessCount() {
        return broadcastSuccessCount;
    }

    /**
     * 获取广播失败数
     *
     * @return 失败数量
     */
    public long getBroadcastFailureCount() {
        return broadcastFailureCount;
    }

    /**
     * 获取总用户数
     *
     * @return 总用户数
     */
    public long getTotalUserCount() {
        return totalUserCount;
    }

    /**
     * 获取总设备数
     *
     * @return 总设备数
     */
    public long getTotalDeviceCount() {
        return totalDeviceCount;
    }

    /**
     * 获取统计信息
     *
     * @return 统计信息字符串
     */
    public String getStats() {
        long total = broadcastSuccessCount + broadcastFailureCount;
        double successRate = total > 0 ? (double) broadcastSuccessCount / total * 100 : 0;
        double avgUsersPerBroadcast = broadcastSuccessCount > 0 ?
                (double) totalUserCount / broadcastSuccessCount : 0;
        double avgDevicesPerBroadcast = broadcastSuccessCount > 0 ?
                (double) totalDeviceCount / broadcastSuccessCount : 0;

        return String.format(
                "PublicBroadcastConsumer{success=%d, failure=%d, successRate=%.2f%%, " +
                        "totalUsers=%d, totalDevices=%d, avgUsers=%.2f, avgDevices=%.2f}",
                broadcastSuccessCount, broadcastFailureCount, successRate,
                totalUserCount, totalDeviceCount, avgUsersPerBroadcast, avgDevicesPerBroadcast
        );
    }

    /**
     * 重置统计信息
     */
    public void resetStats() {
        broadcastSuccessCount = 0;
        broadcastFailureCount = 0;
        totalUserCount = 0;
        totalDeviceCount = 0;
        log.info("公屏广播消息统计信息已重置");
    }
}
