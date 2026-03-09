package com.cw.im.server.consumer;

import com.cw.im.common.model.AckMessage;
import com.cw.im.kafka.MessageListener;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.util.concurrent.ConcurrentHashMap;

/**
 * ACK消息消费监听器
 *
 * <p>消费ACK确认消息Topic，更新消息状态</p>
 *
 * <h3>消费逻辑</h3>
 * <ol>
 *     <li>从Kafka拉取ACK消息</li>
 *     <li>反序列化为AckMessage对象</li>
 *     <li>解析ACK信息（msgId, status, reason）</li>
 *     <li>更新消息状态（可使用Redis或数据库）</li>
 *     <li>如果ACK失败，触发重试或告警</li>
 *     <li>记录ACK统计</li>
 * </ol>
 *
 * <h3>消息状态管理</h3>
 * <p>此Consumer消费ACK后，应该更新消息的状态。</p>
 * <p>实际生产环境中，应该将ACK信息存储到数据库或Redis，供业务系统查询。</p>
 *
 * @author cw
 * @since 1.0.0
 */
@Slf4j
public class AckConsumer implements MessageListener {

    private final ObjectMapper objectMapper;

    /**
     * ACK成功计数
     */
    private long ackSuccessCount = 0;

    /**
     * ACK失败计数
     */
    private long ackFailedCount = 0;

    /**
     * ACK超时计数
     */
    private long ackTimeoutCount = 0;

    /**
     * 处理异常计数
     */
    private long processErrorCount = 0;

    /**
     * 消息状态缓存（msgId -> AckMessage）
     * <p>注意：这里只是示例，实际应该使用Redis或数据库存储</p>
     */
    private final ConcurrentHashMap<String, AckMessage> messageStatusCache = new ConcurrentHashMap<>();

    /**
     * 构造函数
     */
    public AckConsumer() {
        this.objectMapper = new ObjectMapper();
        log.info("AckConsumer 初始化完成");
    }

    @Override
    public void onMessage(ConsumerRecord<String, String> record) {
        try {
            // 1. 反序列化ACK消息
            AckMessage ackMessage = parseAckMessage(record.value());
            if (ackMessage == null) {
                log.warn("无效ACK消息，跳过处理: topic={}, partition={}, offset={}",
                        record.topic(), record.partition(), record.offset());
                processErrorCount++;
                return;
            }

            String msgId = ackMessage.getMsgId();
            AckMessage.AckStatus status = ackMessage.getStatus();

            log.info("收到ACK消息: msgId={}, status={}, from={}, to={}, reason={}",
                    msgId, status, ackMessage.getFrom(), ackMessage.getTo(), ackMessage.getReason());

            // 2. 更新消息状态
            updateMessageStatus(ackMessage);

            // 3. 根据ACK状态处理
            switch (status) {
                case SUCCESS:
                    handleSuccessAck(ackMessage);
                    ackSuccessCount++;
                    break;

                case FAILED:
                    handleFailedAck(ackMessage);
                    ackFailedCount++;
                    break;

                case TIMEOUT:
                    handleTimeoutAck(ackMessage);
                    ackTimeoutCount++;
                    break;

                default:
                    log.warn("未知ACK状态: msgId={}, status={}", msgId, status);
                    processErrorCount++;
                    break;
            }

            log.debug("ACK消息处理完成: msgId={}, status={}", msgId, status);

        } catch (Exception e) {
            log.error("处理ACK消息异常: topic={}, partition={}, offset={}",
                    record.topic(), record.partition(), record.offset(), e);
            processErrorCount++;
        }
    }

    @Override
    public void onException(Exception exception) {
        log.error("ACK消息消费异常", exception);
        processErrorCount++;
    }

    @Override
    public void onComplete() {
        // 批量处理完成后可以做一些清理工作
        if (log.isDebugEnabled()) {
            log.debug("ACK消息批量处理完成: success=%d, failed=%d, timeout=%d, error=%d",
                    ackSuccessCount, ackFailedCount, ackTimeoutCount, processErrorCount);
        }
    }

    /**
     * 更新消息状态
     *
     * @param ackMessage ACK消息
     */
    private void updateMessageStatus(AckMessage ackMessage) {
        try {
            String msgId = ackMessage.getMsgId();

            // 1. 存储到缓存（示例）
            messageStatusCache.put(msgId, ackMessage);

            // 2. 实际生产环境中，应该存储到Redis或数据库
            // 例如：
            // redisManager.setex(String.format(RedisKeys.MSG_ACK, msgId),
            //                     3600, objectMapper.writeValueAsString(ackMessage));
            //
            // 或者：
            // messageMapper.updateMessageStatus(msgId, ackMessage.getStatus());

            log.debug("更新消息状态: msgId={}, status={}", msgId, ackMessage.getStatus());

        } catch (Exception e) {
            log.error("更新消息状态失败: msgId={}", ackMessage.getMsgId(), e);
            throw e;
        }
    }

