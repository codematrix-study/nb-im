package com.cw.im.redis;

import com.cw.im.common.constants.IMConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 在线用户管理器测试
 *
 * <p>测试在线用户的注册、下线、心跳和状态查询功能</p>
 *
 * @author cw
 * @since 1.0.0
 */
@DisplayName("在线用户管理器测试")
class OnlineUserManagerTest {

    private RedisManager redisManager;
    private OnlineUserManager onlineUserManager;

    private static final Long TEST_USER_ID = 1001L;
    private static final String TEST_GATEWAY_ID = "gateway-001";
    private static final String TEST_CHANNEL_ID = "channel-001";
    private static final String TEST_CHANNEL_ID_2 = "channel-002";

    @BeforeEach
    void setUp() {
        // 初始化Redis连接
        redisManager = new RedisManager("192.168.215.3", 6379, null, 0);
        onlineUserManager = new OnlineUserManager(redisManager);

        // 测试Redis连接
        String pong = redisManager.ping();
        assertEquals("PONG", pong, "Redis连接测试失败");
    }

    @AfterEach
    void tearDown() {
        // 清理测试数据
        try {
            onlineUserManager.clearUserData(TEST_USER_ID);
        } catch (Exception e) {
            // 忽略清理错误
        }

        // 关闭连接
        if (redisManager != null) {
            redisManager.close();
        }
    }

    @Test
    @DisplayName("用户上线测试")
    void testRegisterUser() {
        // 执行用户上线
        onlineUserManager.registerUser(TEST_USER_ID, TEST_GATEWAY_ID, TEST_CHANNEL_ID);

        // 验证用户在线
        assertTrue(onlineUserManager.isOnline(TEST_USER_ID), "用户应该在线");

        // 验证网关ID
        String gatewayId = onlineUserManager.getOnlineGateway(TEST_USER_ID);
        assertEquals(TEST_GATEWAY_ID, gatewayId, "网关ID不匹配");

        // 验证Channel列表
        Set<String> channels = onlineUserManager.getUserChannels(TEST_USER_ID);
        assertNotNull(channels, "Channel列表不应为null");
        assertEquals(1, channels.size(), "Channel数量应该是1");
        assertTrue(channels.contains(TEST_CHANNEL_ID), "应该包含测试Channel");
    }

    @Test
    @DisplayName("用户下线测试")
    void testUnregisterUser() {
        // 先让用户上线
        onlineUserManager.registerUser(TEST_USER_ID, TEST_GATEWAY_ID, TEST_CHANNEL_ID);
        assertTrue(onlineUserManager.isOnline(TEST_USER_ID), "用户应该在线");

        // 执行用户下线
        onlineUserManager.unregisterUser(TEST_USER_ID, TEST_CHANNEL_ID);

        // 验证用户已下线
        assertFalse(onlineUserManager.isOnline(TEST_USER_ID), "用户应该已下线");

        // 验证Channel已清空
        Set<String> channels = onlineUserManager.getUserChannels(TEST_USER_ID);
        assertNotNull(channels, "Channel列表不应为null");
        assertTrue(channels.isEmpty(), "Channel列表应该为空");
    }

