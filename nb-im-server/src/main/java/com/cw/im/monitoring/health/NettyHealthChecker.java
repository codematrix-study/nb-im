package com.cw.im.monitoring.health;

import com.cw.im.server.channel.ChannelManager;
import lombok.extern.slf4j.Slf4j;

/**
 * Netty健康检查器
 *
 * <p>检查Netty服务器连接状态</p>
 *
 * @author cw
 * @since 1.0.0
 */
@Slf4j
public class NettyHealthChecker implements HealthChecker {

    private final ChannelManager channelManager;

    /**
     * 构造函数
     *
     * @param channelManager Channel管理器
     */
    public NettyHealthChecker(ChannelManager channelManager) {
        this.channelManager = channelManager;
    }

    @Override
    public HealthCheck check() {
        if (channelManager == null) {
            return HealthCheck.down(getName(), "ChannelManager未初始化");
        }

        try {
            int connectionCount = channelManager.getConnectionCount();
            int onlineUserCount = channelManager.getOnlineUserCount();

            // 检查连接数是否正常
            if (connectionCount < 0) {
                return HealthCheck.down(getName(), "连接数异常: " + connectionCount);
            }

            return HealthCheck.builder()
                    .name(getName())
                    .status(HealthCheck.HealthStatus.UP)
                    .description("Netty服务正常")
                    .details("连接数: " + connectionCount + ", 在线用户: " + onlineUserCount)
                    .build();

        } catch (Exception e) {
            log.error("Netty健康检查失败", e);
            return HealthCheck.builder()
                    .name(getName())
                    .status(HealthCheck.HealthStatus.DOWN)
                    .description("Netty服务异常")
                    .exception(e)
                    .build();
        }
    }

    @Override
    public String getName() {
        return "netty";
    }
}
