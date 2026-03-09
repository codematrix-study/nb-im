package com.cw.im.server.handler;

import com.cw.im.common.constants.KafkaTopics;
import com.cw.im.common.protocol.CommandType;
import com.cw.im.common.protocol.IMMessage;
import com.cw.im.core.MessageDeduplicator;
import com.cw.im.kafka.KafkaProducerManager;
import com.cw.im.redis.OnlineStatusService;
import com.cw.im.server.attributes.ChannelAttributes;
import com.cw.im.server.channel.ChannelManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;

/**
 * 私聊消息处理器
 *
 * <p>专门处理私聊消息的Handler，实现完整的私聊消息流程</p>
 *
 * <h3>私聊消息流程</h3>
 * <pre>
 * 发送流程:
 * 1. 客户端发送私聊消息
 * 2. Gateway接收消息
 * 3. PrivateChatHandler处理消息
 * 4. 检查消息是否重复
 * 5. 查询目标用户在线状态
 * 6. 在线：直接推送给目标用户
 * 7. 离线：发送到Kafka（im-msg-send）
 * 8. 返回ACK给发送者
 *
 * 投递流程:
 * 1. 业务系统消费Kafka消息
 * 2. 业务系统处理消息（持久化等）
 * 3. 业务系统发送到Kafka（im-msg-push）
 * 4. Gateway消费推送消息
 * 5. PushMessageConsumer推送给目标用户
 * 6. 用户客户端返回ACK
 * </pre>
 *
 * @author cw
 * @since 1.0.0
 */
@Slf4j
public class PrivateChatHandler {

    private final ChannelManager channelManager;
    private final OnlineStatusService onlineStatusService;
    private final KafkaProducerManager kafkaProducer;
    private final MessageDeduplicator messageDeduplicator;
    private final ObjectMapper objectMapper;
    private final String gatewayId;

    /**
     * 发送统计
     */
    private long totalSentCount = 0;
    private long directPushCount = 0;
    private long kafkaSentCount = 0;
    private long sendFailedCount = 0;

    /**
     * 构造函数
     *
     * @param channelManager         Channel管理器
     * @param onlineStatusService    在线状态服务
     * @param kafkaProducer          Kafka生产者
     * @param messageDeduplicator    消息去重器
     */
    public PrivateChatHandler(ChannelManager channelManager,
                              OnlineStatusService onlineStatusService,
                              KafkaProducerManager kafkaProducer,
                              MessageDeduplicator messageDeduplicator) {
        this.channelManager = channelManager;
        this.onlineStatusService = onlineStatusService;
        this.kafkaProducer = kafkaProducer;
        this.messageDeduplicator = messageDeduplicator;
        this.objectMapper = new ObjectMapper();
        this.gatewayId = channelManager.getGatewayId();
        log.info("PrivateChatHandler 初始化完成");
    }

    /**
     * 处理私聊消息
     *
     * @param ctx ChannelHandlerContext
     * @param msg IM消息
     */
    public void handle(ChannelHandlerContext ctx, IMMessage msg) {
        totalSentCount++;

        Long fromUserId = msg.getFrom();
        Long toUserId = msg.getTo();
        String msgId = msg.getMsgId();

        try {
            log.info("处理私聊消息: msgId={}, from={}, to={}, content={}",
                    msgId, fromUserId, toUserId, msg.getContent());

            // 1. 检查消息是否重复
            if (messageDeduplicator.isProcessed(msgId)) {
                log.warn("私聊消息重复，跳过处理: msgId={}", msgId);
                sendAck(ctx, msgId, true, "消息重复（已处理）");
                return;
            }

            // 2. 验证消息格式
            if (!validateMessage(msg)) {
                log.warn("私聊消息格式无效: msgId={}", msgId);
                sendAck(ctx, msgId, false, "消息格式无效");
                sendFailedCount++;
                return;
            }

            // 3. 查询目标用户在线状态
            boolean isOnline = onlineStatusService.isOnline(toUserId);

            if (isOnline) {
                // 4. 目标用户在线，尝试直接推送
                handleOnlineUser(ctx, msg, fromUserId, toUserId, msgId);
            } else {
                // 5. 目标用户离线，发送到Kafka
                handleOfflineUser(ctx, msg, fromUserId, toUserId, msgId);
            }

            // 6. 返回ACK给发送者
            sendAck(ctx, msgId, true, "消息已接收");

        } catch (Exception e) {
            log.error("处理私聊消息失败: msgId={}, from={}, to={}",
                    msgId, fromUserId, toUserId, e);
            sendAck(ctx, msgId, false, "处理失败: " + e.getMessage());
            sendFailedCount++;
        }
    }

    /**
     * 处理在线用户的消息推送
     *
     * @param ctx        ChannelHandlerContext
     * @param msg        IM消息
     * @param fromUserId 发送者ID
     * @param toUserId   接收者ID
     * @param msgId      消息ID
     */
    private void handleOnlineUser(ChannelHandlerContext ctx, IMMessage msg,
                                  Long fromUserId, Long toUserId, String msgId) {
        try {
            log.info("目标用户在线，尝试直接推送: toUserId={}", toUserId);

            // 1. 广播到用户所有设备
            int sentCount = channelManager.broadcastToUser(toUserId, msg);

            if (sentCount > 0) {
                log.info("私聊消息直接推送成功: msgId={}, toUserId={}, devices={}",
                        msgId, toUserId, sentCount);
                directPushCount++;
            } else {
                // 用户在线但没有活跃Channel，发送到Kafka
                log.warn("用户在线但无活跃Channel，发送到Kafka: toUserId={}", toUserId);
                sendToKafka(msg, fromUserId, toUserId);
            }

        } catch (Exception e) {
            log.error("直接推送消息失败: msgId={}, toUserId={}", msgId, toUserId, e);
            // 推送失败，发送到Kafka
            sendToKafka(msg, fromUserId, toUserId);
        }
    }

