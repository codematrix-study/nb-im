package com.cw.im.core;

import com.cw.im.common.model.MessageStatus;
import com.cw.im.redis.RedisManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 消息状态跟踪器测试
 *
 * @author cw
 * @since 1.0.0
 */
@DisplayName("消息状态跟踪器测试")
class MessageStatusTrackerTest {

    private RedisManager redisManager;
    private MessageStatusTracker statusTracker;

    @BeforeEach
    void setUp() {
        redisManager = mock(RedisManager.class);
        statusTracker = new MessageStatusTracker(redisManager);
    }

    @Test
    @DisplayName("测试创建消息状态")
    void testCreateStatus() {
        // 准备测试数据
        String msgId = "msg123";
        Long fromUserId = 1001L;
        Long toUserId = 1002L;
        when(redisManager.setex(anyString(), anyInt(), anyString())).thenReturn(true);

        // 执行测试
        MessageStatus status = statusTracker.createStatus(msgId, fromUserId, toUserId);

        // 验证结果
        assertNotNull(status);
        assertEquals(msgId, status.getMsgId());
        assertEquals(fromUserId, status.getFromUserId());
        assertEquals(toUserId, status.getToUserId());
        assertEquals(MessageStatus.Status.SENDING, status.getStatus());
        assertEquals(0, status.getRetryCount());
    }

    @Test
    @DisplayName("测试更新消息状态")
    void testUpdateStatus() throws Exception {
        // 准备测试数据
        String msgId = "msg123";
        String existingStatusJson = "{\"msgId\":\"msg123\",\"status\":\"SENDING\"}";
        when(redisManager.get(anyString())).thenReturn(existingStatusJson);
        when(redisManager.setex(anyString(), anyInt(), anyString())).thenReturn(true);

        // 先创建状态
        statusTracker.createStatus(msgId, 1001L, 1002L);

        // 执行测试
        boolean result = statusTracker.updateStatus(msgId, MessageStatus.Status.SENT);

        // 验证结果
        assertTrue(result);
    }

    @Test
    @DisplayName("测试获取消息状态")
    void testGetStatus() throws Exception {
        // 准备测试数据
        String msgId = "msg123";
        String statusJson = "{\"msgId\":\"msg123\",\"status\":\"SENT\"}";
        when(redisManager.get(anyString())).thenReturn(statusJson);

        // 执行测试
        MessageStatus status = statusTracker.getStatus(msgId);

        // 验证结果
        assertNotNull(status);
        assertEquals(msgId, status.getMsgId());
        assertEquals(MessageStatus.Status.SENT, status.getStatus());
    }

    @Test
    @DisplayName("测试获取不存在的消息状态")
    void testGetStatusNotExists() {
        // 准备测试数据
        String msgId = "msg123";
        when(redisManager.get(anyString())).thenReturn(null);

        // 执行测试
        MessageStatus status = statusTracker.getStatus(msgId);

        // 验证结果
        assertNull(status);
    }

    @Test
    @DisplayName("测试增加重试次数")
    void testIncrementRetryCount() throws Exception {
        // 准备测试数据
        String msgId = "msg123";
        String statusJson = "{\"msgId\":\"msg123\",\"status\":\"FAILED\",\"retryCount\":0}";
        when(redisManager.get(anyString())).thenReturn(statusJson);
        when(redisManager.setex(anyString(), anyInt(), anyString())).thenReturn(true);

        // 执行测试
        int retryCount = statusTracker.incrementRetryCount(msgId);

        // 验证结果
        assertEquals(1, retryCount);
    }

    @Test
    @DisplayName("测试删除消息状态")
    void testDeleteStatus() {
        // 准备测试数据
        String msgId = "msg123";
        when(redisManager.del(anyString())).thenReturn(1L);

        // 执行测试
        boolean result = statusTracker.deleteStatus(msgId);

        // 验证结果
        assertTrue(result);
    }

    @Test
    @DisplayName("测试清空本地缓存")
    void testClearLocalCache() {
        // 执行测试
        statusTracker.clearLocalCache();

        // 验证结果（不抛异常即为成功）
        assertDoesNotThrow(() -> statusTracker.clearLocalCache());
    }

    @Test
    @DisplayName("测试获取统计信息")
    void testGetStats() {
        // 执行测试
        String stats = statusTracker.getStats();

        // 验证结果
        assertNotNull(stats);
        assertTrue(stats.contains("MessageStatusTracker"));
    }

    @Test
    @DisplayName("测试重置统计信息")
    void testResetStats() {
        // 执行测试
        statusTracker.resetStats();

        // 验证结果
        assertEquals(0, statusTracker.getTotalCreatedCount());
        assertEquals(0, statusTracker.getTotalUpdatedCount());
        assertEquals(0, statusTracker.getTotalQueriedCount());
    }

    @Test
    @DisplayName("测试缓存命中率")
    void testCacheHitRate() {
        // 执行测试（初始状态）
        double hitRate = statusTracker.getCacheHitRate();

        // 验证结果
        assertEquals(0.0, hitRate, 0.001);
    }
}
