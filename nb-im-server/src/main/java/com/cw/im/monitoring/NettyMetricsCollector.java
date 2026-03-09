package com.cw.im.monitoring;

import com.cw.im.server.channel.ChannelManager;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Netty指标采集器
 *
 * <p>负责采集Netty连接和通道相关的性能指标</p>
 *
 * <h3>采集指标</h3>
 * <ul>
 *     <li>连接指标：当前连接数、峰值连接数、新建连接速率</li>
 *     <li>通道指标：活跃通道数、可写通道数</li>
 *     <li>用户指标：在线用户数、多端登录用户数</li>
 *     <li>网关指标：网关ID、网关状态</li>
 * </ul>
 *
 * @author cw
 * @since 1.0.0
 */
@Slf4j
public class NettyMetricsCollector extends AbstractMetricsCollector {

    private final ChannelManager channelManager;

    /**
     * 上次采集的连接数（用于计算连接速率）
     */
    private long lastConnectionCount = 0;

    /**
     * 上次采集时间
     */
    private long lastCollectTime = System.currentTimeMillis();

    /**
     * 构造函数
     *
     * @param channelManager Channel管理器
     */
    public NettyMetricsCollector(ChannelManager channelManager) {
        super("netty", "Netty连接和通道指标采集器");
        this.channelManager = channelManager;
    }

    @Override
    public Map<String, Object> collectMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        if (channelManager == null) {
            log.warn("ChannelManager未初始化，跳过Netty指标采集");
            return metrics;
        }

        try {
            // 1. 连接指标
            metrics.putAll(collectConnectionMetrics());

            // 2. 通道指标
            metrics.putAll(collectChannelMetrics());

            // 3. 用户指标
            metrics.putAll(collectUserMetrics());

            // 4. 网关指标
            metrics.putAll(collectGatewayMetrics());

            // 更新上次采集数据
            lastConnectionCount = channelManager.getConnectionCount();
            lastCollectTime = System.currentTimeMillis();

        } catch (Exception e) {
            log.error("采集Netty指标异常", e);
        }

        return metrics;
    }

    /**
     * 采集连接指标
     */
    private Map<String, Object> collectConnectionMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        int currentConnections = channelManager.getConnectionCount();
        int peakConnections = channelManager.getPeakConnections();

        metrics.put("netty_connections_current", currentConnections);
        metrics.put("netty_connections_peak", peakConnections);

        // 计算连接速率（连接数/秒）
        long timeDiff = System.currentTimeMillis() - lastCollectTime;
        if (timeDiff > 0) {
            double connectionRate = (currentConnections - lastConnectionCount) * 1000.0 / timeDiff;
            metrics.put("netty_connections_rate", connectionRate);
        }

        return metrics;
    }

    /**
     * 采集通道指标
     */
    private Map<String, Object> collectChannelMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        int activeChannels = 0;
        int writableChannels = 0;
        int inactiveChannels = 0;

        Set<Long> onlineUsers = channelManager.getAllOnlineUsers();
        for (Long userId : onlineUsers) {
            List<Channel> channels = channelManager.getChannels(userId);
            for (Channel channel : channels) {
                if (channel.isActive()) {
                    activeChannels++;
                    if (channel.isWritable()) {
                        writableChannels++;
                    }
                } else {
                    inactiveChannels++;
                }
            }
        }

        metrics.put("netty_channels_active", activeChannels);
        metrics.put("netty_channels_writable", writableChannels);
        metrics.put("netty_channels_inactive", inactiveChannels);

        return metrics;
    }

    /**
     * 采集用户指标
     */
    private Map<String, Object> collectUserMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        int onlineUsers = channelManager.getOnlineUserCount();

        metrics.put("netty_users_online", onlineUsers);

        // 统计多端登录用户数
        int multiDeviceUsers = 0;
        int totalDevices = 0;

        Set<Long> onlineUserSet = channelManager.getAllOnlineUsers();
        for (Long userId : onlineUserSet) {
            int deviceCount = channelManager.getUserDeviceCount(userId);
            if (deviceCount > 1) {
                multiDeviceUsers++;
            }
            totalDevices += deviceCount;
        }

        metrics.put("netty_users_multi_device", multiDeviceUsers);
        metrics.put("netty_devices_total", totalDevices);

        return metrics;
    }

    /**
     * 采集网关指标
     */
    private Map<String, Object> collectGatewayMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        String gatewayId = channelManager.getGatewayId();
        metrics.put("netty_gateway_id", gatewayId);
        metrics.put("netty_gateway_status", "UP");

        return metrics;
    }
}
