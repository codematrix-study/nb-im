package com.cw.im.core;

import com.cw.im.common.protocol.IMMessage;
import com.cw.im.common.protocol.CommandType;
import com.cw.im.redis.RedisManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 死信队列测试
 *
 * @author cw
 * @since 1.0.0
 */
@DisplayName("死信队列测试")
class DeadLetterQueueTest {

    private RedisManager redisManager;
    private DeadLetterQueue deadLetterQueue;

    @BeforeEach
    void setUp() {
        redisManager = mock(RedisManager.class);
        deadLetterQueue = new DeadLetterQueue(redisManager);
    }

    @Test
    @DisplayName("测试消息入队成功")
    void testEnqueueSuccess() throws Exception {
        // 准备测试数据
        IMMessage message = createTestMessage("msg123", 1001L, 1002L);
        String failureReason = "连接超时";
        when(redisManager.setex(anyString(), anyInt(), anyString())).thenReturn(true);

        // 执行测试
        boolean result = deadLetterQueue.enqueue(message, failureReason);

        // 验证结果
        assertTrue(result);
        verify(redisManager, times(1)).setex(anyString(), anyInt(), anyString());
    }

    @Test
    @DisplayName("测试消息入队失败")
    void testEnqueueFailure() {
        // 准备测试数据
        IMMessage message = createTestMessage("msg123", 1001L, 1002L);
        when(redisManager.setex(anyString(), anyInt(), anyString())).thenReturn(false);

        // 执行测试
        boolean result = deadLetterQueue.enqueue(message, "测试失败");

        // 验证结果
        assertFalse(result);
    }

    @Test
    @DisplayName("测试消息出队成功")
    void testDequeueSuccess() throws Exception {
        // 准备测试数据
        String msgId = "msg123";
        String json = "{\"message\":{\"msgId\":\"msg123\"},\"failureReason\":\"测试失败\"}";
        when(redisManager.get(anyString())).thenReturn(json);
        when(redisManager.del(anyString())).thenReturn(1L);

        // 执行测试
        DeadLetterQueue.DeadLetterMessage result = deadLetterQueue.dequeue(msgId);

        // 验证结果
        assertNotNull(result);
        assertEquals(msgId, result.getMessage().getMsgId());
        verify(redisManager, times(1)).del(anyString());
    }

    @Test
    @DisplayName("测试消息出队失败（消息不存在）")
    void testDequeueNotExists() {
        // 准备测试数据
        String msgId = "msg123";
        when(redisManager.get(anyString())).thenReturn(null);

        // 执行测试
        DeadLetterQueue.DeadLetterMessage result = deadLetterQueue.dequeue(msgId);

        // 验证结果
        assertNull(result);
    }

    @Test
    @DisplayName("测试查询死信消息")
    void testQuery() throws Exception {
        // 准备测试数据
        String msgId = "msg123";
        String json = "{\"message\":{\"msgId\":\"msg123\"},\"failureReason\":\"测试失败\"}";
        when(redisManager.get(anyString())).thenReturn(json);

        // 执行测试
        DeadLetterQueue.DeadLetterMessage result = deadLetterQueue.query(msgId);

        // 验证结果
        assertNotNull(result);
        assertEquals(msgId, result.getMessage().getMsgId());
    }

    @Test
    @DisplayName("测试检查消息存在")
    void testExists() {
        // 准备测试数据
        String msgId = "msg123";
        when(redisManager.exists(anyString())).thenReturn(true);

        // 执行测试
        boolean result = deadLetterQueue.exists(msgId);

        // 验证结果
        assertTrue(result);
    }

    @Test
    @DisplayName("测试删除死信消息")
    void testDelete() {
        // 准备测试数据
        String msgId = "msg123";
        when(redisManager.del(anyString())).thenReturn(1L);

        // 执行测试
        boolean result = deadLetterQueue.delete(msgId);

        // 验证结果
        assertTrue(result);
    }

    @Test
    @DisplayName("测试获取消息TTL")
    void testGetTTL() {
        // 准备测试数据
        String msgId = "msg123";
        when(redisManager.ttl(anyString())).thenReturn(3600L);

        // 执行测试
        long ttl = deadLetterQueue.getTTL(msgId);

        // 验证结果
        assertEquals(3600L, ttl);
    }

    @Test
    @DisplayName("测试重新处理消息成功")
    void testReprocessSuccess() throws Exception {
        // 准备测试数据
        String msgId = "msg123";
        String json = "{\"message\":{\"msgId\":\"msg123\"},\"failureReason\":\"测试失败\"}";
        when(redisManager.get(anyString())).thenReturn(json);
        when(redisManager.del(anyString())).thenReturn(1L);

        DeadLetterQueue.ReprocessCallback callback = mock(DeadLetterQueue.ReprocessCallback.class);
        when(callback.onReprocess(any())).thenReturn(true);

        // 执行测试
        boolean result = deadLetterQueue.reprocess(msgId, callback);

        // 验证结果
        assertTrue(result);
        verify(callback, times(1)).onReprocess(any());
    }

    @Test
    @DisplayName("测试重新处理消息失败")
    void testReprocessFailure() throws Exception {
        // 准备测试数据
        String msgId = "msg123";
        String json = "{\"message\":{\"msgId\":\"msg123\"},\"failureReason\":\"测试失败\"}";
        when(redisManager.get(anyString())).thenReturn(json);
        when(redisManager.del(anyString())).thenReturn(1L);
        when(redisManager.setex(anyString(), anyInt(), anyString())).thenReturn(true);

        DeadLetterQueue.ReprocessCallback callback = mock(DeadLetterQueue.ReprocessCallback.class);
        when(callback.onReprocess(any())).thenReturn(false);

        // 执行测试
        boolean result = deadLetterQueue.reprocess(msgId, callback);

        // 验证结果
        assertFalse(result);
    }

    @Test
    @DisplayName("测试获取统计信息")
    void testGetStats() {
        // 执行测试
        String stats = deadLetterQueue.getStats();

        // 验证结果
        assertNotNull(stats);
        assertTrue(stats.contains("DeadLetterQueue"));
    }

    @Test
    @DisplayName("测试重置统计信息")
    void testResetStats() {
        // 执行测试
        deadLetterQueue.resetStats();

        // 验证结果
        assertEquals(0, deadLetterQueue.getTotalEnqueuedCount());
        assertEquals(0, deadLetterQueue.getTotalDequeuedCount());
        assertEquals(0, deadLetterQueue.getTotalReprocessedCount());
    }

    @Test
    @DisplayName("测试估算队列大小")
    void testGetEstimatedSize() {
        // 执行测试
        long size = deadLetterQueue.getEstimatedSize();

        // 验证结果
        assertTrue(size >= 0);
    }

    /**
     * 创建测试消息
     */
    private IMMessage createTestMessage(String msgId, Long from, Long to) {
        IMMessage message = new IMMessage();
        message.setMsgId(msgId);
        message.setCmd(CommandType.PRIVATE_CHAT);
        message.setFrom(from);
        message.setTo(to);
        message.setContent("test content");
        message.setTimestamp(System.currentTimeMillis());
        return message;
    }
}
