package com.cw.im.client;

import com.cw.im.common.codec.IMMessageDecoder;
import com.cw.im.common.codec.IMMessageEncoder;
import com.cw.im.common.model.MessageBody;
import com.cw.im.common.model.MessageHeader;
import com.cw.im.common.protocol.CommandType;
import com.cw.im.common.protocol.IMMessage;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;

import java.util.Scanner;
import java.util.concurrent.TimeUnit;

/**
 * Netty IM 客户端
 *
 * @author cw
 */
@Slf4j
public class NettyIMClient {

    private final String host;
    private final int port;
    private EventLoopGroup workerGroup;
    private Channel channel;

    public NettyIMClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public NettyIMClient() {
        this("localhost", 8080);
    }

    /**
     * 连接到服务器
     */
    public void connect(IMClientHandler.MessageCallback callback) {
        workerGroup = new NioEventLoopGroup();

        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(workerGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();

                        // 编解码器
                        pipeline.addLast("frameDecoder", new io.netty.handler.codec.LengthFieldBasedFrameDecoder(
                            65536, 0, 4, 0, 4));
                        pipeline.addLast("frameEncoder", new io.netty.handler.codec.LengthFieldPrepender(4));

                        // IM 消息编解码器
                        pipeline.addLast("decoder", new IMMessageDecoder());
                        pipeline.addLast("encoder", new IMMessageEncoder());

                        // 业务 Handler
                        pipeline.addLast("handler", new IMClientHandler(callback));
                    }
                });

            // 连接到服务器
            ChannelFuture future = bootstrap.connect(host, port).sync();
            channel = future.channel();

            log.info("========================================");
            log.info("Netty IM 客户端启动成功!");
            log.info("连接到服务器: {}:{}", host, port);
            log.info("========================================");

            // 等待连接关闭
            channel.closeFuture().sync();

        } catch (InterruptedException e) {
            log.error("客户端启动异常", e);
            Thread.currentThread().interrupt();
        } finally {
            shutdown();
        }
    }

    /**
     * 发送消息
     */
    public void sendMessage(IMMessage message) {
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(message).addListener(future -> {
                if (future.isSuccess()) {
                    log.debug("消息发送成功: cmd={}", message.getCmd());
                } else {
                    log.error("消息发送失败: cmd={}", message.getCmd(), future.cause());
                }
            });
        } else {
            log.warn("通道未连接，无法发送消息");
        }
    }

    /**
     * 关闭客户端
     */
    public void shutdown() {
        log.info("正在关闭客户端...");
        if (channel != null) {
            channel.close();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        log.info("客户端已关闭");
    }

    /**
     * 交互式控制台模式
     */
    public void startConsoleMode() {
        Scanner scanner = new Scanner(System.in);

        System.out.println("\n========================================");
        System.out.println("       IM 客户端交互模式");
        System.out.println("========================================");
        System.out.println("命令格式:");
        System.out.println("  1 <userId> <message>  - 发送私聊消息");
        System.out.println("  2 <groupId> <message>  - 发送群聊消息");
        System.out.println("  3 <message>             - 发送公屏消息");
        System.out.println("  q                       - 退出");
        System.out.println("========================================\n");

        System.out.print("请输入您的用户ID: ");
        long myUserId = scanner.nextLong();
        scanner.nextLine(); // 消耗换行符

        System.out.println("\n开始聊天吧！\n");

        while (true) {
            try {
                System.out.print("> ");
                String input = scanner.nextLine().trim();

                if (input.isEmpty()) {
                    continue;
                }

                if ("q".equalsIgnoreCase(input)) {
                    System.out.println("再见！");
                    shutdown();
                    break;
                }

                String[] parts = input.split("\\s+", 3);
                if (parts.length < 2) {
                    System.out.println("命令格式错误，请重新输入");
                    continue;
                }

                int cmdCode = Integer.parseInt(parts[0]);
                CommandType commandType = CommandType.fromCode(cmdCode);

                // 构建消息
                MessageHeader.MessageHeaderBuilder headerBuilder = MessageHeader.builder()
                    .msgId(java.util.UUID.randomUUID().toString())
                    .cmd(commandType)
                    .from(myUserId)
                    .timestamp(System.currentTimeMillis());

                MessageBody.MessageBodyBuilder bodyBuilder = MessageBody.builder()
                    .contentType("text/plain");

                // 根据命令类型设置接收者和内容
                Long toUserId = null;
                String content = "";

                switch (commandType) {
                    case PRIVATE_CHAT:
                        toUserId = Long.parseLong(parts[1]);
                        content = parts.length >= 3 ? parts[2] : "";
                        headerBuilder.to(toUserId);
                        break;
                    case GROUP_CHAT:
                        toUserId = Long.parseLong(parts[1]);
                        content = parts.length >= 3 ? parts[2] : "";
                        headerBuilder.to(toUserId);
                        break;
                    case PUBLIC_CHAT:
                        content = parts[1];
                        headerBuilder.to(0L);
                        break;
                    default:
                        content = parts[parts.length - 1];
                        headerBuilder.to(0L);
                        break;
                }

                bodyBuilder.content(content);

                // 构建完整消息
                IMMessage message = IMMessage.builder()
                    .header(headerBuilder.build())
                    .body(bodyBuilder.build())
                    .build();

                sendMessage(message);

            } catch (Exception e) {
                log.error("处理输入失败", e);
                System.out.println("输入格式错误，请重新输入");
            }
        }
    }

    /**
     * 主入口
     */
    public static void main(String[] args) {
        NettyIMClient client = new NettyIMClient("localhost", 8080);

        // 在单独线程中启动客户端连接
        Thread connectThread = new Thread(() -> {
            client.connect(message -> {
                // 收到消息时的回调
                System.out.println("\n收到消息: " + message.getContent());
                System.out.print("> ");
            });
        });
        connectThread.start();

        // 等待连接建立
        try {
            TimeUnit.SECONDS.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 启动控制台模式
        client.startConsoleMode();
    }
}
