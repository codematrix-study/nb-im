package com.cw.im.core;

import com.cw.im.common.constants.RedisKeys;
import com.cw.im.common.model.MessageStatus;
import com.cw.im.redis.RedisManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 消息状态跟踪器
 *
 * <p>负责跟踪和更新消息的生命周期状态</p>
 *
 * <h3>核心功能</h3>
 * <ul>
 *     <li>状态创建：为消息创建初始状态</li>
 *     <li>状态更新：更新消息的当前状态</li>
 *     <li>状态查询：查询消息的当前状态</li>
 *     <li>批量查询：批量查询多个消息的状态</li>
 *     <li>本地缓存：本地缓存热点消息状态</li>
 * </ul>
 *
 * <h3>状态流转</h3>
 * <pre>
 * SENDING → SENT → DELIVERED → READ
 *     ↓
 *   FAILED
 * </pre>
 *
 * @author cw
 * @since 1.0.0
 */
@Slf4j
public class MessageStatusTracker {

    private final RedisManager redisManager;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    /**
     * 本地缓存（消息ID -> 状态）
     */
    private final Map<String, MessageStatus> localCache;

    /**
     * 本地缓存最大大小
     */
    private static final int MAX_CACHE_SIZE = 10000;

    /**
     * 统计信息
     */
    private final AtomicLong totalCreatedCount = new AtomicLong(0);
    private final AtomicLong totalUpdatedCount = new AtomicLong(0);
    private final AtomicLong totalQueriedCount = new AtomicLong(0);
    private final AtomicLong cacheHitCount = new AtomicLong(0);
    private final AtomicLong cacheMissCount = new AtomicLong(0);

    /**
     * 构造函数（使用默认TTL）
     *
     * @param redisManager Redis管理器
     */
    public MessageStatusTracker(RedisManager redisManager) {
        this(redisManager, RedisKeys.MESSAGE_STATUS_TTL);
    }

    /**
     * 构造函数（自定义TTL）
     *
     * @param redisManager Redis管理器
     * @param ttl          状态TTL
     */
    public MessageStatusTracker(RedisManager redisManager, Duration ttl) {
        this.redisManager = redisManager;
        this.objectMapper = new ObjectMapper();
        this.ttl = ttl;
        this.localCache = new ConcurrentHashMap<>(1024);
        log.info("消息状态跟踪器初始化完成: ttl={}", ttl);
    }

    /**
     * 创建消息状态
     *
     * @param msgId      消息ID
     * @param fromUserId 发送者ID
     * @param toUserId   接收者ID
     * @return 消息状态对象
     */
    public MessageStatus createStatus(String msgId, Long fromUserId, Long toUserId) {
        MessageStatus status = MessageStatus.createInitial(msgId, fromUserId, toUserId);
        saveStatus(status);

        totalCreatedCount.incrementAndGet();
        log.debug("创建消息状态: msgId={}, status={}", msgId, status.getStatus());

        return status;
    }

    /**
     * 更新消息状态
     *
     * @param msgId     消息ID
     * @param newStatus 新状态
     * @return true-更新成功, false-更新失败
     */
    public boolean updateStatus(String msgId, MessageStatus.Status newStatus) {
        MessageStatus status = getStatus(msgId);
        if (status == null) {
            log.warn("消息状态不存在，无法更新: msgId={}", msgId);
            return false;
        }

        status.updateStatus(newStatus);
        saveStatus(status);

        totalUpdatedCount.incrementAndGet();
        log.debug("更新消息状态: msgId={}, newStatus={}", msgId, newStatus);

        return true;
    }

    /**
     * 更新消息状态（带失败原因）
     *
     * @param msgId          消息ID
     * @param newStatus      新状态
     * @param failureReason  失败原因
     * @return true-更新成功, false-更新失败
     */
    public boolean updateStatus(String msgId, MessageStatus.Status newStatus, String failureReason) {
        MessageStatus status = getStatus(msgId);
        if (status == null) {
            log.warn("消息状态不存在，无法更新: msgId={}", msgId);
            return false;
        }

        status.updateStatus(newStatus, failureReason);
        saveStatus(status);

        totalUpdatedCount.incrementAndGet();
        log.debug("更新消息状态: msgId={}, newStatus={}, reason={}", msgId, newStatus, failureReason);

        return true;
    }

    /**
     * 增加重试次数
     *
     * @param msgId 消息ID
     * @return 当前重试次数
     */
    public int incrementRetryCount(String msgId) {
        MessageStatus status = getStatus(msgId);
        if (status == null) {
            log.warn("消息状态不存在，无法增加重试次数: msgId={}", msgId);
            return 0;
        }

        status.incrementRetryCount();
        saveStatus(status);

        log.debug("增加消息重试次数: msgId={}, retryCount={}", msgId, status.getRetryCount());

        return status.getRetryCount();
    }

