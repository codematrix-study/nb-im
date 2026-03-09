package com.cw.im.core;

import com.cw.im.common.constants.RedisKeys;
import com.cw.im.common.protocol.IMMessage;
import com.cw.im.redis.RedisManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 死信队列
 *
 * <p>负责存储处理失败的消息，供后续人工干预或重新处理</p>
 *
 * <h3>核心功能</h3>
 * <ul>
 *     <li>消息入队：将失败的消息加入死信队列</li>
 *     <li>消息出队：从死信队列中取出消息重新处理</li>
 *     <li>消息查询：查询死信队列中的消息</li>
 *     <li>消息清理：清理过期或已处理的死信消息</li>
 *     <li>统计监控：统计死信队列的大小和处理情况</li>
 * </ul>
 *
 * <h3>使用场景</h3>
 * <ul>
 *     <li>消息重试次数超过上限</li>
 *     <li>消息格式错误无法解析</li>
 *     <li>目标用户不存在或已注销</li>
 *     <li>其他无法自动恢复的错误</li>
 * </ul>
 *
 * @author cw
 * @since 1.0.0
 */
@Slf4j
public class DeadLetterQueue {

    private final RedisManager redisManager;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    /**
     * 统计信息
     */
    private final AtomicLong totalEnqueuedCount = new AtomicLong(0);
    private final AtomicLong totalDequeuedCount = new AtomicLong(0);
    private final AtomicLong totalReprocessedCount = new AtomicLong(0);

    /**
     * 死信消息包装类
     */
    @Data
    public static class DeadLetterMessage {
        /**
         * 原始消息
         */
        private IMMessage message;

        /**
         * 失败原因
         */
        private String failureReason;

        /**
         * 入队时间
         */
        private String enqueueTime;

        /**
         * 重试次数
         */
        private int retryCount;

        /**
         * 额外信息
         */
        private String extras;
    }

    /**
     * 构造函数（使用默认TTL）
     *
     * @param redisManager Redis管理器
     */
    public DeadLetterQueue(RedisManager redisManager) {
        this(redisManager, RedisKeys.DEAD_LETTER_QUEUE_TTL);
    }

    /**
     * 构造函数（自定义TTL）
     *
     * @param redisManager Redis管理器
     * @param ttl          死信消息TTL
     */
    public DeadLetterQueue(RedisManager redisManager, Duration ttl) {
        this.redisManager = redisManager;
        this.objectMapper = new ObjectMapper();
        this.ttl = ttl;
        log.info("死信队列初始化完成: ttl={}", ttl);
    }