    @Test
    @DisplayName("多端连接测试")
    void testMultiDeviceConnection() {
        // 用户从第一个设备上线
        onlineUserManager.registerUser(TEST_USER_ID, TEST_GATEWAY_ID, TEST_CHANNEL_ID);
        Set<String> channels = onlineUserManager.getUserChannels(TEST_USER_ID);
        assertEquals(1, channels.size(), "应该有1个Channel");

        // 用户从第二个设备上线
        onlineUserManager.registerUser(TEST_USER_ID, TEST_GATEWAY_ID, TEST_CHANNEL_ID_2);
        channels = onlineUserManager.getUserChannels(TEST_USER_ID);
        assertEquals(2, channels.size(), "应该有2个Channel");
        assertTrue(channels.contains(TEST_CHANNEL_ID), "应该包含第一个Channel");
        assertTrue(channels.contains(TEST_CHANNEL_ID_2), "应该包含第二个Channel");

        // 下线第一个设备
        onlineUserManager.unregisterUser(TEST_USER_ID, TEST_CHANNEL_ID);
        channels = onlineUserManager.getUserChannels(TEST_USER_ID);
        assertEquals(1, channels.size(), "应该还剩1个Channel");
        assertTrue(channels.contains(TEST_CHANNEL_ID_2), "应该还剩第二个Channel");

        // 验证用户仍然在线
        assertTrue(onlineUserManager.isOnline(TEST_USER_ID), "用户应该仍然在线");

        // 下线第二个设备
        onlineUserManager.unregisterUser(TEST_USER_ID, TEST_CHANNEL_ID_2);
        assertFalse(onlineUserManager.isOnline(TEST_USER_ID), "用户应该已下线");
    }

    @Test
    @DisplayName("心跳更新测试")
    void testHeartbeat() {
        // 用户上线
        onlineUserManager.registerUser(TEST_USER_ID, TEST_GATEWAY_ID, TEST_CHANNEL_ID);

        // 获取初始心跳时间
        long heartbeat1 = onlineUserManager.getHeartbeatTimestamp(TEST_USER_ID, TEST_CHANNEL_ID);
        assertTrue(heartbeat1 > 0, "心跳时间戳应该大于0");

        // 等待一小段时间
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            fail("测试被中断");
        }

        // 更新心跳
        onlineUserManager.heartbeat(TEST_USER_ID, TEST_CHANNEL_ID);

        // 获取更新后的心跳时间
        long heartbeat2 = onlineUserManager.getHeartbeatTimestamp(TEST_USER_ID, TEST_CHANNEL_ID);

