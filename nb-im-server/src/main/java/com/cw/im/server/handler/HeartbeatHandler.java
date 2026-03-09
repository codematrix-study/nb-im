package com.cw.im.server.handler;

import com.cw.im.common.protocol.CommandType;
import com.cw.im.common.protocol.IMMessage;
import com.cw.im.redis.OnlineStatusService;
import com.cw.im.server.attributes.ChannelAttributes;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;

/**
 * 心跳 Handler
 *
 * <p>负责处理客户端心跳检测，维护连接活性</p>
 *
 * <h3>心跳机制</h3>
 * <ul>
 *     <li>服务端检测：60秒读空闲时发送心跳检测</li>
 *     <li>客户端响应：收到心跳检测后立即回复心跳</li>
 *     <li>超时断开：120秒无心跳则关闭连接</li>
 *     <li>更新Redis：每次心跳更新Redis中的心跳时间戳</li>
 * </ul>
 *
 * <h3>心跳消息格式</h3>
 * <pre>
 * {
 *   "header": {
 *     "cmd": "HEARTBEAT",
 *     "from": userId,
 *     "timestamp": 1234567890
 *   },
 *   "body": {
 *     "content": "ping" | "pong"
 *   }
 * }
 * </pre>
 *
 * @author cw
 * @since 1.0.0
 */
@Slf4j
public class HeartbeatHandler extends ChannelInboundHandlerAdapter {

    /**
     * 读空闲时间（秒）
     */
    private static final int READER_IDLE_TIME_SECONDS = 60;

    /**
     * 心跳超时时间（秒）
     */
    private static final int HEARTBEAT_TIMEOUT_SECONDS = 120;

    /**
     * 在线状态服务
     */
    private final OnlineStatusService onlineStatusService;

