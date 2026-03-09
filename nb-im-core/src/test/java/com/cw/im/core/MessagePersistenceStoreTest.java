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
 * 消息持久化存储测试
 *
 * @author cw
 * @since 1.0.0
 */
@DisplayName("消息持久化存储测试")
class MessagePersistenceStoreTest {

    private RedisManager redisManager;
    private MessagePersistenceStore persistenceStore;

    @BeforeEach
    void setUp() {
        redisManager = mock(RedisManager.class);
        persistenceStore = new MessagePersistenceStore(redisManager);
    }

    @Test
    @DisplayName("测试消息持久化成功")
    void testPersistSuccess() {
        // 准备测试数据
        IMMessage message = createTestMessage("msg123", 1001L, 1002L);
        when(redisManager.setex(anyString(), anyInt(), anyString())).thenReturn(true);

        // 执行测试
        boolean result = persistenceStore.persist(message);

        // 验证结果
        assertTrue(result);
        verify(redisManager, times(1)).setex(anyString(), anyInt(), anyString());
    }

    @Test
    @DisplayName("测试消息持久化失败")
    void testPersistFailure() {
        // 准备测试数据
        IMMessage message = createTestMessage("msg123", 1001L, 1002L);
        when(redisManager.setex(anyString(), anyInt(), anyString())).thenReturn(false);

        // 执行测试
        boolean result = persistenceStore.persist(message);

        // 验证结果
        assertFalse(result);
    }

    @Test
    @DisplayName("测试消息恢复成功")
    void testRecoverSuccess() throws Exception {
        // 准备测试数据
        String msgId = "msg123";
        String json = "{\"msgId\":\"msg123\",\"from\":1001,\"to\":1002}";
        when(redisManager.get(anyString())).thenReturn(json);

        // 执行测试
        IMMessage result = persistenceStore.recover(msgId);

        // 验证结果
        assertNotNull(result);
        assertEquals(msgId, result.getMsgId());
    }

    @Test
    @DisplayName("测试消息恢复失败（消息不存在）")
    void testRecoverNotExists() {
        // 准备测试数据
        String msgId = "msg123";
        when(redisManager.get(anyString())).thenReturn(null);

        // 执行测试
        IMMessage result = persistenceStore.recover(msgId);

        // 验证结果
        assertNull(result);
    }

    @Test
    @DisplayName("测试删除消息成功")
    void testDeleteSuccess() {
        // 准备测试数据
        String msgId = "msg123";
        when(redisManager.del(anyString())).thenReturn(1L);

        // 执行测试
        boolean result = persistenceStore.delete(msgId);

        // 验证结果
        assertTrue(result);
    }

    @Test
    @DisplayName("测试检查消息存在")
    void testExists() {
        // 准备测试数据
        String msgId = "msg123";
        when(redisManager.exists(anyString())).thenReturn(true);

        // 执行测试
        boolean result = persistenceStore.exists(msgId);

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
        long ttl = persistenceStore.getTTL(msgId);

        // 验证结果
        assertEquals(3600L, ttl);
    }

    @Test
    @DisplayName("测试获取统计信息")
    void testGetStats() {
        // 执行测试
        String stats = persistenceStore.getStats();

        // 验证结果
        assertNotNull(stats);
        assertTrue(stats.contains("MessagePersistenceStore"));
    }

    @Test
    @DisplayName("测试重置统计信息")
    void testResetStats() {
        // 执行测试
        persistenceStore.resetStats();

        // 验证结果
        assertEquals(0, persistenceStore.getTotalPersistedCount());
        assertEquals(0, persistenceStore.getTotalRecoveredCount());
        assertEquals(0, persistenceStore.getTotalDeletedCount());
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
