package com.cw.im.redis;

/**
 * 在线状态服务使用示例
 *
 * <p>演示如何使用OnlineStatusService管理用户在线状态</p>
 *
 * @author cw
 * @since 1.0.0
 */
public class OnlineStatusServiceExample {

    public static void main(String[] args) {
        // 1. 创建Redis管理器
        RedisManager redisManager = new RedisManager("192.168.215.3", 6379, null, 0);

        // 2. 创建在线状态服务（心跳扫描间隔30秒）
        OnlineStatusService onlineStatusService = new OnlineStatusService(redisManager, 30);

        try {
            // 3. 初始化服务
            onlineStatusService.init();
            System.out.println("在线状态服务已启动");

            // 4. 用户上线场景
            userOnlineScenario(onlineStatusService);

            // 5. 多端登录场景
            multiDeviceScenario(onlineStatusService);

            // 6. 批量查询场景
            batchQueryScenario(onlineStatusService);

            // 7. 查看Redis统计信息
            System.out.println("\n=== Redis统计信息 ===");
            System.out.println(onlineStatusService.getRedisStats());

            // 8. 等待一段时间观察心跳检测
            System.out.println("\n等待心跳检测运行...");
            Thread.sleep(35000);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // 9. 关闭服务
            onlineStatusService.shutdown();
            System.out.println("\n在线状态服务已关闭");
        }
    }

    /**
     * 用户上线场景示例
     */
    private static void userOnlineScenario(OnlineStatusService service) {
        System.out.println("\n=== 用户上线场景 ===");

        Long userId = 1001L;
        String gatewayId = "gateway-001";
        String channelId = "channel-001";

        // 用户上线
        service.registerUser(userId, gatewayId, channelId);
        System.out.println("用户 " + userId + " 已上线");

        // 检查在线状态
        boolean isOnline = service.isOnline(userId);
        System.out.println("用户在线状态: " + isOnline);

        // 查询所在网关
        String onlineGateway = service.getOnlineGateway(userId);
        System.out.println("用户所在网关: " + onlineGateway);

        // 查询Channel列表
        var channels = service.getUserChannels(userId);
        System.out.println("用户Channel数量: " + channels.size());

        // 更新心跳
        service.heartbeat(userId, channelId);
        System.out.println("用户心跳已更新");

        // 用户下线
        service.unregisterUser(userId, channelId);
        System.out.println("用户 " + userId + " 已下线");

        // 再次检查在线状态
        isOnline = service.isOnline(userId);
        System.out.println("用户在线状态: " + isOnline);
    }

    /**
     * 多端登录场景示例
     */
    private static void multiDeviceScenario(OnlineStatusService service) {
        System.out.println("\n=== 多端登录场景 ===");

        Long userId = 1002L;
        String gatewayId = "gateway-002";
        String mobileChannel = "channel-mobile-002";
        String webChannel = "channel-web-002";

        // 移动端上线
        service.registerUser(userId, gatewayId, mobileChannel);
        System.out.println("用户 " + userId + " 移动端已上线");

        var channels = service.getUserChannels(userId);
        System.out.println("当前Channel数量: " + channels.size());

        // Web端上线
        service.registerUser(userId, gatewayId, webChannel);
        System.out.println("用户 " + userId + " Web端已上线");

        channels = service.getUserChannels(userId);
        System.out.println("当前Channel数量: " + channels.size());

        // 移动端下线
        service.unregisterUser(userId, mobileChannel);
        System.out.println("用户 " + userId + " 移动端已下线");

        // 检查用户是否仍然在线
        boolean isOnline = service.isOnline(userId);
        System.out.println("用户仍然在线: " + isOnline);

        channels = service.getUserChannels(userId);
        System.out.println("剩余Channel数量: " + channels.size());

        // Web端下线
        service.unregisterUser(userId, webChannel);
        System.out.println("用户 " + userId + " Web端已下线");

        // 检查用户是否完全下线
        isOnline = service.isOnline(userId);
        System.out.println("用户在线状态: " + isOnline);
    }

    /**
     * 批量查询场景示例
     */
    private static void batchQueryScenario(OnlineStatusService service) {
        System.out.println("\n=== 批量查询场景 ===");

        // 让部分用户上线
        service.registerUser(2001L, "gateway-003", "channel-2001");
        service.registerUser(2003L, "gateway-003", "channel-2003");

        System.out.println("用户 2001 和 2003 已上线");

        // 批量查询用户在线状态
        var userIds = java.util.List.of(2001L, 2002L, 2003L, 2004L);
        var onlineStatusMap = service.batchCheckOnline(userIds);

        System.out.println("批量查询结果:");
        onlineStatusMap.forEach((userId, online) ->
            System.out.println("  用户 " + userId + ": " + (online ? "在线" : "离线"))
        );

        // 清理测试数据
        service.clearUserData(2001L);
        service.clearUserData(2002L);
        service.clearUserData(2003L);
        service.clearUserData(2004L);
    }
}
