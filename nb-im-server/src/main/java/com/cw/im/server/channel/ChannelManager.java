package com.cw.im.server.channel;

import com.cw.im.common.protocol.IMMessage;
import com.cw.im.redis.OnlineStatusService;
import com.cw.im.server.attributes.ChannelAttributes;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Channel 管理器
 *
 * <p>负责管理所有 Netty Channel 的生命周期，提供 Channel 的增删改查功能</p>
 *
 * <h3>核心功能</h3>
 * <ul>
 *     <li>Channel 的添加和移除</li>
 *     <li>用户多端登录支持（一个用户多个Channel）</li>
 *     <li>按设备类型查询 Channel</li>
 *     <li>广播消息到用户所有设备</li>
 *     <li>连接数统计</li>
 * </ul>
 *
 * <h3>数据结构</h3>
 * <ul>
 *     <li>userId -> List&lt;Channel&gt; : 用户的所有Channel</li>
 *     <li>channel -> userId : Channel到用户的反向映射</li>
 * </ul>
 *
 * @author cw
 * @since 1.0.0
 */
@Slf4j
public class ChannelManager {

    /**
     * 用户ID -> Channel列表（支持多端登录）
     */
    private final Map<Long, Set<Channel>> userChannels = new ConcurrentHashMap<>();

    /**
     * Channel -> 用户ID（反向查询）
     */
    private final Map<Channel, Long> channelUserMap = new ConcurrentHashMap<>();

    /**
     * Channel -> 设备ID
     */
    private final Map<Channel, String> channelDeviceMap = new ConcurrentHashMap<>();

    /**
     * 当前连接数
     */
    private final AtomicInteger connectionCount = new AtomicInteger(0);

    /**
     * 峰值连接数
     */
    private final AtomicInteger peakConnections = new AtomicInteger(0);

    /**
     * 在线状态服务
     */
    private final OnlineStatusService onlineStatusService;

    /**
     * 网关ID
     */
    private final String gatewayId;

    /**
     * 构造函数
     *
     * @param onlineStatusService 在线状态服务
     * @param gatewayId           网关ID
     */
    public ChannelManager(OnlineStatusService onlineStatusService, String gatewayId) {
        this.onlineStatusService = onlineStatusService;
        this.gatewayId = gatewayId;
        log.info("ChannelManager 初始化完成, gatewayId={}", gatewayId);
    }

    /**
     * 构造函数（自动生成网关ID）
     *
     * @param onlineStatusService 在线状态服务
     */
    public ChannelManager(OnlineStatusService onlineStatusService) {
        this(onlineStatusService, generateGatewayId());
    }

    /**
     * 生成网关ID
     *
     * @return 网关ID（格式：gateway-{hostname}-{timestamp}）
     */
    private static String generateGatewayId() {
        String hostname = System.getenv().getOrDefault("HOSTNAME", "localhost");
        long timestamp = System.currentTimeMillis();
        return "gateway-" + hostname + "-" + timestamp;
    }

    // ==================== Channel 管理 ====================