    /**
     * 消息入队
     *
     * @param message       消息对象
     * @param failureReason 失败原因
     * @return true-入队成功, false-入队失败
     */
    public boolean enqueue(IMMessage message, String failureReason) {
        if (message == null || message.getMsgId() == null) {
            log.warn("消息为空或消息ID为空，无法入队");
            return false;
        }

        try {
            String msgId = message.getMsgId();
            String key = buildKey(msgId);

            // 创建死信消息
            DeadLetterMessage deadLetterMsg = new DeadLetterMessage();
            deadLetterMsg.setMessage(message);
            deadLetterMsg.setFailureReason(failureReason);
            deadLetterMsg.setEnqueueTime(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            deadLetterMsg.setRetryCount(0);

            // 序列化
            String json = objectMapper.writeValueAsString(deadLetterMsg);

            // 保存到Redis并设置TTL
            redisManager.setex(key, (int) ttl.getSeconds(), json);
            totalEnqueuedCount.incrementAndGet();
            log.info("消息入死信队列: msgId={}, reason={}", msgId, failureReason);
            return true;

        } catch (Exception e) {
            log.error("消息入队异常: msgId={}", message.getMsgId(), e);
            return false;
        }
    }

    /**
     * 消息出队
     *
     * @param msgId 消息ID
     * @return 死信消息对象，如果不存在则返回null
     */
    public DeadLetterMessage dequeue(String msgId) {
        if (msgId == null || msgId.trim().isEmpty()) {
            log.warn("消息ID为空，无法出队");
            return null;
        }

        try {
            String key = buildKey(msgId);
            String json = redisManager.get(key);

            if (json == null || json.isEmpty()) {
                log.debug("死信队列中不存在该消息: msgId={}", msgId);
                return null;
            }

            // 反序列化
            DeadLetterMessage deadLetterMsg = objectMapper.readValue(json, DeadLetterMessage.class);

            // 从Redis删除
            redisManager.del(key);

            totalDequeuedCount.incrementAndGet();
            log.info("消息出死信队列: msgId={}", msgId);

            return deadLetterMsg;

        } catch (Exception e) {
            log.error("消息出队异常: msgId={}", msgId, e);
            return null;
        }
    }

    /**
     * 重新处理死信消息
     *
     * @param msgId          消息ID
     * @param reprocessCallback 重新处理回调
     * @return true-重新处理成功, false-重新处理失败
     */
    public boolean reprocess(String msgId, ReprocessCallback reprocessCallback) {
        DeadLetterMessage deadLetterMsg = dequeue(msgId);
        if (deadLetterMsg == null) {
            log.warn("死信消息不存在，无法重新处理: msgId={}", msgId);
            return false;
        }

        try {
            log.info("重新处理死信消息: msgId={}", msgId);

            // 调用回调重新处理
            boolean success = reprocessCallback.onReprocess(deadLetterMsg.getMessage());

            if (success) {
                totalReprocessedCount.incrementAndGet();
                log.info("死信消息重新处理成功: msgId={}", msgId);
            } else {
                // 重新处理失败，重新入队
                deadLetterMsg.setRetryCount(deadLetterMsg.getRetryCount() + 1);
                enqueue(deadLetterMsg.getMessage(), "重新处理失败: " + deadLetterMsg.getFailureReason());
                log.warn("死信消息重新处理失败，重新入队: msgId={}, retryCount={}", msgId, deadLetterMsg.getRetryCount());
            }

            return success;

        } catch (Exception e) {
            log.error("重新处理死信消息异常: msgId={}", msgId, e);
            // 异常情况下重新入队
            enqueue(deadLetterMsg.getMessage(), "重新处理异常: " + e.getMessage());
            return false;
        }
    }

    /**
     * 查询死信消息
     *
     * @param msgId 消息ID
     * @return 死信消息对象，如果不存在则返回null
     */
    public DeadLetterMessage query(String msgId) {
        if (msgId == null || msgId.trim().isEmpty()) {
            return null;
        }

        try {
            String key = buildKey(msgId);
            String json = redisManager.get(key);

            if (json == null || json.isEmpty()) {
                return null;
            }

            return objectMapper.readValue(json, DeadLetterMessage.class);

        } catch (Exception e) {
            log.error("查询死信消息异常: msgId={}", msgId, e);
            return null;
        }
    }

    /**
     * 检查消息是否在死信队列中
     *
     * @param msgId 消息ID
     * @return true-存在, false-不存在
     */
    public boolean exists(String msgId) {
        if (msgId == null || msgId.trim().isEmpty()) {
            return false;
        }

        try {
            String key = buildKey(msgId);
            return redisManager.exists(key);
        } catch (Exception e) {
            log.error("检查死信消息存在性异常: msgId={}", msgId, e);
            return false;
        }
    }

    /**
     * 删除死信消息
     *
     * @param msgId 消息ID
     * @return true-删除成功, false-删除失败
     */
    public boolean delete(String msgId) {
        if (msgId == null || msgId.trim().isEmpty()) {
            return false;
        }

        try {
            String key = buildKey(msgId);
            long deleted = redisManager.del(key);

            if (deleted > 0) {
                log.info("删除死信消息: msgId={}", msgId);
                return true;
            } else {
                log.debug("死信消息不存在: msgId={}", msgId);
                return false;
            }

        } catch (Exception e) {
            log.error("删除死信消息异常: msgId={}", msgId, e);
            return false;
        }
    }

    /**
     * 获取消息剩余TTL（秒）
     *
     * @param msgId 消息ID
     * @return 剩余TTL（秒），-1表示永久存在，-2表示不存在
     */
    public long getTTL(String msgId) {
        if (msgId == null || msgId.trim().isEmpty()) {
            return -2;
        }

        try {
            String key = buildKey(msgId);
            return redisManager.ttl(key);
        } catch (Exception e) {
            log.error("获取死信消息TTL异常: msgId={}", msgId, e);
            return -2;
        }
    }

    /**
     * 构建Redis Key
     *
     * @param msgId 消息ID
     * @return Redis Key
     */
    private String buildKey(String msgId) {
        return String.format(RedisKeys.DEAD_LETTER_QUEUE, msgId);
    }

    // ==================== 统计方法 ====================

    /**
     * 获取总入队数量
     */
    public long getTotalEnqueuedCount() {
        return totalEnqueuedCount.get();
    }

    /**
     * 获取总出队数量
     */
    public long getTotalDequeuedCount() {
        return totalDequeuedCount.get();
    }

    /**
     * 获取总重新处理数量
     */
    public long getTotalReprocessedCount() {
        return totalReprocessedCount.get();
    }

    /**
     * 获取当前队列大小（估算）
     *
     * <p>注意：这个值是入队减出队的差值，不是实时准确值</p>
     */
    public long getEstimatedSize() {
        return totalEnqueuedCount.get() - totalDequeuedCount.get();
    }

    /**
     * 获取统计信息
     */
    public String getStats() {
        return String.format(
                "DeadLetterQueue{enqueued=%d, dequeued=%d, reprocessed=%d, estimatedSize=%d, ttl=%s}",
                totalEnqueuedCount.get(),
                totalDequeuedCount.get(),
                totalReprocessedCount.get(),
                getEstimatedSize(),
                ttl
        );
    }

    /**
     * 重置统计信息
     */
    public void resetStats() {
        totalEnqueuedCount.set(0);
        totalDequeuedCount.set(0);
        totalReprocessedCount.set(0);
        log.info("死信队列统计信息已重置");
    }

    /**
     * 重新处理回调接口
     */
    public interface ReprocessCallback {
        /**
         * 重新处理消息
         *
         * @param message 消息对象
         * @return true-处理成功, false-处理失败
         */
        boolean onReprocess(IMMessage message);
    }
}
