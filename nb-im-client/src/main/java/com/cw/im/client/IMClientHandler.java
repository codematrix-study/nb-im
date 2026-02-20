package com.cw.im.client;

import com.cw.im.common.protocol.CommandType;
import com.cw.im.common.protocol.IMMessage;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * IM 客户端处理器
 *
 * @author cw
 */
@Slf4j
public class IMClientHandler extends SimpleChannelInboundHandler<IMMessage> {

    /**
     * 消息回调接口
     */
    public interface MessageCallback {
        void onMessageReceived(IMMessage message);
    }

    private final MessageCallback callback;

    public IMClientHandler(MessageCallback callback) {
        this.callback = callback;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, IMMessage msg) {
        log.info("收到消息: cmd={}, from={}, to={}, payload={}",
            msg.getCmd(), msg.getFrom(), msg.getTo(), msg.getPayload());

        // 调用回调
        if (callback != null) {
            callback.onMessageReceived(msg);
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        log.info("连接已建立: {}", ctx.channel().remoteAddress());

        // 连接建立后，可以发送认证消息或心跳
        // sendAuth(ctx);
        // startHeartbeat(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.warn("连接已断开: {}", ctx.channel().remoteAddress());
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        // 可以在这里处理空闲事件，发送心跳
        // if (evt instanceof IdleStateEvent) {
        //     sendHeartbeat(ctx);
        // }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("发生异常", cause);
        ctx.close();
    }

    /**
     * 发送认证消息
     */
    private void sendAuth(ChannelHandlerContext ctx) {
        IMMessage authMsg = IMMessage.builder()
            .msgId(java.util.UUID.randomUUID().toString())
            .cmd(CommandType.SYSTEM_NOTICE)
            .timestamp(System.currentTimeMillis())
            .payload("{\"type\":\"auth\",\"token\":\"your-token\"}")
            .build();

        ctx.writeAndFlush(authMsg);
        log.info("发送认证消息");
    }

    /**
     * 发送心跳
     */
    private void sendHeartbeat(ChannelHandlerContext ctx) {
        IMMessage heartbeat = IMMessage.builder()
            .msgId(java.util.UUID.randomUUID().toString())
            .cmd(CommandType.HEARTBEAT)
            .timestamp(System.currentTimeMillis())
            .build();

        ctx.writeAndFlush(heartbeat);
        log.debug("发送心跳");
    }
}