    /**
     * 添加 Channel
     *
     * @param userId    用户ID
     * @param deviceId  设备ID
     * @param channel   Channel对象
     */
    public void addChannel(Long userId, String deviceId, Channel channel) {
        if (userId == null || deviceId == null || channel == null) {
            throw new IllegalArgumentException("userId, deviceId 和 channel 不能为 null");
        }

        try {
            // 1. 存储 userId -> List<Channel>
            userChannels.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet())
                    .add(channel);

            // 2. 存储 channel -> userId（反向查询）
            channelUserMap.put(channel, userId);

            // 3. 存储 channel -> deviceId
            channelDeviceMap.put(channel, deviceId);

            // 4. 设置 Channel 属性
            ChannelAttributes.setUserId(channel, userId);
            ChannelAttributes.setDeviceId(channel, deviceId);
            ChannelAttributes.setGatewayId(channel, gatewayId);

            // 5. 更新连接数
            int count = connectionCount.incrementAndGet();
            peakConnections.updateAndGet(max -> Math.max(max, count));

            // 6. 注册到在线状态服务（Redis）
            String channelId = channel.id().asLongText();
            onlineStatusService.registerUser(userId, gatewayId, channelId);

            log.info("Channel 添加成功: userId={}, deviceId={}, channelId={}, 当前连接数={}",
                    userId, deviceId, channelId, count);

        } catch (Exception e) {
            log.error("添加 Channel 失败: userId={}, deviceId={}", userId, deviceId, e);
            throw e;
        }
    }

    /**
     * 移除 Channel
     *
     * @param channel Channel对象
     */
    public void removeChannel(Channel channel) {
        if (channel == null) {
            return;
        }

        try {
            // 1. 获取用户ID和设备ID
            Long userId = channelUserMap.get(channel);
            String deviceId = channelDeviceMap.get(channel);
            String channelId = channel.id().asLongText();

            if (userId != null) {
                // 2. 从用户Channel列表中移除
                Set<Channel> channels = userChannels.get(userId);
                if (channels != null) {
                    channels.remove(channel);

                    // 如果用户没有其他Channel，移除用户条目
                    if (channels.isEmpty()) {
                        userChannels.remove(userId);
                    }
                }

                // 3. 从在线状态服务中注销（Redis）
                onlineStatusService.unregisterUser(userId, channelId);

                log.info("Channel 移除成功: userId={}, deviceId={}, channelId={}",
                        userId, deviceId, channelId);
            }

            // 4. 清理反向映射
            channelUserMap.remove(channel);
            channelDeviceMap.remove(channel);

            // 5. 清理 Channel 属性
            ChannelAttributes.clearAll(channel);

            // 6. 更新连接数
            int count = connectionCount.decrementAndGet();
            log.debug("当前连接数: {}", count);

        } catch (Exception e) {
            log.error("移除 Channel 失败: channelId={}", channel.id(), e);
        }
    }

    /**
     * 获取用户指定类型的 Channel
     *
     * @param userId     用户ID
     * @param deviceType 设备类型
     * @return Channel对象，如果不存在则返回null
     */
    public Channel getChannel(Long userId, String deviceType) {
        Set<Channel> channels = userChannels.get(userId);
        if (channels == null || channels.isEmpty()) {
            return null;
        }

        // 查找匹配设备类型的 Channel
        for (Channel channel : channels) {
            if (channel.isActive()) {
                String channelDeviceType = ChannelAttributes.getDeviceType(channel);
                if (deviceType == null || deviceType.equals(channelDeviceType)) {
                    return channel;
                }
            }
        }

        return null;
    }

    /**
     * 获取用户所有活跃的 Channel
     *
     * @param userId 用户ID
     * @return Channel列表
     */
    public List<Channel> getChannels(Long userId) {
        Set<Channel> channels = userChannels.get(userId);
        if (channels == null || channels.isEmpty()) {
            return List.of();
        }

        // 只返回活跃的 Channel
        return channels.stream()
                .filter(Channel::isActive)
                .collect(Collectors.toList());
    }

    /**
     * 获取用户所有 Channel（包括不活跃的）
     *
     * @param userId 用户ID
     * @return Channel列表
     */
    public List<Channel> getAllChannels(Long userId) {
        Set<Channel> channels = userChannels.get(userId);
        if (channels == null || channels.isEmpty()) {
            return List.of();
        }

        return List.copyOf(channels);
    }

    /**
     * 广播消息到用户所有设备
     *
     * @param userId  用户ID
     * @param message IM消息
     * @return 成功发送的设备数量
     */
    public int broadcastToUser(Long userId, IMMessage message) {
        List<Channel> channels = getChannels(userId);
        if (channels.isEmpty()) {
            log.warn("用户无活跃Channel: userId={}", userId);
            return 0;
        }

        int successCount = 0;
        for (Channel channel : channels) {
            if (channel.isActive() && channel.isWritable()) {
                try {
                    channel.writeAndFlush(message).addListener(future -> {
                        if (!future.isSuccess()) {
                            log.error("发送消息失败: userId={}, channelId={}",
                                    userId, channel.id(), future.cause());
                        }
                    });
                    successCount++;
                } catch (Exception e) {
                    log.error("发送消息异常: userId={}, channelId={}",
                            userId, channel.id(), e);
                }
            }
        }

        log.debug("广播消息完成: userId={}, 设备数={}, 成功数={}",
                userId, channels.size(), successCount);

        return successCount;
    }

    /**
     * 发送消息到指定 Channel
     *
     * @param channel Channel对象
     * @param message IM消息
     * @return true-发送成功, false-发送失败
     */
    public boolean sendToChannel(Channel channel, IMMessage message) {
        if (channel == null || !channel.isActive() || !channel.isWritable()) {
            log.warn("Channel 不可用: channelId={}, active={}, writable={}",
                    channel != null ? channel.id() : null,
                    channel != null && channel.isActive(),
                    channel != null && channel.isWritable());
            return false;
        }

        try {
            channel.writeAndFlush(message).addListener(future -> {
                if (!future.isSuccess()) {
                    log.error("发送消息失败: channelId={}", channel.id(), future.cause());
                }
            });
            return true;
        } catch (Exception e) {
            log.error("发送消息异常: channelId={}", channel.id(), e);
            return false;
        }
    }

    /**
     * 获取所有在线用户ID
     *
     * @return 在线用户ID集合
     */
    public Set<Long> getAllOnlineUsers() {
        return Set.copyOf(userChannels.keySet());
    }

    // ==================== 统计信息 ====================

    /**
     * 获取当前连接数
     *
     * @return 连接数
     */
    public int getConnectionCount() {
        return connectionCount.get();
    }

    /**
     * 获取峰值连接数
     *
     * @return 峰值连接数
     */
    public int getPeakConnections() {
        return peakConnections.get();
    }

    /**
     * 获取在线用户数
     *
     * @return 在线用户数
     */
    public int getOnlineUserCount() {
        return userChannels.size();
    }

    /**
     * 获取网关ID
     *
     * @return 网关ID
     */
    public String getGatewayId() {
        return gatewayId;
    }

    /**
     * 检查用户是否在线
     *
     * @param userId 用户ID
     * @return true-在线, false-离线
     */
    public boolean isUserOnline(Long userId) {
        Set<Channel> channels = userChannels.get(userId);
        return channels != null && !channels.isEmpty()
                && channels.stream().anyMatch(Channel::isActive);
    }

    /**
     * 获取用户在线设备数
     *
     * @param userId 用户ID
     * @return 在线设备数
     */
    public int getUserDeviceCount(Long userId) {
        List<Channel> channels = getChannels(userId);
        return channels.size();
    }

    /**
     * 清理不活跃的 Channel
     *
     * @return 清理的 Channel 数量
     */
    public int cleanupInactiveChannels() {
        int cleaned = 0;

        for (Map.Entry<Long, Set<Channel>> entry : userChannels.entrySet()) {
            Set<Channel> channels = entry.getValue();
            channels.removeIf(channel -> {
                if (!channel.isActive()) {
                    channelUserMap.remove(channel);
                    channelDeviceMap.remove(channel);
                    connectionCount.decrementAndGet();
                    log.info("清理不活跃Channel: userId={}, channelId={}",
                            entry.getKey(), channel.id());
                    return true;
                }
                return false;
            });

            if (channels.isEmpty()) {
                userChannels.remove(entry.getKey());
            }
        }

        log.info("清理不活跃Channel完成: 清理数量={}", cleaned);
        return cleaned;
    }

    /**
     * 获取统计信息
     *
     * @return 统计信息JSON字符串
     */
    public String getStats() {
        return String.format(
                "ChannelManager{gatewayId='%s', connections=%d, peakConnections=%d, onlineUsers=%d}",
                gatewayId, connectionCount.get(), peakConnections.get(), userChannels.size()
        );
    }

    /**
     * 关闭管理器
     */
    public void shutdown() {
        log.info("正在关闭 ChannelManager...");

        // 关闭所有 Channel
        int closed = 0;
        for (Set<Channel> channels : userChannels.values()) {
            for (Channel channel : channels) {
                if (channel.isActive()) {
                    channel.close();
                    closed++;
                }
            }
        }

        // 清空所有映射
        userChannels.clear();
        channelUserMap.clear();
        channelDeviceMap.clear();
        connectionCount.set(0);

        log.info("ChannelManager 已关闭: 关闭Channel数量={}", closed);
    }
}