        // 验证心跳时间已更新
        assertTrue(heartbeat2 > heartbeat1, "心跳时间应该已更新");
    }

    @Test
    @DisplayName("在线状态查询测试")
    void testIsOnline() {
        // 用户未上线时
        assertFalse(onlineUserManager.isOnline(TEST_USER_ID), "用户初始状态应该是不在线");

        // 用户上线后
        onlineUserManager.registerUser(TEST_USER_ID, TEST_GATEWAY_ID, TEST_CHANNEL_ID);
        assertTrue(onlineUserManager.isOnline(TEST_USER_ID), "用户上线后应该是在线状态");

        // 用户下线后
        onlineUserManager.unregisterUser(TEST_USER_ID, TEST_CHANNEL_ID);
        assertFalse(onlineUserManager.isOnline(TEST_USER_ID), "用户下线后应该是离线状态");
    }

    @Test
    @DisplayName("批量查询在线状态测试")
    void testBatchCheckOnline() {
        // 准备测试数据
        Long userId1 = 2001L;
        Long userId2 = 2002L;
        Long userId3 = 2003L;

        try {
            // 只有userId2上线
            onlineUserManager.registerUser(userId2, TEST_GATEWAY_ID, "channel-2002");

            // 批量查询
            List<Long> userIds = List.of(userId1, userId2, userId3);
            Map<Long, Boolean> results = onlineUserManager.batchCheckOnline(userIds);

            // 验证结果
            assertNotNull(results, "结果不应为null");
            assertEquals(3, results.size(), "应该返回3个用户的状态");
            assertFalse(results.get(userId1), "userId1应该不在线");
            assertTrue(results.get(userId2), "userId2应该在线");
            assertFalse(results.get(userId3), "userId3应该不在线");
        } finally {
            // 清理测试数据
            onlineUserManager.clearUserData(userId1);
            onlineUserManager.clearUserData(userId2);
            onlineUserManager.clearUserData(userId3);
        }
    }

    @Test
    @DisplayName("获取用户网关测试")
    void testGetOnlineGateway() {
        // 用户未上线时
        String gatewayId = onlineUserManager.getOnlineGateway(TEST_USER_ID);
        assertNull(gatewayId, "用户未上线时网关ID应该为null");

        // 用户上线后
        onlineUserManager.registerUser(TEST_USER_ID, TEST_GATEWAY_ID, TEST_CHANNEL_ID);
        gatewayId = onlineUserManager.getOnlineGateway(TEST_USER_ID);
        assertEquals(TEST_GATEWAY_ID, gatewayId, "网关ID应该匹配");

        // 用户下线后
        onlineUserManager.unregisterUser(TEST_USER_ID, TEST_CHANNEL_ID);
        gatewayId = onlineUserManager.getOnlineGateway(TEST_USER_ID);
        assertNull(gatewayId, "用户下线后网关ID应该为null");
    }

    @Test
    @DisplayName("清理用户数据测试")
    void testClearUserData() {
        // 用户上线两个设备
        onlineUserManager.registerUser(TEST_USER_ID, TEST_GATEWAY_ID, TEST_CHANNEL_ID);
        onlineUserManager.registerUser(TEST_USER_ID, TEST_GATEWAY_ID, TEST_CHANNEL_ID_2);
        assertTrue(onlineUserManager.isOnline(TEST_USER_ID), "用户应该在线");

        // 清理用户数据
        onlineUserManager.clearUserData(TEST_USER_ID);

        // 验证所有数据已清理
        assertFalse(onlineUserManager.isOnline(TEST_USER_ID), "用户应该已下线");
        Set<String> channels = onlineUserManager.getUserChannels(TEST_USER_ID);
        assertTrue(channels.isEmpty(), "Channel列表应该为空");

        // 验证心跳也已清理
        long heartbeat = onlineUserManager.getHeartbeatTimestamp(TEST_USER_ID, TEST_CHANNEL_ID);
        assertEquals(-1, heartbeat, "心跳应该已被清理");
    }

    @Test
    @DisplayName("参数校验测试")
    void testParameterValidation() {
        // 测试null参数
        assertThrows(IllegalArgumentException.class, () -> {
            onlineUserManager.registerUser(null, TEST_GATEWAY_ID, TEST_CHANNEL_ID);
        }, "userId为null时应该抛出异常");

        assertThrows(IllegalArgumentException.class, () -> {
            onlineUserManager.registerUser(TEST_USER_ID, null, TEST_CHANNEL_ID);
        }, "gatewayId为null时应该抛出异常");

        assertThrows(IllegalArgumentException.class, () -> {
            onlineUserManager.registerUser(TEST_USER_ID, TEST_GATEWAY_ID, null);
        }, "channelId为null时应该抛出异常");

        assertThrows(IllegalArgumentException.class, () -> {
            onlineUserManager.isOnline(null);
        }, "查询在线状态时userId为null应该抛出异常");
    }

    @Test
    @DisplayName("心跳超时检测测试")
    void testHeartbeatTimeout() {
        // 用户上线
        onlineUserManager.registerUser(TEST_USER_ID, TEST_GATEWAY_ID, TEST_CHANNEL_ID);

        // 获取当前心跳时间
        long heartbeatTime = onlineUserManager.getHeartbeatTimestamp(TEST_USER_ID, TEST_CHANNEL_ID);
        long currentTime = IMConstants.getCurrentTimestamp();

        // 验证心跳未超时
        assertFalse(
            IMConstants.isHeartbeatTimeout(heartbeatTime, currentTime),
            "刚上线的心跳不应该超时"
        );

        // 模拟超时时间戳（当前时间 - 超时时间 - 1秒）
        long expiredHeartbeatTime = currentTime - IMConstants.HEARTBEAT_TIMEOUT_SECONDS * 1000 - 1000;

        // 验证心跳已超时
        assertTrue(
            IMConstants.isHeartbeatTimeout(expiredHeartbeatTime, currentTime),
            "超时的心跳应该被检测到"
        );
    }
}