    /**
     * 处理成功ACK
     *
     * @param ackMessage ACK消息
     */
    private void handleSuccessAck(AckMessage ackMessage) {
        log.info("消息投递成功: msgId={}, from={}, to={}",
                ackMessage.getMsgId(), ackMessage.getFrom(), ackMessage.getTo());

        // 1. 可以在这里更新消息状态为"已送达"
        // 2. 可以通知业务系统消息已成功送达
        // 3. 可以触发其他业务逻辑

        // 示例：清理重试队列中的消息
        // retryQueue.remove(ackMessage.getMsgId());
    }

    /**
     * 处理失败ACK
     *
     * @param ackMessage ACK消息
     */
    private void handleFailedAck(AckMessage ackMessage) {
        log.warn("消息投递失败: msgId={}, from={}, to={}, reason={}",
                ackMessage.getMsgId(), ackMessage.getFrom(), ackMessage.getTo(), ackMessage.getReason());

        // 1. 可以在这里更新消息状态为"投递失败"
        // 2. 可以将消息加入重试队列
        // 3. 可以触发告警通知

        // 示例：加入重试队列
        // retryQueue.add(ackMessage.getMsgId(), ackMessage);

        // 示例：发送告警
        // alertService.sendAlert("消息投递失败: " + ackMessage.getMsgId());
    }

    /**
     * 处理超时ACK
     *
     * @param ackMessage ACK消息
     */
    private void handleTimeoutAck(AckMessage ackMessage) {
        log.warn("消息投递超时: msgId={}, from={}, to={}",
                ackMessage.getMsgId(), ackMessage.getFrom(), ackMessage.getTo());

        // 1. 可以在这里更新消息状态为"投递超时"
        // 2. 可以将消息加入重试队列
        // 3. 可以触发告警通知

        // 示例：加入重试队列
        // retryQueue.add(ackMessage.getMsgId(), ackMessage);

        // 示例：发送告警
        // alertService.sendAlert("消息投递超时: " + ackMessage.getMsgId());
    }

    /**
     * 解析ACK消息
     *
     * @param jsonStr JSON字符串
     * @return AckMessage对象
     */
    private AckMessage parseAckMessage(String jsonStr) {
        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            return null;
        }

        try {
            return objectMapper.readValue(jsonStr, AckMessage.class);
        } catch (Exception e) {
            log.error("解析ACK消息失败: json={}", jsonStr, e);
            return null;
        }
    }

    /**
     * 查询消息状态
     *
     * @param msgId 消息ID
     * @return ACK消息（如果存在）
     */
    public AckMessage getMessageStatus(String msgId) {
        return messageStatusCache.get(msgId);
    }

    // ==================== 统计方法 ====================

    /**
     * 获取ACK成功数
     *
     * @return 成功数量
     */
    public long getAckSuccessCount() {
        return ackSuccessCount;
    }

    /**
     * 获取ACK失败数
     *
     * @return 失败数量
     */
    public long getAckFailedCount() {
        return ackFailedCount;
    }

    /**
     * 获取ACK超时数
     *
     * @return 超时数量
     */
    public long getAckTimeoutCount() {
        return ackTimeoutCount;
    }

    /**
     * 获取处理异常数
     *
     * @return 异常数量
     */
    public long getProcessErrorCount() {
        return processErrorCount;
    }

    /**
     * 获取统计信息
     *
     * @return 统计信息字符串
     */
    public String getStats() {
        long total = ackSuccessCount + ackFailedCount + ackTimeoutCount + processErrorCount;
        double successRate = total > 0 ? (double) ackSuccessCount / total * 100 : 0;
        double failureRate = total > 0 ? (double) ackFailedCount / total * 100 : 0;
        double timeoutRate = total > 0 ? (double) ackTimeoutCount / total * 100 : 0;

        return String.format(
                "AckConsumer{success=%d(%.2f%%), failed=%d(%.2f%%), timeout=%d(%.2f%%), error=%d}",
                ackSuccessCount, successRate, ackFailedCount, failureRate,
                ackTimeoutCount, timeoutRate, processErrorCount
        );
    }

    /**
     * 重置统计信息
     */
    public void resetStats() {
        ackSuccessCount = 0;
        ackFailedCount = 0;
        ackTimeoutCount = 0;
        processErrorCount = 0;
        log.info("ACK消息统计信息已重置");
    }
}
