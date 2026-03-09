package com.cw.im.server;

import com.cw.im.common.protocol.CommandType;
import com.cw.im.common.protocol.IMMessage;
import com.cw.im.common.model.MessageHeader;
import com.cw.im.common.model.MessageBody;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Netty IM 服务器测试类
 *
 * <p>用于测试服务器的各项功能</p>
 *
 * <h3>测试场景</h3>
 * <ul>
 *     <li>服务器启动和关闭</li>
 *     <li>客户端连接和断开</li>
 *     <li>认证流程测试</li>
 *     <li>心跳消息测试</li>
 *     <li>私聊消息路由测试</li>
 *     <li>多端登录测试</li>
 * </ul>
 *
 * @author cw
 * @since 1.0.0
 */
@Slf4j
public class NettyIMServerTest {

    private static final String HOST = "localhost";
    private static final int PORT = 8080;

    public static void main(String[] args) {
        log.info("========================================");
        log.info("Netty IM 服务器测试");
        log.info("========================================");

        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.println("\n请选择测试场景:");
            System.out.println("1. 启动服务器");
            System.out.println("2. 测试客户端连接");
            System.out.println("3. 测试私聊消息");
            System.out.println("4. 测试多端登录");
            System.out.println("5. 测试心跳");
            System.out.println("0. 退出");
            System.out.print("请输入选项: ");

            String choice = scanner.nextLine();

            switch (choice) {
                case "1":
                    testStartServer();
                    break;
                case "2":
                    testClientConnection();
                    break;
                case "3":
                    testPrivateChat();
                    break;
                case "4":
                    testMultiDeviceLogin();
                    break;
                case "5":
                    testHeartbeat();
                    break;
                case "0":
                    log.info("退出测试");
                    return;
                default:
                    System.out.println("无效选项");
                    break;
            }
        }
    }

    /**
     * 测试启动服务器
     */
    private static void testStartServer() {
        log.info("========================================");
        log.info("测试场景: 启动服务器");
        log.info("========================================");

        NettyIMServer server = new NettyIMServer(PORT);

        // 添加关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("正在关闭服务器...");
            server.shutdown();
        }));

        try {
            server.start();
        } catch (Exception e) {
            log.error("服务器启动失败", e);
        }
    }

    /**
     * 测试客户端连接
     */
    private static void testClientConnection() {
        log.info("========================================");
        log.info("测试场景: 客户端连接");
        log.info("========================================");

        EventLoopGroup group = new NioEventLoopGroup();

        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                    .addLast("frameDecoder", new LengthFieldBasedFrameDecoder(65536, 0, 4, 0, 4))
                                    .addLast("frameEncoder", new LengthFieldPrepender(4))
                                    .addLast("decoder", new com.cw.im.common.codec.IMMessageDecoder())
                                    .addLast("encoder", new com.cw.im.common.codec.IMMessageEncoder())
                                    .addLast("clientHandler", new SimpleChannelInboundHandler<IMMessage>() {
                                        @Override
                                        protected void channelRead0(ChannelHandlerContext ctx, IMMessage msg) {
                                            log.info("收到消息: cmd={}, from={}, content={}",
                                                    msg.getCmd(), msg.getFrom(), msg.getContent());
                                        }

                                        @Override
                                        public void channelActive(ChannelHandlerContext ctx) {
                                            log.info("连接建立: {}", ctx.channel().remoteAddress());
                                        }

                                        @Override
                                        public void channelInactive(ChannelHandlerContext ctx) {
                                            log.info("连接断开: {}", ctx.channel().remoteAddress());
                                        }
                                    });
                        }
                    });

            // 连接服务器
            ChannelFuture future = bootstrap.connect(HOST, PORT).sync();
            log.info("已连接到服务器: {}:{}", HOST, PORT);

            // 发送认证消息
            Long userId = 1001L;
            IMMessage authMessage = createAuthMessage(userId);
            future.channel().writeAndFlush(authMessage);
            log.info("发送认证消息: userId={}", userId);

            // 等待一段时间
            Thread.sleep(5000);

            // 关闭连接
            future.channel().close().sync();
            log.info("已断开连接");

        } catch (Exception e) {
            log.error("测试失败", e);
        } finally {
            group.shutdownGracefully();
        }
    }

    /**
     * 测试私聊消息
     */
    private static void testPrivateChat() {
        log.info("========================================");
        log.info("测试场景: 私聊消息");
        log.info("========================================");

        EventLoopGroup group = new NioEventLoopGroup();
        CountDownLatch latch = new CountDownLatch(1);

        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                    .addLast("frameDecoder", new LengthFieldBasedFrameDecoder(65536, 0, 4, 0, 4))
                                    .addLast("frameEncoder", new LengthFieldPrepender(4))
                                    .addLast("decoder", new com.cw.im.common.codec.IMMessageDecoder())
                                    .addLast("encoder", new com.cw.im.common.codec.IMMessageEncoder())
                                    .addLast("clientHandler", new SimpleChannelInboundHandler<IMMessage>() {
                                        @Override
                                        protected void channelRead0(ChannelHandlerContext ctx, IMMessage msg) {
                                            log.info("收到消息: cmd={}, from={}, content={}",
                                                    msg.getCmd(), msg.getFrom(), msg.getContent());

                                            if (msg.getCmd() == CommandType.ACK) {
                                                log.info("收到ACK: {}", msg.getContent());
                                                latch.countDown();
                                            }
                                        }
                                    });
                        }
                    });

            ChannelFuture future = bootstrap.connect(HOST, PORT).sync();
            Channel channel = future.channel();

            // 1. 发送认证消息
            Long userId = 1002L;
            IMMessage authMessage = createAuthMessage(userId);
            channel.writeAndFlush(authMessage);
            log.info("发送认证消息: userId={}", userId);
            Thread.sleep(1000);

            // 2. 发送私聊消息
            IMMessage chatMessage = createPrivateChatMessage(userId, 1003L, "Hello, 这是测试消息");
            channel.writeAndFlush(chatMessage);
            log.info("发送私聊消息: from={}, to={}", userId, 1003L);

            // 等待 ACK
            boolean received = latch.await(5, TimeUnit.SECONDS);
            if (received) {
                log.info("测试成功: 收到ACK");
            } else {
                log.warn("测试失败: 未收到ACK");
            }

            Thread.sleep(2000);
            channel.close().sync();

        } catch (Exception e) {
            log.error("测试失败", e);
        } finally {
            group.shutdownGracefully();
        }
    }

    /**
     * 测试多端登录
     */
    private static void testMultiDeviceLogin() {
        log.info("========================================");
        log.info("测试场景: 多端登录");
        log.info("========================================");

        AtomicInteger loginCount = new AtomicInteger(0);
        CountDownLatch allConnected = new CountDownLatch(3);

        // 模拟同一个用户从3个设备登录
        Long userId = 1004L;
        String[] deviceTypes = {"PC", "MOBILE", "WEB"};

        for (String deviceType : deviceTypes) {
            new Thread(() -> {
                EventLoopGroup group = new NioEventLoopGroup();

                try {
                    Bootstrap bootstrap = new Bootstrap();
                    bootstrap.group(group)
                            .channel(NioSocketChannel.class)
                            .option(ChannelOption.TCP_NODELAY, true)
                            .handler(new ChannelInitializer<SocketChannel>() {
                                @Override
                                protected void initChannel(SocketChannel ch) {
                                    ch.pipeline()
                                            .addLast("frameDecoder", new LengthFieldBasedFrameDecoder(65536, 0, 4, 0, 4))
                                            .addLast("frameEncoder", new LengthFieldPrepender(4))
                                            .addLast("decoder", new com.cw.im.common.codec.IMMessageDecoder())
                                            .addLast("encoder", new com.cw.im.common.codec.IMMessageEncoder())
                                            .addLast("clientHandler", new SimpleChannelInboundHandler<IMMessage>() {
                                                @Override
                                                protected void channelRead0(ChannelHandlerContext ctx, IMMessage msg) {
                                                    log.info("[{}] 收到消息: {}", deviceType, msg.getContent());
                                                }

                                                @Override
                                                public void channelActive(ChannelHandlerContext ctx) {
                                                    log.info("[{}] 设备连接成功", deviceType);
                                                    int count = loginCount.incrementAndGet();
                                                    log.info("已登录设备数: {}/{}", count, deviceTypes.length);
                                                    allConnected.countDown();
                                                }
                                    });
                                }
                            });

                    ChannelFuture future = bootstrap.connect(HOST, PORT).sync();
                    Channel channel = future.channel();

                    // 发送认证消息
                    IMMessage authMessage = createAuthMessage(userId, deviceType);
                    channel.writeAndFlush(authMessage);
                    log.info("[{}] 发送认证消息", deviceType);

                    // 保持连接
                    Thread.sleep(30000);

                    channel.close().sync();

                } catch (Exception e) {
                    log.error("[{}] 连接失败", deviceType, e);
                } finally {
                    group.shutdownGracefully();
                }
            }).start();
        }

        try {
            // 等待所有设备连接
            boolean allConnectedSuccess = allConnected.await(10, TimeUnit.SECONDS);
            if (allConnectedSuccess) {
                log.info("测试成功: 所有设备都已登录");
                Thread.sleep(25000); // 让设备保持连接30秒
            } else {
                log.warn("测试失败: 部分设备未成功登录");
            }
        } catch (InterruptedException e) {
            log.error("测试被中断", e);
        }
    }

    /**
     * 测试心跳
     */
    private static void testHeartbeat() {
        log.info("========================================");
        log.info("测试场景: 心跳");
        log.info("========================================");

        EventLoopGroup group = new NioEventLoopGroup();

        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline()
                                    .addLast("frameDecoder", new LengthFieldBasedFrameDecoder(65536, 0, 4, 0, 4))
                                    .addLast("frameEncoder", new LengthFieldPrepender(4))
                                    .addLast("decoder", new com.cw.im.common.codec.IMMessageDecoder())
                                    .addLast("encoder", new com.cw.im.common.codec.IMMessageEncoder())
                                    .addLast("clientHandler", new SimpleChannelInboundHandler<IMMessage>() {
                                        @Override
                                        protected void channelRead0(ChannelHandlerContext ctx, IMMessage msg) {
                                            if (msg.getCmd() == CommandType.HEARTBEAT) {
                                                log.info("收到心跳: {}", msg.getContent());
                                                // 回复 pong
                                                if ("ping".equals(msg.getContent())) {
                                                    IMMessage pong = IMMessage.builder()
                                                            .header(MessageHeader.builder()
                                                                    .msgId(UUID.randomUUID().toString())
                                                                    .cmd(CommandType.HEARTBEAT)
                                                                    .from(1005L)
                                                                    .to(0L)
                                                                    .timestamp(System.currentTimeMillis())
                                                                    .version("1.0")
                                                                    .build())
                                                            .body(MessageBody.builder()
                                                                    .content("pong")
                                                                    .contentType("heartbeat")
                                                                    .build())
                                                            .build();
                                                    ctx.writeAndFlush(pong);
                                                }
                                            } else {
                                                log.info("收到消息: {}", msg.getContent());
                                            }
                                        }
                                    });
                        }
                    });

            ChannelFuture future = bootstrap.connect(HOST, PORT).sync();
            Channel channel = future.channel();

            // 发送认证消息
            Long userId = 1005L;
            IMMessage authMessage = createAuthMessage(userId);
            channel.writeAndFlush(authMessage);
            log.info("发送认证消息: userId={}", userId);

            // 保持连接，观察心跳
            log.info("保持连接60秒，观察心跳...");
            Thread.sleep(60000);

            channel.close().sync();

        } catch (Exception e) {
            log.error("测试失败", e);
        } finally {
            group.shutdownGracefully();
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 创建认证消息
     *
     * @param userId 用户ID
     * @return 认证消息
     */
    private static IMMessage createAuthMessage(Long userId) {
        return createAuthMessage(userId, "PC");
    }

    /**
     * 创建认证消息
     *
     * @param userId     用户ID
     * @param deviceType 设备类型
     * @return 认证消息
     */
    private static IMMessage createAuthMessage(Long userId, String deviceType) {
        Map<String, Object> headerExtras = new HashMap<>();
        headerExtras.put("token", "valid-token-" + userId);
        headerExtras.put("deviceType", deviceType);
        headerExtras.put("deviceId", "device-" + userId);

        return IMMessage.builder()
                .header(MessageHeader.builder()
                        .msgId(UUID.randomUUID().toString())
                        .cmd(CommandType.PRIVATE_CHAT) // 使用私聊消息进行认证
                        .from(userId)
                        .to(0L)
                        .timestamp(System.currentTimeMillis())
                        .version("1.0")
                        .extras(headerExtras)
                        .build())
                .body(MessageBody.builder()
                        .content("auth")
                        .contentType("auth")
                        .build())
                .build();
    }

    /**
     * 创建私聊消息
     *
     * @param from 发送者ID
     * @param to   接收者ID
     * @param content 消息内容
     * @return 私聊消息
     */
    private static IMMessage createPrivateChatMessage(Long from, Long to, String content) {
        return IMMessage.builder()
                .header(MessageHeader.builder()
                        .msgId(UUID.randomUUID().toString())
                        .cmd(CommandType.PRIVATE_CHAT)
                        .from(from)
                        .to(to)
                        .timestamp(System.currentTimeMillis())
                        .version("1.0")
                        .build())
                .body(MessageBody.builder()
                        .content(content)
                        .contentType("text")
                        .build())
                .build();
    }
}
