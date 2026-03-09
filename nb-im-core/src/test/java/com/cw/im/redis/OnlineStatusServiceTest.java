package com.cw.im.redis;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 在线状态服务测试
 *
 * <p>测试在线状态服务的完整功能，包括初始化、用户管理和心跳检测</p>
 *
 * @author cw
 * @since 1.0.0
 */
@DisplayName("在线状态服务测试")
class OnlineStatusServiceTest {

    private OnlineStatusService onlineStatusService;
    private RedisManager redisManager;

    private static final Long TEST_USER_ID = 3001L;
    private static final String TEST_GATEWAY_ID = "gateway-test-001";
    private static final String TEST_CHANNEL_ID = "channel-test-001";

    @BeforeEach
    void setUp() {
        // 初始化Redis连接
        redisManager = new RedisManager("192.168.215.3", 6379, null, 0);

        // 创建在线状态服务
        onlineStatusService = new OnlineStatusService(redisManager, 30);

        // 测试Redis连接
        String pong = redisManager.ping();
        assertEquals("PONG", pong, "Redis连接测试失败");
    }

    @AfterEach
    void tearDown() {
        // 关闭服务
        if (onlineStatusService != null && onlineStatusService.isInitialized()) {
            onlineStatusService.shutdown();
        }

        // 清理测试数据
        try {
            if (onlineStatusService != null) {
                onlineStatusService.clearUserData(TEST_USER_ID);
            }
        } catch (Exception e) {
            // 忽略清理错误
        }

        // 关闭Redis连接
        if (redisManager != null) {
            redisManager.close();
        }
    }

    @Test
    @DisplayName("服务初始化测试")
    void testServiceInit() {
        // 验证服务未初始化
        assertFalse(onlineStatusService.isInitialized(), "服务初始状态应该是未初始化");

        // 初始化服务
        onlineStatusService.init();

        // 验证服务已初始化
        assertTrue(onlineStatusService.isInitialized(), "服务应该已初始化");
        assertTrue(onlineStatusService.isHeartbeatMonitorRunning(), "心跳检测器应该正在运行");

        // 关闭服务
        onlineStatusService.shutdown();

        // 验证服务已关闭
        assertFalse(onlineStatusService.isInitialized(), "服务关闭后应该是未初始化状态");
        assertFalse(onlineStatusService.isHeartbeatMonitorRunning(), "心跳检测器应该已停止");
    }

    @Test
    @DisplayName("服务初始化前操作测试")
    void testOperationBeforeInit() {
        // 服务未初始化时尝试操作
        assertThrows(IllegalStateException.class, () -> {
            onlineStatusService.registerUser(TEST_USER_ID, TEST_GATEWAY_ID, TEST_CHANNEL_ID);
        }, "服务未初始化时操作应该抛出异常");

        assertThrows(IllegalStateException.class, () -> {
            onlineStatusService.isOnline(TEST_USER_ID);
        }, "服务未初始化时查询应该抛出异常");
    }

    @Test
    @DisplayName("完整用户生命周期测试")
    void testUserLifecycle() {
        // 初始化服务
        onlineStatusService.init();

        // 1. 用户上线
        onlineStatusService.registerUser(TEST_USER_ID, TEST_GATEWAY_ID, TEST_CHANNEL_ID);
        assertTrue(onlineStatusService.isOnline(TEST_USER_ID), "用户应该在线");

        // 2. 查询网关
        String gatewayId = onlineStatusService.getOnlineGateway(TEST_USER_ID);
        assertEquals(TEST_GATEWAY_ID, gatewayId, "网关ID应该匹配");

        // 3. 查询Channel
        Set<String> channels = onlineStatusService.getUserChannels(TEST_USER_ID);
        assertEquals(1, channels.size(), "应该有1个Channel");

        // 4. 更新心跳
        onlineStatusService.heartbeat(TEST_USER_ID, TEST_CHANNEL_ID);
        long heartbeatTime = onlineStatusService.getHeartbeatTimestamp(TEST_USER_ID, TEST_CHANNEL_ID);
        assertTrue(heartbeatTime > 0, "心跳时间戳应该大于0");

        // 5. 用户下线
        onlineStatusService.unregisterUser(TEST_USER_ID, TEST_CHANNEL_ID);
        assertFalse(onlineStatusService.isOnline(TEST_USER_ID), "用户应该已下线");
    }

    @Test
    @DisplayName("批量查询在线状态测试")
    void testBatchCheckOnline() {
        // 初始化服务
        onlineStatusService.init();

        // 准备测试数据
        Long userId1 = 4001L;
        Long userId2 = 4002L;
        Long userId3 = 4003L;

        try {
            // 只有userId2上线
            onlineStatusService.registerUser(userId2, TEST_GATEWAY_ID, "channel-4002");

            // 批量查询
            List<Long> userIds = List.of(userId1, userId2, userId3);
            Map<Long, Boolean> results = onlineStatusService.batchCheckOnline(userIds);

            // 验证结果
            assertNotNull(results, "结果不应为null");
            assertEquals(3, results.size(), "应该返回3个用户的状态");
            assertFalse(results.get(userId1), "userId1应该不在线");
            assertTrue(results.get(userId2), "userId2应该在线");
            assertFalse(results.get(userId3), "userId3应该不在线");
        } finally {
            // 清理测试数据
            onlineStatusService.clearUserData(userId1);
            onlineStatusService.clearUserData(userId2);
            onlineStatusService.clearUserData(userId3);
        }
    }

