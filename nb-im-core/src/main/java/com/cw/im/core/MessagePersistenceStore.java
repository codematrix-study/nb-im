package com.cw.im.core;

import com.cw.im.common.constants.RedisKeys;
import com.cw.im.common.model.MessageStatus;
import com.cw.im.common.protocol.IMMessage;
import com.cw.im.redis.RedisManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

/**
 * 消息持久化存储
 *
 * <p>负责将消息持久化到Redis，确保消息不会因系统故障而丢失</p>
 *
 * <h3>核心功能</h3>
 * <ul>
 *     <li>消息持久化：在发送前保存消息</li>
 *     <li>消息恢复：从持久化存储中恢复消息</li>
 *     <li>消息删除：成功处理后删除持久化消息</li>
 *     <li>TTL管理：自动清理过期消息</li>
 * </ul>
 *
 * <h3>存储策略</h3>
 * <ul>
 *     <li>所有消息在发送前必须持久化</li>
 *     <li>使用Redis String存储消息内容</li>
 *     <li>设置24小时TTL自动清理</li>
 *     <li>成功投递后可手动删除以释放空间</li>
 * </ul>
 *
 * @author cw
 * @since 1.0.0
 */
@Slf4j
public class MessagePersistenceStore {

    private final RedisManager redisManager;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    /**
     * 统计信息
     */
    private long totalPersistedCount = 0;
    private long totalRecoveredCount = 0;
    private long totalDeletedCount = 0;

    /**
     * 构造函数（使用默认TTL）
     *
     * @param redisManager Redis管理器
     */
    public MessagePersistenceStore(RedisManager redisManager) {
        this(redisManager, RedisKeys.MESSAGE_PERSISTENCE_TTL);
    }

    /**
     * 构造函数（自定义TTL）
     *
     * @param redisManager Redis管理器
     * @param ttl          消息持久化TTL
     */
    public MessagePersistenceStore(RedisManager redisManager, Duration ttl) {
        this.redisManager = redisManager;
        this.objectMapper = new ObjectMapper();
        this.ttl = ttl;
        log.info("消息持久化存储初始化完成: ttl={}", ttl);
    }

    /**
     * 持久化消息
     *
     * <p>在发送消息前调用，将消息保存到Redis</p>
     *
     * @param message IM消息
     * @return true-持久化成功, false-持久化失败
     */
    public boolean persist(IMMessage message) {
        if (message == null || message.getMsgId() == null) {
            log.warn("消息为空或消息ID为空，无法持久化");
            return false;
        }

        try {
            String msgId = message.getMsgId();
            String key = buildKey(msgId);

            // 序列化消息
            String json = objectMapper.writeValueAsString(message);

            // 保存到Redis并设置TTL
            redisManager.setex(key, (int) ttl.getSeconds(), json);
            totalPersistedCount++;
            log.debug("消息持久化成功: msgId={}, ttl={}s", msgId, ttl.getSeconds());
            return true;

        } catch (Exception e) {
            log.error("持久化消息异常: msgId={}", message.getMsgId(), e);
            return false;
        }
    }

    /**
     * 恢复消息
     *
     * <p>从Redis中恢复已持久化的消息</p>
     *
     * @param msgId 消息ID
     * @return IM消息对象，如果不存在则返回null
     */
    public IMMessage recover(String msgId) {
        if (msgId == null || msgId.trim().isEmpty()) {
            log.warn("消息ID为空，无法恢复消息");
            return null;
        }

        try {
            String key = buildKey(msgId);
            String json = redisManager.get(key);

            if (json == null || json.isEmpty()) {
                log.debug("消息不存在或已过期: msgId={}", msgId);
                return null;
            }

            // 反序列化消息
            IMMessage message = objectMapper.readValue(json, IMMessage.class);

            totalRecoveredCount++;
            log.debug("消息恢复成功: msgId={}", msgId);

            return message;

        } catch (Exception e) {
            log.error("恢复消息异常: msgId={}", msgId, e);
            return null;
        }
    }

    /**
     * 删除持久化消息
     *
     * <p>消息成功投递后调用，释放存储空间</p>
     *
     * @param msgId 消息ID
     * @return true-删除成功, false-删除失败
     */
    public boolean delete(String msgId) {
        if (msgId == null || msgId.trim().isEmpty()) {
            log.warn("消息ID为空，无法删除消息");
            return false;
        }

        try {
            String key = buildKey(msgId);
            long deleted = redisManager.del(key);

            if (deleted > 0) {
                totalDeletedCount++;
                log.debug("删除持久化消息成功: msgId={}", msgId);
                return true;
            } else {
                log.debug("消息不存在: msgId={}", msgId);
                return false;
            }

        } catch (Exception e) {
            log.error("删除持久化消息异常: msgId={}", msgId, e);
            return false;
        }
    }

    /**
     * 检查消息是否存在
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
            log.error("检查消息存在性异常: msgId={}", msgId, e);
            return false;
        }
    }

    /**
     * 更新消息TTL
     *
     * <p>延长消息的存活时间</p>
     *
     * @param msgId    消息ID
     * @param duration 新的TTL
     * @return true-更新成功, false-更新失败
     */
    public boolean updateTTL(String msgId, Duration duration) {
        if (msgId == null || msgId.trim().isEmpty()) {
            return false;
        }

        try {
            String key = buildKey(msgId);
            return redisManager.expire(key, (int) duration.getSeconds());
        } catch (Exception e) {
            log.error("更新消息TTL异常: msgId={}", msgId, e);
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
            log.error("获取消息TTL异常: msgId={}", msgId, e);
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
        return String.format(RedisKeys.MESSAGE_PERSISTENCE, msgId);
    }

    // ==================== 统计方法 ====================

    /**
     * 获取总持久化数量
     *
     * @return 总持久化数量
     */
    public long getTotalPersistedCount() {
        return totalPersistedCount;
    }

    /**
     * 获取总恢复数量
     *
     * @return 总恢复数量
     */
    public long getTotalRecoveredCount() {
        return totalRecoveredCount;
    }

    /**
     * 获取总删除数量
     *
     * @return 总删除数量
     */
    public long getTotalDeletedCount() {
        return totalDeletedCount;
    }

    /**
     * 获取统计信息
     *
     * @return 统计信息字符串
     */
    public String getStats() {
        return String.format(
                "MessagePersistenceStore{persisted=%d, recovered=%d, deleted=%d, ttl=%s}",
                totalPersistedCount, totalRecoveredCount, totalDeletedCount, ttl
        );
    }

    /**
     * 重置统计信息
     */
    public void resetStats() {
        totalPersistedCount = 0;
        totalRecoveredCount = 0;
        totalDeletedCount = 0;
        log.info("消息持久化统计信息已重置");
    }
}