    /**
     * 获取消息状态
     *
     * @param msgId 消息ID
     * @return 消息状态对象，如果不存在则返回null
     */
    public MessageStatus getStatus(String msgId) {
        if (msgId == null || msgId.trim().isEmpty()) {
            return null;
        }

        totalQueriedCount.incrementAndGet();

        // 1. 先查本地缓存
        MessageStatus cached = localCache.get(msgId);
        if (cached != null) {
            cacheHitCount.incrementAndGet();
            log.debug("从本地缓存获取消息状态: msgId={}", msgId);
            return cached;
        }

        cacheMissCount.incrementAndGet();

        // 2. 从Redis加载
        try {
            String key = buildKey(msgId);
            String json = redisManager.get(key);

            if (json == null || json.isEmpty()) {
                log.debug("消息状态不存在: msgId={}", msgId);
                return null;
            }

            // 反序列化
            MessageStatus status = objectMapper.readValue(json, MessageStatus.class);

            // 3. 更新本地缓存
            updateLocalCache(msgId, status);

            log.debug("从Redis加载消息状态: msgId={}, status={}", msgId, status.getStatus());

            return status;

        } catch (Exception e) {
            log.error("获取消息状态异常: msgId={}", msgId, e);
            return null;
        }
    }

    /**
     * 批量获取消息状态
     *
     * @param msgIds 消息ID列表
     * @return 消息ID -> 状态映射
     */
    public Map<String, MessageStatus> getStatusBatch(List<String> msgIds) {
        Map<String, MessageStatus> result = new ConcurrentHashMap<>();

        if (msgIds == null || msgIds.isEmpty()) {
            return result;
        }

        // 并行加载
        msgIds.parallelStream().forEach(msgId -> {
            MessageStatus status = getStatus(msgId);
            if (status != null) {
                result.put(msgId, status);
            }
        });

        log.debug("批量获取消息状态: requested={}, found={}", msgIds.size(), result.size());

        return result;
    }

    /**
     * 删除消息状态
     *
     * @param msgId 消息ID
     * @return true-删除成功, false-删除失败
     */
    public boolean deleteStatus(String msgId) {
        if (msgId == null || msgId.trim().isEmpty()) {
            return false;
        }

        try {
            // 从本地缓存删除
            localCache.remove(msgId);

            // 从Redis删除
            String key = buildKey(msgId);
            long deleted = redisManager.del(key);

            if (deleted > 0) {
                log.debug("删除消息状态: msgId={}", msgId);
                return true;
            } else {
                log.debug("消息状态不存在: msgId={}", msgId);
                return false;
            }

        } catch (Exception e) {
            log.error("删除消息状态异常: msgId={}", msgId, e);
            return false;
        }
    }

    /**
     * 保存消息状态到Redis和本地缓存
     *
     * @param status 消息状态
     */
    private void saveStatus(MessageStatus status) {
        if (status == null || status.getMsgId() == null) {
            return;
        }

        try {
            String msgId = status.getMsgId();
            String key = buildKey(msgId);
            String json = objectMapper.writeValueAsString(status);

            // 保存到Redis
            redisManager.setex(key, (int) ttl.getSeconds(), json);

            // 更新本地缓存
            updateLocalCache(msgId, status);

        } catch (Exception e) {
            log.error("保存消息状态异常: msgId={}", status.getMsgId(), e);
        }
    }

    /**
     * 更新本地缓存（LRU策略）
     *
     * @param msgId  消息ID
     * @param status 消息状态
     */
    private void updateLocalCache(String msgId, MessageStatus status) {
        // 如果缓存已满，清理一部分
        if (localCache.size() >= MAX_CACHE_SIZE) {
            // 简单的清理策略：清理一半
            localCache.clear();
            log.debug("本地缓存已满，已清理");
        }

        localCache.put(msgId, status);
    }

    /**
     * 构建Redis Key
     *
     * @param msgId 消息ID
     * @return Redis Key
     */
    private String buildKey(String msgId) {
        return String.format(RedisKeys.MESSAGE_STATUS, msgId);
    }

    /**
     * 清空本地缓存
     */
    public void clearLocalCache() {
        localCache.clear();
        log.info("本地缓存已清空");
    }

    // ==================== 统计方法 ====================

    /**
     * 获取总创建数量
     */
    public long getTotalCreatedCount() {
        return totalCreatedCount.get();
    }

    /**
     * 获取总更新数量
     */
    public long getTotalUpdatedCount() {
        return totalUpdatedCount.get();
    }

    /**
     * 获取总查询数量
     */
    public long getTotalQueriedCount() {
        return totalQueriedCount.get();
    }

    /**
     * 获取缓存命中数量
     */
    public long getCacheHitCount() {
        return cacheHitCount.get();
    }

    /**
     * 获取缓存未命中数量
     */
    public long getCacheMissCount() {
        return cacheMissCount.get();
    }

    /**
     * 获取缓存命中率
     */
    public double getCacheHitRate() {
        long total = cacheHitCount.get() + cacheMissCount.get();
        if (total == 0) {
            return 0.0;
        }
        return (double) cacheHitCount.get() / total * 100;
    }

    /**
     * 获取统计信息
     */
    public String getStats() {
        return String.format(
                "MessageStatusTracker{created=%d, updated=%d, queried=%d, cacheHit=%d(%.2f%%), cacheSize=%d, ttl=%s}",
                totalCreatedCount.get(),
                totalUpdatedCount.get(),
                totalQueriedCount.get(),
                cacheHitCount.get(),
                getCacheHitRate(),
                localCache.size(),
                ttl
        );
    }

    /**
     * 重置统计信息
     */
    public void resetStats() {
        totalCreatedCount.set(0);
        totalUpdatedCount.set(0);
        totalQueriedCount.set(0);
        cacheHitCount.set(0);
        cacheMissCount.set(0);
        log.info("消息状态跟踪统计信息已重置");
    }
}