    @Test
    @DisplayName("Redis统计信息测试")
    void testRedisStats() {
        // 初始化服务
        onlineStatusService.init();

        // 获取Redis统计信息
        String stats = onlineStatusService.getRedisStats();
        assertNotNull(stats, "统计信息不应为null");
        assertTrue(stats.contains("healthy"), "统计信息应该包含健康状态");
        assertTrue(stats.contains("retryCount"), "统计信息应该包含重试次数");
        assertTrue(stats.contains("failureCount"), "统计信息应该包含失败次数");
    }

    @Test
    @DisplayName("心跳扫描间隔测试")
    void testHeartbeatScanInterval() {
        // 验证扫描间隔
        assertEquals(30, onlineStatusService.getHeartbeatScanInterval(), "心跳扫描间隔应该是30秒");

        // 验证toString方法
        String toString = onlineStatusService.toString();
        assertTrue(toString.contains("heartbeatScanInterval=30"), "toString应该包含扫描间隔信息");
    }

    @Test
    @DisplayName("多端连接场景测试")
    void testMultiDeviceScenario() {
        // 初始化服务
        onlineStatusService.init();

        String channel1 = "channel-mobile";
        String channel2 = "channel-web";

        // 用户从移动端上线
        onlineStatusService.registerUser(TEST_USER_ID, TEST_GATEWAY_ID, channel1);
        assertTrue(onlineStatusService.isOnline(TEST_USER_ID), "用户应该在线");

        Set<String> channels = onlineStatusService.getUserChannels(TEST_USER_ID);
        assertEquals(1, channels.size(), "应该有1个Channel");

        // 用户从Web端上线
        onlineStatusService.registerUser(TEST_USER_ID, TEST_GATEWAY_ID, channel2);
        channels = onlineStatusService.getUserChannels(TEST_USER_ID);
        assertEquals(2, channels.size(), "应该有2个Channel");

        // 移动端下线
        onlineStatusService.unregisterUser(TEST_USER_ID, channel1);
        channels = onlineStatusService.getUserChannels(TEST_USER_ID);
        assertEquals(1, channels.size(), "应该还剩1个Channel");
        assertTrue(onlineStatusService.isOnline(TEST_USER_ID), "用户应该仍然在线");

        // Web端下线
        onlineStatusService.unregisterUser(TEST_USER_ID, channel2);
        assertFalse(onlineStatusService.isOnline(TEST_USER_ID), "用户应该已下线");
    }

    @Test
    @DisplayName("心跳检测器手动扫描测试")
    void testManualHeartbeatScan() {
        // 初始化服务
        onlineStatusService.init();

        // 用户上线
        onlineStatusService.registerUser(TEST_USER_ID, TEST_GATEWAY_ID, TEST_CHANNEL_ID);

        // 手动触发扫描
        onlineStatusService.scanExpiredUsers();

        // 验证用户仍然在线（因为心跳未超时）
        assertTrue(onlineStatusService.isOnline(TEST_USER_ID), "用户应该仍然在线");
    }

    @Test
    @DisplayName("服务重复初始化测试")
    void testDuplicateInit() {
        // 第一次初始化
        onlineStatusService.init();
        assertTrue(onlineStatusService.isInitialized(), "服务应该已初始化");

        // 第二次初始化（应该被忽略）
        onlineStatusService.init();
        assertTrue(onlineStatusService.isInitialized(), "服务应该仍然已初始化");

        // 验证只有一个心跳检测器在运行
        assertTrue(onlineStatusService.isHeartbeatMonitorRunning(), "心跳检测器应该正在运行");
    }

    @Test
    @DisplayName("服务重复关闭测试")
    void testDuplicateShutdown() {
        // 初始化服务
        onlineStatusService.init();
        assertTrue(onlineStatusService.isInitialized(), "服务应该已初始化");

        // 第一次关闭
        onlineStatusService.shutdown();
        assertFalse(onlineStatusService.isInitialized(), "服务应该已关闭");

        // 第二次关闭（应该被忽略）
        onlineStatusService.shutdown();
        assertFalse(onlineStatusService.isInitialized(), "服务应该仍然已关闭");
    }

    @Test
    @DisplayName("清理过期连接测试")
    void testCleanupExpiredConnections() {
        // 初始化服务
        onlineStatusService.init();

        // 用户上线两个设备
        String channel1 = "channel-1";
        String channel2 = "channel-2";
        onlineStatusService.registerUser(TEST_USER_ID, TEST_GATEWAY_ID, channel1);
        onlineStatusService.registerUser(TEST_USER_ID, TEST_GATEWAY_ID, channel2);

        Set<String> channels = onlineStatusService.getUserChannels(TEST_USER_ID);
        assertEquals(2, channels.size(), "应该有2个Channel");

        // 清理过期连接（由于心跳未超时，应该不会清理）
        int cleanedCount = onlineStatusService.cleanupExpiredConnections(TEST_USER_ID);
        assertEquals(0, cleanedCount, "不应该有被清理的连接");

        // 验证连接仍然存在
        channels = onlineStatusService.getUserChannels(TEST_USER_ID);
        assertEquals(2, channels.size(), "应该仍然有2个Channel");
    }
}
