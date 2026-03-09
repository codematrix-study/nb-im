package com.cw.im.server.handler;

import com.cw.im.common.constants.KafkaTopics;
import com.cw.im.common.protocol.CommandType;
import com.cw.im.common.protocol.IMMessage;
import com.cw.im.core.MessageDeduplicator;
import com.cw.im.kafka.KafkaProducerManager;
import com.cw.im.server.attributes.ChannelAttributes;
import com.cw.im.server.channel.ChannelManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;

/**
 * 公屏消息处理器
 *
 * <p>专门处理公屏消息的Handler，实现完整的公屏消息流程</p>
 *
 * <h3>公屏消息流程</h3>
 * <pre>
 * 发送流程:
 * 1. 客户端发送公屏消息
 * 2. Gateway接收消息
 * 3. PublicChatHandler处理消息
 * 4. 检查消息是否重复
 * 5. 发送到Kafka广播Topic（im-msg-send）
 * 6. 返回ACK给发送者
 *
 * 投递流程（业务层处理）:
 * 1. 业务系统消费Kafka消息
 * 2. 业务系统发送到Kafka广播Topic（im-msg-push）
 * 3. 所有Gateway消费广播消息
 * 4. PublicBroadcastConsumer广播到所有在线用户
 * </pre>
 *
 * @author cw
 * @since 1.0.0
 */
@Slf4j
public class PublicChatHandler {

    private final ChannelManager channelManager;
    private final KafkaProducerManager kafkaProducer;
    private final MessageDeduplicator messageDeduplicator;
    private final ObjectMapper objectMapper;
    private final String gatewayId;

    /**
     * 发送统计
     */
    private long totalSentCount = 0;
    private long kafkaSentCount = 0;
    private long sendFailedCount = 0;

    /**
     * 构造函数
     *
     * @param channelManager         Channel管理器
     * @param kafkaProducer          Kafka生产者
     * @param messageDeduplicator    消息去重器
     */
    public PublicChatHandler(ChannelManager channelManager,
                             KafkaProducerManager kafkaProducer,
                             MessageDeduplicator messageDeduplicator) {
        this.channelManager = channelManager;
        this.kafkaProducer = kafkaProducer;
        this.messageDeduplicator = messageDeduplicator;
        this.objectMapper = new ObjectMapper();
        this.gatewayId = channelManager.getGatewayId();
        log.info("PublicChatHandler 初始化完成");
    }

    /**
     * 处理公屏消息
     *
     * @param ctx ChannelHandlerContext
     * @param msg IM消息
     */
    public void handle(ChannelHandlerContext ctx, IMMessage msg) {
        totalSentCount++;

        Long fromUserId = msg.getFrom();
        String msgId = msg.getMsgId();

        try {
            log.info("处理公屏消息: msgId={}, from={}, content={}",
                    msgId, fromUserId, msg.getContent());

            // 1. 检查消息是否重复
            if (messageDeduplicator.isProcessed(msgId)) {
                log.warn("公屏消息重复，跳过处理: msgId={}", msgId);
                sendAck(ctx, msgId, true, "消息重复（已处理）");
                return;
            }

            // 2. 验证消息格式
            if (!validateMessage(msg)) {
                log.warn("公屏消息格式无效: msgId={}", msgId);
                sendAck(ctx, msgId, false, "消息格式无效");
                sendFailedCount++;
                return;
            }

            // 3. 发送到Kafka广播Topic
            sendToKafka(msg);

            // 4. 返回ACK给发送者
            sendAck(ctx, msgId, true, "公屏消息已接收");

            kafkaSentCount++;
            log.debug("公屏消息处理完成: msgId={}", msgId);

        } catch (Exception e) {
            log.error("处理公屏消息失败: msgId={}, from={}", msgId, fromUserId, e);
            sendAck(ctx, msgId, false, "处理失败: " + e.getMessage());
            sendFailedCount++;
        }
    }

    /**
     * 发送消息到Kafka
     *
     * @param msg IM消息
     */
    private void sendToKafka(IMMessage msg) {
        try {
            // 1. 序列化消息为JSON
            String json = objectMapper.writeValueAsString(msg);

            // 2. 公屏消息使用固定的分区key或轮询
            // 这里使用固定的key，保证公屏消息的顺序性
            String partitionKey = "public-chat";

            // 3. 异步发送到Kafka
            kafkaProducer.sendAsync(KafkaTopics.MSG_SEND, partitionKey, json, null);

            log.debug("公屏消息发送到Kafka: msgId={}, partitionKey={}",
                    msg.getMsgId(), partitionKey);

        } catch (Exception e) {
            log.error("发送公屏消息到Kafka失败: msgId={}", msg.getMsgId(), e);
            throw new RuntimeException("发送到Kafka失败", e);
        }
    }

    /**
     * 验证消息格式
     *
     * @param msg IM消息
     * @return true-有效, false-无效
     */
    private boolean validateMessage(IMMessage msg) {
        return msg.isValid()
                && msg.getCmd() == CommandType.PUBLIC_CHAT
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
        double successRate = totalSentCount > 0 ? (double) kafkaSentCount / totalSentCount * 100 : 0;
        double failureRate = totalSentCount > 0 ? (double) sendFailedCount / totalSentCount * 100 : 0;

        return String.format(
                "PublicChatHandler{totalSent=%d, kafkaSent=%d(%.2f%%), failed=%d(%.2f%%)}",
                totalSentCount, kafkaSentCount, successRate, sendFailedCount, failureRate
        );
    }

    /**
     * 重置统计信息
     */
    public void resetStats() {
        totalSentCount = 0;
        kafkaSentCount = 0;
        sendFailedCount = 0;
        log.info("公屏消息统计信息已重置");
    }
}
