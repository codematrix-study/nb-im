package com.cw.im.server.handler;

import com.cw.im.server.attributes.ChannelAttributes;
import com.cw.im.server.channel.ChannelManager;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 连接管理 Handler
 *
 * <p>负责管理客户端连接的生命周期，包括连接建立、断开和异常处理</p>
 *
 * <h3>核心功能</h3>
 * <ul>
 *     <li>连接建立：记录日志、更新统计、初始化Channel属性</li>
 *     <li>连接断开：清理Channel、更新Redis、记录日志</li>
 *     <li>异常处理：记录日志、关闭连接、清理资源</li>
 *     <li>连接统计：记录总连接数、当前连接数等</li>
 * </ul>
 *
 * <h3>使用注意</h3>
 * <p>此 Handler 需要标注 @Sharable，因为要在所有 Channel 中共享同一个实例</p>
 *
 * @author cw
 * @since 1.0.0
 */
@Slf4j
@ChannelHandler.Sharable
public class ConnectionHandler extends ChannelInboundHandlerAdapter {

    /**
     * 总连接数（累计）
     */
    private final AtomicLong totalConnections = new AtomicLong(0);

    /**
     * Channel 管理器
     */
    private final ChannelManager channelManager;

    /**
     * 构造函数
     *
     * @param channelManager Channel管理器
     */
    public ConnectionHandler(ChannelManager channelManager) {
        this.channelManager = channelManager;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        InetSocketAddress remoteAddress = (InetSocketAddress) channel.remoteAddress();

        try {
            // 1. 更新连接统计
            long total = totalConnections.incrementAndGet();
            int current = channelManager.getConnectionCount();

            // 2. 记录连接信息
            String clientIp = remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : "unknown";
            int clientPort = remoteAddress != null ? remoteAddress.getPort() : -1;
            String channelId = channel.id().asLongText();

            // 3. 设置 Channel 属性
            ChannelAttributes.setConnectTime(channel, System.currentTimeMillis());
            ChannelAttributes.setRemoteAddress(channel, channel.remoteAddress().toString());

            log.info("========================================");
            log.info("新连接建立");
            log.info("客户端地址: {}:{}", clientIp, clientPort);
            log.info("Channel ID: {}", channelId);
            log.info("总连接数: {}", total);
            log.info("当前连接数: {}", current);
            log.info("网关ID: {}", channelManager.getGatewayId());
            log.info("========================================");

        } catch (Exception e) {
            log.error("处理连接建立事件失败: remoteAddress={}", remoteAddress, e);
        }

        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        Long userId = ChannelAttributes.getUserId(channel);
        String deviceId = ChannelAttributes.getDeviceId(channel);
        String channelId = channel.id().asLongText();
        String remoteAddress = ChannelAttributes.getRemoteAddress(channel);

        try {
            // 1. 检查是否已认证
            boolean authenticated = ChannelAttributes.isAuthenticated(channel);

            // 2. 如果已认证，从 ChannelManager 中移除
            if (authenticated && userId != null) {
                channelManager.removeChannel(channel);
                log.info("已认证用户断开连接: userId={}, deviceId={}, channelId={}, remoteAddress={}",
                        userId, deviceId, channelId, remoteAddress);
            } else {
                log.info("未认证用户断开连接: channelId={}, remoteAddress={}",
                        channelId, remoteAddress);
            }

            // 3. 记录连接时长
            Long connectTime = ChannelAttributes.getConnectTime(channel);
            if (connectTime != null) {
                long duration = System.currentTimeMillis() - connectTime;
                log.debug("连接时长: {}ms ({}s)", duration, duration / 1000);
            }

            // 4. 输出当前连接数统计
            int current = channelManager.getConnectionCount();
            log.info("当前连接数: {}", current);

        } catch (Exception e) {
            log.error("处理连接断开事件失败: channelId={}", channelId, e);
        }

        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        Channel channel = ctx.channel();
        Long userId = ChannelAttributes.getUserId(channel);
        String channelId = channel.id().asLongText();
        String remoteAddress = channel.remoteAddress().toString();

        try {
            // 1. 记录异常信息
            log.error("========================================");
            log.error("连接发生异常");
            log.error("用户ID: {}", userId);
            log.error("Channel ID: {}", channelId);
            log.error("远程地址: {}", remoteAddress);
            log.error("异常类型: {}", cause.getClass().getSimpleName());
            log.error("异常消息: {}", cause.getMessage());
            log.error("========================================", cause);

            // 2. 关闭连接
            if (channel.isActive()) {
                ctx.close();
            }

        } catch (Exception e) {
            log.error("处理异常事件失败: channelId={}", channelId, e);
        }
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        Long userId = ChannelAttributes.getUserId(channel);

        // 检查 Channel 的可写状态
        if (channel.isWritable()) {
            log.debug("Channel 恢复可写: userId={}, writableBytes={}",
                    userId, channel.bytesBeforeUnwritable());
        } else {
            log.warn("Channel 不可写: userId={}, 可能是写入慢或客户端消费慢",
                    userId);
        }

        super.channelWritabilityChanged(ctx);
    }

    // ==================== 统计信息 ====================

    /**
     * 获取总连接数（累计）
     *
     * @return 总连接数
     */
    public long getTotalConnections() {
        return totalConnections.get();
    }

    /**
     * 获取当前连接数
     *
     * @return 当前连接数
     */
    public int getCurrentConnections() {
        return channelManager.getConnectionCount();
    }

    /**
     * 获取峰值连接数
     *
     * @return 峰值连接数
     */
    public int getPeakConnections() {
        return channelManager.getPeakConnections();
    }

    /**
     * 获取在线用户数
     *
     * @return 在线用户数
     */
    public int getOnlineUserCount() {
        return channelManager.getOnlineUserCount();
    }

    /**
     * 获取统计信息
     *
     * @return 统计信息JSON字符串
     */
    public String getStats() {
        return String.format(
                "ConnectionHandler{totalConnections=%d, currentConnections=%d, peakConnections=%d, onlineUsers=%d}",
                getTotalConnections(),
                getCurrentConnections(),
                getPeakConnections(),
                getOnlineUserCount()
        );
    }

    /**
     * 重置统计信息
     */
    public void resetStats() {
        totalConnections.set(0);
        log.info("连接统计信息已重置");
    }
}
