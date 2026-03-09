package com.cw.im.server.handler;

import com.cw.im.common.constants.KafkaTopics;
import com.cw.im.common.model.AckMessage;
import com.cw.im.common.protocol.CommandType;
import com.cw.im.common.protocol.IMMessage;
import com.cw.im.core.MessageDeduplicator;
import com.cw.im.kafka.KafkaProducerManager;
import com.cw.im.server.attributes.ChannelAttributes;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;

/**
 * ACK消息处理器
 *
 * <p>专门处理客户端ACK确认消息的Handler</p>
 *
 * <h3>ACK流程</h3>
 * <pre>
 * 1. 客户端收到消息后返回ACK
 * 2. Gateway接收ACK消息
 * 3. AckHandler处理ACK
 * 4. 解析ACK信息（msgId, success, reason）
 * 5. 转发ACK到Kafka（im-ack）
 * 6. 业务系统消费ACK并更新消息状态
 * </pre>
 *
 * @author cw
 * @since 1.0.0
 */
@Slf4j
public class AckHandler {

    private final KafkaProducerManager kafkaProducer;
    private final MessageDeduplicator messageDeduplicator;
    private final ObjectMapper objectMapper;
    private final String gatewayId;

    /**
     * ACK统计
     */
    private long totalAckCount = 0;
    private long successAckCount = 0;
    private long failedAckCount = 0;
    private long forwardFailedCount = 0;

    /**
     * 构造函数
     *
     * @param kafkaProducer       Kafka生产者
     * @param messageDeduplicator 消息去重器
     * @param gatewayId           网关ID
     */
    public AckHandler(KafkaProducerManager kafkaProducer,
                      MessageDeduplicator messageDeduplicator,
                      String gatewayId) {
        this.kafkaProducer = kafkaProducer;
        this.messageDeduplicator = messageDeduplicator;
        this.objectMapper = new ObjectMapper();
        this.gatewayId = gatewayId;
        log.info("AckHandler 初始化完成: gatewayId={}", gatewayId);
    }

    /**
     * 处理ACK消息
     *
     * @param ctx ChannelHandlerContext
     * @param msg IM消息
     */
    public void handle(ChannelHandlerContext ctx, IMMessage msg) {
        totalAckCount++;

        String ackMsgId = msg.getMsgId();
        Long userId = ChannelAttributes.getUserId(ctx.channel());

        try {
            log.info("处理ACK消息: ackMsgId={}, from={}, extras={}",
                    ackMsgId, userId, msg.getBody().getExtras());

            // 1. 检查ACK消息是否重复
            if (messageDeduplicator.isProcessed(ackMsgId)) {
                log.warn("ACK消息重复，跳过处理: ackMsgId={}", ackMsgId);
                return;
            }

            // 2. 解析ACK信息
            AckMessage ackMessage = parseAckMessage(msg, userId);
            if (ackMessage == null) {
                log.warn("解析ACK消息失败: ackMsgId={}", ackMsgId);
                forwardFailedCount++;
                return;
            }

            // 3. 转发ACK到Kafka
            forwardAckToKafka(ackMessage);

            // 4. 更新统计
            if (ackMessage.isSuccess()) {
                successAckCount++;
            } else {
                failedAckCount++;
            }

            log.debug("ACK消息处理完成: ackMsgId={}, originalMsgId={}, status={}",
                    ackMsgId, ackMessage.getMsgId(), ackMessage.getStatus());

        } catch (Exception e) {
            log.error("处理ACK消息失败: ackMsgId={}, userId={}", ackMsgId, userId, e);
            forwardFailedCount++;
        }
    }

    /**
     * 解析ACK消息
     *
     * @param msg    IM消息
     * @param userId 用户ID
     * @return ACK消息
     */
    private AckMessage parseAckMessage(IMMessage msg, Long userId) {
        try {
            java.util.Map<String, Object> extras = msg.getBody().getExtras();

            String originalMsgId = (String) extras.get("msgId");
            Boolean success = (Boolean) extras.get("success");
            String reason = (String) extras.get("reason");

            if (originalMsgId == null) {
                log.warn("ACK消息缺少msgId: extras={}", extras);
                return null;
            }

            // 构建AckMessage
            AckMessage.AckStatus status = AckMessage.AckStatus.SUCCESS;
            if (Boolean.FALSE.equals(success)) {
                status = AckMessage.AckStatus.FAILED;
            }

            return AckMessage.builder()
                    .msgId(originalMsgId)
                    .from(msg.getFrom())
                    .to(msg.getTo())
                    .timestamp(msg.getTimestamp())
                    .status(status)
                    .reason(reason)
                    .gatewayId(gatewayId)
                    .build();

        } catch (Exception e) {
            log.error("解析ACK消息异常: msgId={}", msg.getMsgId(), e);
            return null;
        }
    }

    /**
     * 转发ACK到Kafka
     *
     * @param ackMessage ACK消息
     */
    private void forwardAckToKafka(AckMessage ackMessage) {
        try {
            // 1. 序列化ACK消息为JSON
            String json = objectMapper.writeValueAsString(ackMessage);

            // 2. 使用原始消息ID作为分区key
            String partitionKey = ackMessage.getMsgId();

            // 3. 异步发送到Kafka
            kafkaProducer.sendAsync(KafkaTopics.ACK, partitionKey, json, null);

            log.debug("ACK消息转发到Kafka: originalMsgId={}, status={}",
                    ackMessage.getMsgId(), ackMessage.getStatus());

        } catch (Exception e) {
            log.error("转发ACK到Kafka失败: originalMsgId={}",
                    ackMessage.getMsgId(), e);
            throw new RuntimeException("转发ACK到Kafka失败", e);
        }
    }

    // ==================== 统计方法 ====================

    /**
     * 获取统计信息
     *
     * @return 统计信息字符串
     */
    public String getStats() {
        double successRate = totalAckCount > 0 ? (double) successAckCount / totalAckCount * 100 : 0;
        double failureRate = totalAckCount > 0 ? (double) failedAckCount / totalAckCount * 100 : 0;
        double forwardFailureRate = totalAckCount > 0 ? (double) forwardFailedCount / totalAckCount * 100 : 0;

        return String.format(
                "AckHandler{totalAck=%d, successAck=%d(%.2f%%), failedAck=%d(%.2f%%), forwardFailed=%d(%.2f%%)}",
                totalAckCount, successAckCount, successRate,
                failedAckCount, failureRate, forwardFailedCount, forwardFailureRate
        );
    }

    /**
     * 重置统计信息
     */
    public void resetStats() {
        totalAckCount = 0;
        successAckCount = 0;
        failedAckCount = 0;
        forwardFailedCount = 0;
        log.info("ACK消息统计信息已重置");
    }
}
