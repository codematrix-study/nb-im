package com.cw.im.server.consumer;

import com.cw.im.common.protocol.IMMessage;
import com.cw.im.kafka.MessageListener;
import com.cw.im.redis.OnlineStatusService;
import com.cw.im.server.channel.ChannelManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;

/**
 * 推送消息消费监听器
 *
 * <p>消费推送消息Topic，推送给在线用户</p>
 *
 * <h3>消费逻辑</h3>
 * <ol>
 *     <li>从Kafka拉取消息</li>
 *     <li>反序列化消息为IMMessage对象</li>
 *     <li>获取目标用户ID</li>
 *     <li>查询用户是否在线</li>
 *     <li>在线则推送给用户所有设备</li>
 *     <li>离线则跳过（业务层处理离线消息存储）</li>
 *     <li>提交Offset（由KafkaConsumerService统一处理）</li>
 * </ol>
 *
 * @author cw
 * @since 1.0.0
 */
@Slf4j
public class PushMessageConsumer implements MessageListener {

    private final ChannelManager channelManager;
    private final OnlineStatusService onlineStatusService;
    private final ObjectMapper objectMapper;

    /**
     * 推送成功计数
     */
    private long pushSuccessCount = 0;

    /**
     * 推送失败计数
     */
    private long pushFailureCount = 0;

    /**
     * 用户离线跳过计数
     */
    private long offlineSkipCount = 0;

    /**
     * 构造函数
     *
     * @param channelManager         Channel管理器
     * @param onlineStatusService    在线状态服务
     */
    public PushMessageConsumer(ChannelManager channelManager, OnlineStatusService onlineStatusService) {
        this.channelManager = channelManager;
        this.onlineStatusService = onlineStatusService;
        this.objectMapper = new ObjectMapper();
        log.info("PushMessageConsumer 初始化完成");
    }

    @Override
    public void onMessage(ConsumerRecord<String, String> record) {
        try {
            // 1. 反序列化消息
            IMMessage message = parseMessage(record.value());
            if (message == null || !message.isValid()) {
                log.warn("无效消息，跳过推送: topic={}, partition={}, offset={}, value={}",
                        record.topic(), record.partition(), record.offset(), record.value());
                pushFailureCount++;
                return;
            }

            String msgId = message.getMsgId();
            Long toUserId = message.getTo();

            // 2. 获取目标用户ID
            if (toUserId == null) {
                log.warn("消息缺少接收者ID，跳过推送: msgId={}", msgId);
                pushFailureCount++;
                return;
            }

            log.debug("开始处理推送消息: msgId={}, toUserId={}, topic={}, partition={}, offset={}",
                    msgId, toUserId, record.topic(), record.partition(), record.offset());

            // 3. 查询用户是否在线
            boolean isOnline = onlineStatusService.isOnline(toUserId);

            if (!isOnline) {
                // 4. 离线则跳过推送
                log.info("用户离线，跳过推送: userId={}, msgId={}", toUserId, msgId);
                offlineSkipCount++;
                return;
            }

            // 5. 在线则推送给用户所有设备
            int successCount = channelManager.broadcastToUser(toUserId, message);

            if (successCount > 0) {
                log.info("推送消息成功: userId={}, msgId={}, devices={}", toUserId, msgId, successCount);
                pushSuccessCount++;
            } else {
                log.warn("推送消息失败（无活跃Channel）: userId={}, msgId={}", toUserId, msgId);
                pushFailureCount++;
            }

        } catch (Exception e) {
            log.error("处理推送消息异常: topic={}, partition={}, offset={}, value={}",
                    record.topic(), record.partition(), record.offset(), record.value(), e);
            pushFailureCount++;
        }
    }

    @Override
    public void onException(Exception exception) {
        log.error("推送消息消费异常", exception);
        pushFailureCount++;
    }

    @Override
    public void onComplete() {
        // 批量处理完成后可以做一些清理工作
        // 例如：输出统计信息
        if (log.isDebugEnabled()) {
            log.debug("推送消息批量处理完成: success={}, failure={}, offlineSkip={}",
                    pushSuccessCount, pushFailureCount, offlineSkipCount);
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
            log.error("解析消息失败: json={}", jsonStr, e);
            return null;
        }
    }

    // ==================== 统计方法 ====================

    /**
     * 获取推送成功数
     *
     * @return 成功数量
     */
    public long getPushSuccessCount() {
        return pushSuccessCount;
    }

    /**
     * 获取推送失败数
     *
     * @return 失败数量
     */
    public long getPushFailureCount() {
        return pushFailureCount;
    }

    /**
     * 获取离线跳过数
     *
     * @return 跳过数量
     */
    public long getOfflineSkipCount() {
        return offlineSkipCount;
    }

    /**
     * 获取统计信息
     *
     * @return 统计信息字符串
     */
    public String getStats() {
        long total = pushSuccessCount + pushFailureCount + offlineSkipCount;
        double successRate = total > 0 ? (double) pushSuccessCount / total * 100 : 0;

        return String.format(
                "PushMessageConsumer{success=%d, failure=%d, offlineSkip=%d, total=%d, successRate=%.2f%%}",
                pushSuccessCount, pushFailureCount, offlineSkipCount, total, successRate
        );
    }

    /**
     * 重置统计信息
     */
    public void resetStats() {
        pushSuccessCount = 0;
        pushFailureCount = 0;
        offlineSkipCount = 0;
        log.info("推送消息统计信息已重置");
    }
}
