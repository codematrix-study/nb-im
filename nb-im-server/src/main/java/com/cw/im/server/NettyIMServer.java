package com.cw.im.server;

import com.cw.im.common.codec.IMMessageDecoder;
import com.cw.im.common.codec.IMMessageEncoder;
import com.cw.im.common.protocol.IMMessage;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;

/**
 * Netty IM 服务器 - 最小启动版本
 *
 * @author cw
 */
@Slf4j
public class NettyIMServer {

    private static final int DEFAULT_PORT = 8080;

    private final int port;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    public NettyIMServer() {
        this(DEFAULT_PORT);
    }

    public NettyIMServer(int port) {
        this.port = port;
    }

    /**
     * 启动 Netty 服务器
     */
    public void start() {
        // Boss 线程组：处理客户端连接
        bossGroup = new NioEventLoopGroup(1);
        // Worker 线程组：处理 I/O 操作
        workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 128)          // 连接队列大小
                    .childOption(ChannelOption.SO_KEEPALIVE, true)  // 保持长连接
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            log.info("新连接接入: {}", ch.id());

                            // 添加 Pipeline
                            ch.pipeline()

                                    // 1. 拆包解码器（按长度字段拆包）
                                    .addLast("frameDecoder", new LengthFieldBasedFrameDecoder(
                                            65536,   // 最大帧长度
                                            0,         // 长度字段偏移量
                                            4,         // 长度字段长度
                                            0,         // 长度字段后的字节数
                                            4))        // 需要剥离的字节数

                                    // 2. 添加长度字段的编码器
                                    .addLast("frameEncoder", new LengthFieldPrepender(4))

                                    // 3. IM 消息编解码器
                                    .addLast("decoder", new IMMessageDecoder())
                                    .addLast("encoder", new IMMessageEncoder())

                                    // 4. 业务处理器（回显消息）
                                    .addLast("serverHandler", new SimpleChannelInboundHandler<IMMessage>() {
                                        @Override
                                        protected void channelRead0(ChannelHandlerContext ctx, IMMessage msg) {
                                            log.info("收到消息: cmd={}, from={}, to={}, payload={}",
                                                    msg.getCmd(), msg.getFrom(), msg.getTo(), msg.getPayload());

                                            // 原样发送回去（用于测试）
                                            ctx.writeAndFlush(msg);
                                        }

                                        @Override
                                        public void channelActive(ChannelHandlerContext ctx) {
                                            log.info("客户端连接建立: remoteAddress={}", ctx.channel().remoteAddress());
                                        }

                                        @Override
                                        public void channelInactive(ChannelHandlerContext ctx) {
                                            log.info("客户端断开连接: remoteAddress={}", ctx.channel().remoteAddress());
                                        }

                                        @Override
                                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                            log.error("发生异常", cause);
                                            ctx.close();
                                        }
                                    });
                        }
                    });

            // 绑定端口并启动
            ChannelFuture future = bootstrap.bind(port).sync();
            log.info("========================================");
            log.info("Netty IM 服务器启动成功!");
            log.info("监听端口: {}", port);
            log.info("========================================");

            // 等待服务器 Socket 关闭
            future.channel().closeFuture().sync();

        } catch (InterruptedException e) {
            log.error("Netty 服务器启动异常", e);
            Thread.currentThread().interrupt();
        } finally {
            shutdown();
        }
    }

    /**
     * 优雅关闭服务器
     */
    public void shutdown() {
        log.info("正在关闭 Netty 服务器...");
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        log.info("Netty 服务器已关闭");
    }

    /**
     * 主入口
     */
    public static void main(String[] args) {
        NettyIMServer server = new NettyIMServer(8080);

        // 添加关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(server::shutdown));

        server.start();
    }
}
