package com.cw.im.core;

import com.cw.im.common.constants.RedisKeys;
import com.cw.im.redis.RedisManager;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

/**
 * 消息去重器
 *
 * <p>基于Redis实现消息去重功能，防止重复处理同一消息</p>
 *
 * <h3>去重原理</h3>
 * <ul>
 *     <li>使用Redis的SETNX命令（setIfAbsent）实现原子性检查</li>
 *     <li>为每个消息ID设置24小时的TTL</li>
 *     <li>如果Key已存在，说明消息已处理过，返回true</li>
 *     <li>如果Key不存在，设置Key并返回false</li>
 * </ul>
 *
 * <h3>使用场景</h3>
 * <ul>
 *     <li>Kafka消息消费去重</li>
 *     <li>客户端重复消息去重</li>
 *     <li>网络重传导致的重复消息</li>
 * </ul>
 *
 * @author cw
 * @since 1.0.0
 */
@Slf4j
public class MessageDeduplicator {

    /**
     * Redis管理器
     */
    private final RedisManager redisManager;

    /**
     * 消息去重TTL（默认24小时）
     */
    private final Duration ttl;

    /**
     * 去重统计
     */
    private long totalCheckCount = 0;
    private long duplicateCount = 0;
    private long processedCount = 0;

    /**
     * 构造函数（使用默认TTL）
     *
     * @param redisManager Redis管理器
     */
    public MessageDeduplicator(RedisManager redisManager) {
        this(redisManager, RedisKeys.MSG_PROCESSED_TTL);
    }

    /**
     * 构造函数（自定义TTL）
     *
     * @param redisManager Redis管理器
     * @param ttl          过期时间
     */
    public MessageDeduplicator(RedisManager redisManager, Duration ttl) {
        this.redisManager = redisManager;
        this.ttl = ttl;
        log.info("消息去重器初始化完成: ttl={}", ttl);
    }

    /**
     * 检查消息是否已处理
     *
     * <p>如果消息未处理过，会自动标记为已处理</p>
     *
     * @param msgId 消息ID
     * @return true-消息已处理过, false-消息未处理过（已自动标记为处理中）
     */
    public boolean isProcessed(String msgId) {
        if (msgId == null || msgId.trim().isEmpty()) {
            log.warn("消息ID为空，无法检查去重");
            return true; // 视为已处理，避免处理无效消息
        }

        totalCheckCount++;

        try {
            String key = buildKey(msgId);

            // 尝试设置Key（原子操作）
            // 如果Key不存在，设置成功并返回true（首次处理）
            // 如果Key已存在，设置失败并返回false（已处理过）
            Boolean isFirstTime = redisManager.setnx(key, "1", (long) ttl.getSeconds());

            if (Boolean.TRUE.equals(isFirstTime)) {
                // 首次处理
                processedCount++;
                log.debug("消息首次处理: msgId={}", msgId);
                return false;
            } else {
                // 已处理过
                duplicateCount++;
                log.debug("消息重复，跳过处理: msgId={}", msgId);
                return true;
            }

        } catch (Exception e) {
            log.error("检查消息去重异常: msgId={}", msgId, e);
            // 异常情况下返回true，避免重复处理
            return true;
        }
    }

    /**
     * 标记消息为已处理
     *
     * <p>通常不需要手动调用，isProcessed()方法会自动标记</p>
     *
     * @param msgId 消息ID
     */
    public void markAsProcessed(String msgId) {
        if (msgId == null || msgId.trim().isEmpty()) {
            return;
        }

        try {
            String key = buildKey(msgId);
            redisManager.setex(key, (int) ttl.getSeconds(), "1");
            log.debug("标记消息为已处理: msgId={}", msgId);
        } catch (Exception e) {
            log.error("标记消息已处理失败: msgId={}", msgId, e);
        }
    }

    /**
     * 强制删除消息处理记录
     *
     * <p>用于特殊场景，如需要重新处理某条消息</p>
     *
     * @param msgId 消息ID
     */
    public void removeProcessedMark(String msgId) {
        if (msgId == null || msgId.trim().isEmpty()) {
            return;
        }

        try {
            String key = buildKey(msgId);
            redisManager.del(key);
            log.info("删除消息处理记录: msgId={}", msgId);
        } catch (Exception e) {
            log.error("删除消息处理记录失败: msgId={}", msgId, e);
        }
    }

    /**
     * 构建Redis Key
     *
     * @param msgId 消息ID
     * @return Redis Key
     */
    private String buildKey(String msgId) {
        return String.format(RedisKeys.MSG_PROCESSED, msgId);
    }

    /**
     * 检查并处理消息（组合方法）
     *
     * <p>如果消息未处理过，执行指定的处理逻辑</p>
     *
     * @param msgId    消息ID
     * @param runnable 处理逻辑
     * @return true-执行了处理逻辑, false-消息已处理过，跳过
     */
    public boolean processIfNotProcessed(String msgId, Runnable runnable) {
        if (!isProcessed(msgId)) {
            try {
                runnable.run();
                return true;
            } catch (Exception e) {
                log.error("执行消息处理逻辑失败: msgId={}", msgId, e);
                // 处理失败，删除标记，允许重试
                removeProcessedMark(msgId);
                throw e;
            }
        }
        return false;
    }

    // ==================== 统计方法 ====================

    /**
     * 获取总检查次数
     *
     * @return 总检查次数
     */
    public long getTotalCheckCount() {
        return totalCheckCount;
    }

    /**
     * 获取重复消息数量
     *
     * @return 重复消息数量
     */
    public long getDuplicateCount() {
        return duplicateCount;
    }

    /**
     * 获取已处理消息数量
     *
     * @return 已处理消息数量
     */
    public long getProcessedCount() {
        return processedCount;
    }

    /**
     * 获取重复率
     *
     * @return 重复率（百分比）
     */
    public double getDuplicateRate() {
        if (totalCheckCount == 0) {
            return 0.0;
        }
        return (double) duplicateCount / totalCheckCount * 100;
    }

    /**
     * 获取统计信息
     *
     * @return 统计信息字符串
     */
    public String getStats() {
        return String.format(
                "MessageDeduplicator{totalCheck=%d, duplicate=%d, processed=%d, duplicateRate=%.2f%%}",
                totalCheckCount, duplicateCount, processedCount, getDuplicateRate()
        );
    }

    /**
     * 重置统计信息
     */
    public void resetStats() {
        totalCheckCount = 0;
        duplicateCount = 0;
        processedCount = 0;
        log.info("消息去重统计信息已重置");
    }
}