    /**
     * 处理离线用户的消息
     *
     * @param ctx        ChannelHandlerContext
     * @param msg        IM消息
     * @param fromUserId 发送者ID
     * @param toUserId   接收者ID
     * @param msgId      消息ID
     */
    private void handleOfflineUser(ChannelHandlerContext ctx, IMMessage msg,
                                   Long fromUserId, Long toUserId, String msgId) {
        try {
            log.info("目标用户离线，发送到Kafka: toUserId={}", toUserId);

            // 发送到Kafka，由业务层处理持久化和离线推送
            sendToKafka(msg, fromUserId, toUserId);

        } catch (Exception e) {
            log.error("发送离线消息到Kafka失败: msgId={}, toUserId={}", msgId, toUserId, e);
            throw e;
        }
    }

    /**
     * 发送消息到Kafka
     *
     * @param msg        IM消息
     * @param fromUserId 发送者ID
     * @param toUserId   接收者ID
     */
    private void sendToKafka(IMMessage msg, Long fromUserId, Long toUserId) {
        try {
            // 1. 序列化消息为JSON
            String json = objectMapper.writeValueAsString(msg);

            // 2. 构建分区Key（会话ID）
            String partitionKey = buildConversationId(fromUserId, toUserId);

            // 3. 异步发送到Kafka
            kafkaProducer.sendAsync(KafkaTopics.MSG_SEND, partitionKey, json, null);

            kafkaSentCount++;
            log.debug("私聊消息发送到Kafka: msgId={}, partitionKey={}", msg.getMsgId(), partitionKey);

        } catch (Exception e) {
            log.error("发送私聊消息到Kafka失败: msgId={}", msg.getMsgId(), e);
            throw new RuntimeException("发送到Kafka失败", e);
        }
    }

    /**
     * 构建会话ID
     *
     * <p>保证同一会话的消息进入同一分区，保证顺序性</p>
     *
     * @param from 发送者ID
     * @param to   接收者ID
     * @return 会话ID
     */
    public String buildConversationId(Long from, Long to) {
        // 使用较小的ID在前，较大的ID在后，保证同一会话的分区key一致
        long min = Math.min(from, to);
        long max = Math.max(from, to);
        return min + "-" + max;
    }

    /**
     * 验证消息格式
     *
     * @param msg IM消息
     * @return true-有效, false-无效
     */
    private boolean validateMessage(IMMessage msg) {
        return msg.isValid()
                && msg.getCmd() == CommandType.PRIVATE_CHAT
                && msg.getContent() != null
                && !msg.getContent().trim().isEmpty();
    }

    /**
     * 发送ACK响应
     *
     * @param ctx     ChannelHandlerContext
     * @param msgId   原始消息ID
     * @param success 是否成功
     * @param reason  原因
     */
    private void sendAck(ChannelHandlerContext ctx, String msgId, boolean success, String reason) {
        try {
            Long userId = ChannelAttributes.getUserId(ctx.channel());

            IMMessage ackMessage = IMMessage.builder()
                    .header(com.cw.im.common.model.MessageHeader.builder()
                            .msgId(java.util.UUID.randomUUID().toString())
                            .cmd(CommandType.ACK)
                            .from(0L)  // 系统消息
                            .to(userId != null ? userId : 0L)
                            .timestamp(System.currentTimeMillis())
                            .version("1.0")
                            .build())
                    .body(com.cw.im.common.model.MessageBody.builder()
                            .content(success ? "ACK: " + msgId : "NACK: " + msgId)
                            .contentType("ack")
                            .extras(java.util.Map.of(
                                    "msgId", msgId,
                                    "success", success,
                                    "reason", reason != null ? reason : "",
                                    "gatewayId", gatewayId
                            ))
                            .build())
                    .build();

            ctx.writeAndFlush(ackMessage);
            log.debug("发送ACK: msgId={}, success={}, reason={}", msgId, success, reason);

        } catch (Exception e) {
            log.error("发送ACK失败: msgId={}", msgId, e);
        }
    }

    // ==================== 统计方法 ====================

    /**
     * 获取统计信息
     *
     * @return 统计信息字符串
     */
    public String getStats() {
        double directPushRate = totalSentCount > 0 ? (double) directPushCount / totalSentCount * 100 : 0;
        double kafkaSentRate = totalSentCount > 0 ? (double) kafkaSentCount / totalSentCount * 100 : 0;
        double failureRate = totalSentCount > 0 ? (double) sendFailedCount / totalSentCount * 100 : 0;

        return String.format(
                "PrivateChatHandler{totalSent=%d, directPush=%d(%.2f%%), kafkaSent=%d(%.2f%%), failed=%d(%.2f%%)}",
                totalSentCount, directPushCount, directPushRate,
                kafkaSentCount, kafkaSentRate, sendFailedCount, failureRate
        );
    }

    /**
     * 重置统计信息
     */
    public void resetStats() {
        totalSentCount = 0;
        directPushCount = 0;
        kafkaSentCount = 0;
        sendFailedCount = 0;
        log.info("私聊消息统计信息已重置");
    }
}