    /**
     * 构造函数
     *
     * @param onlineStatusService 在线状态服务
     */
    public HeartbeatHandler(OnlineStatusService onlineStatusService) {
        this.onlineStatusService = onlineStatusService;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof IMMessage)) {
            ctx.fireChannelRead(msg);
            return;
        }

        IMMessage message = (IMMessage) msg;

        // 1. 检查是否为心跳消息
        if (message.getCmd() == CommandType.HEARTBEAT) {
            handleHeartbeat(ctx, message);
        } else {
            // 2. 非心跳消息，更新心跳时间并传递给下一个 Handler
            updateHeartbeat(ctx);
            ctx.fireChannelRead(msg);
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;

            if (event.state() == IdleState.READER_IDLE) {
                // 读空闲超时，发送心跳检测
                handleReaderIdle(ctx);
            } else if (event.state() == IdleState.WRITER_IDLE) {
                // 写空闲（一般不需要处理）
                log.debug("写空闲: remoteAddress={}", ctx.channel().remoteAddress());
            } else if (event.state() == IdleState.ALL_IDLE) {
                // 读写空闲
                log.debug("读写空闲: remoteAddress={}", ctx.channel().remoteAddress());
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Long userId = ChannelAttributes.getUserId(ctx.channel());
        log.info("连接断开: userId={}, remoteAddress={}",
                userId, ctx.channel().remoteAddress());
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("心跳Handler异常: remoteAddress={}",
                ctx.channel().remoteAddress(), cause);
        ctx.close();
    }

    // ==================== 私有方法 ====================

    /**
     * 处理心跳消息
     *
     * @param ctx     ChannelHandlerContext
     * @param message 心跳消息
     */
    private void handleHeartbeat(ChannelHandlerContext ctx, IMMessage message) {
        Long userId = ChannelAttributes.getUserId(ctx.channel());
        String content = message.getContent();

        try {
            if ("ping".equalsIgnoreCase(content)) {
                // 客户端发送的心跳请求，回复 pong
                log.debug("收到心跳请求: userId={}, content=ping", userId);
                sendPong(ctx);
            } else if ("pong".equalsIgnoreCase(content)) {
                // 收到客户端的心跳响应
                log.debug("收到心跳响应: userId={}, content=pong", userId);
                updateHeartbeat(ctx);
            } else {
                // 其他心跳消息
                log.debug("收到心跳消息: userId={}, content={}", userId, content);
                updateHeartbeat(ctx);
            }
        } catch (Exception e) {
            log.error("处理心跳消息失败: userId={}", userId, e);
        }
    }

    /**
     * 处理读空闲超时
     *
     * @param ctx ChannelHandlerContext
     */
    private void handleReaderIdle(ChannelHandlerContext ctx) {
        Long userId = ChannelAttributes.getUserId(ctx.channel());
        Long lastHeartbeatTime = ChannelAttributes.getLastHeartbeatTime(ctx.channel());
        long currentTime = System.currentTimeMillis();

        // 1. 计算距离上次心跳的时间
        long timeSinceLastHeartbeat = lastHeartbeatTime != null
                ? (currentTime - lastHeartbeatTime) / 1000
                : HEARTBEAT_TIMEOUT_SECONDS + 1;

        if (timeSinceLastHeartbeat > HEARTBEAT_TIMEOUT_SECONDS) {
            // 2. 心跳超时，关闭连接
            log.warn("心跳超时，关闭连接: userId={}, remoteAddress={}, 距离上次心跳={}s, 超时时间={}s",
                    userId, ctx.channel().remoteAddress(), timeSinceLastHeartbeat, HEARTBEAT_TIMEOUT_SECONDS);
            ctx.close();
        } else {
            // 3. 发送心跳检测（ping）
            log.debug("读空闲超时，发送心跳检测: userId={}, timeSinceLastHeartbeat={}s",
                    userId, timeSinceLastHeartbeat);
            sendPing(ctx);
        }
    }

    /**
     * 发送心跳检测（ping）
     *
     * @param ctx ChannelHandlerContext
     */
    private void sendPing(ChannelHandlerContext ctx) {
        try {
            Long userId = ChannelAttributes.getUserId(ctx.channel());

            IMMessage pingMessage = IMMessage.builder()
                    .header(com.cw.im.common.model.MessageHeader.builder()
                            .msgId(java.util.UUID.randomUUID().toString())
                            .cmd(CommandType.HEARTBEAT)
                            .from(0L)  // 系统消息
                            .to(userId != null ? userId : 0L)
                            .timestamp(System.currentTimeMillis())
                            .version("1.0")
                            .build())
                    .body(com.cw.im.common.model.MessageBody.builder()
                            .content("ping")
                            .contentType("heartbeat")
                            .build())
                    .build();

            ctx.writeAndFlush(pingMessage);
            log.debug("发送心跳检测: userId={}", userId);
        } catch (Exception e) {
            log.error("发送心跳检测失败: remoteAddress={}", ctx.channel().remoteAddress(), e);
        }
    }

    /**
     * 发送心跳响应（pong）
     *
     * @param ctx ChannelHandlerContext
     */
    private void sendPong(ChannelHandlerContext ctx) {
        try {
            Long userId = ChannelAttributes.getUserId(ctx.channel());

            IMMessage pongMessage = IMMessage.builder()
                    .header(com.cw.im.common.model.MessageHeader.builder()
                            .msgId(java.util.UUID.randomUUID().toString())
                            .cmd(CommandType.HEARTBEAT)
                            .from(0L)  // 系统消息
                            .to(userId != null ? userId : 0L)
                            .timestamp(System.currentTimeMillis())
                            .version("1.0")
                            .build())
                    .body(com.cw.im.common.model.MessageBody.builder()
                            .content("pong")
                            .contentType("heartbeat")
                            .build())
                    .build();

            ctx.writeAndFlush(pongMessage);
            log.debug("发送心跳响应: userId={}", userId);
        } catch (Exception e) {
            log.error("发送心跳响应失败: remoteAddress={}", ctx.channel().remoteAddress(), e);
        }
    }

    /**
     * 更新心跳时间
     *
     * @param ctx ChannelHandlerContext
     */
    private void updateHeartbeat(ChannelHandlerContext ctx) {
        try {
            // 1. 更新 Channel 属性中的心跳时间
            ChannelAttributes.updateHeartbeatTime(ctx.channel());

            // 2. 更新 Redis 中的心跳时间
            Long userId = ChannelAttributes.getUserId(ctx.channel());
            String deviceId = ChannelAttributes.getDeviceId(ctx.channel());
            String channelId = ctx.channel().id().asLongText();

            if (userId != null && onlineStatusService != null) {
                onlineStatusService.heartbeat(userId, channelId);
                log.debug("更新心跳时间: userId={}, channelId={}", userId, channelId);
            }
        } catch (Exception e) {
            log.error("更新心跳时间失败: remoteAddress={}",
                    ctx.channel().remoteAddress(), e);
        }
    }

    /**
     * 获取读空闲时间（秒）
     *
     * @return 读空闲时间
     */
    public static int getReaderIdleTimeSeconds() {
        return READER_IDLE_TIME_SECONDS;
    }

    /**
     * 获取心跳超时时间（秒）
     *
     * @return 心跳超时时间
     */
    public static int getHeartbeatTimeoutSeconds() {
        return HEARTBEAT_TIMEOUT_SECONDS;
    }
}
