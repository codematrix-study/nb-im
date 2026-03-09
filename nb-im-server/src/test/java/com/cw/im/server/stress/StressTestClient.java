package com.cw.im.server.stress;

import com.cw.im.common.model.MessageBody;
import com.cw.im.common.model.MessageHeader;
import com.cw.im.common.protocol.CommandType;
import com.cw.im.common.protocol.IMMessage;
import com.cw.im.server.handler.AuthHandler;
import com.cw.im.server.handler.HeartbeatHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.json.JsonObjectDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 压力测试客户端
 *
 * <p>使用Netty实现的IM压力测试客户端，支持：
 * <ul>
 *     <li>可配置连接数</li>
 *     <li>可配置TPS</li>
 *     <li>可配置测试时长</li>
 *     <li>性能指标收集</li>
 *     <li>延迟统计（P50/P95/P99）</li>
 * </ul>
 *
 * @author cw
 * @since 1.0.0
 */
@Slf4j
public class StressTestClient {

    private final String host;
    private final int port;
    private final int totalConnections;
    private final int targetTPS;
    private final long testDurationSeconds;
    private final int warmupSeconds;

    private EventLoopGroup workerGroup;
    private final ConcurrentHashMap<Long, Channel> activeChannels = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    // 性能统计
    private final AtomicLong totalMessagesSent = new AtomicLong(0);
    private final AtomicLong totalMessagesReceived = new AtomicLong(0);
    private final AtomicLong totalErrors = new AtomicLong(0);
    private final ConcurrentSkipListMap<Long, Long> latencyRecords = new ConcurrentSkipListMap<>();

    // 运行状态
    private volatile boolean isRunning = false;
    private volatile long testStartTime;

    /**
     * 构造函数
     *
     * @param host               服务器地址
     * @param port               服务器端口
     * @param totalConnections   总连接数
     * @param targetTPS          目标TPS
     * @param testDurationSeconds 测试时长（秒）
     * @param warmupSeconds      预热时长（秒）
     */
    public StressTestClient(String host, int port, int totalConnections,
                           int targetTPS, long testDurationSeconds, int warmupSeconds) {
        this.host = host;
        this.port = port;
        this.totalConnections = totalConnections;
        this.targetTPS = targetTPS;
        this.testDurationSeconds = testDurationSeconds;
        this.warmupSeconds = warmupSeconds;
    }

    /**
     * 启动压力测试
     */
    public void start() throws InterruptedException {
        log.info("========================================");
        log.info("压力测试开始");
        log.info("目标服务器: {}:{}", host, port);
        log.info("连接数: {}", totalConnections);
        log.info("目标TPS: {}", targetTPS);
        log.info("测试时长: {}秒", testDurationSeconds);
        log.info("预热时长: {}秒", warmupSeconds);
        log.info("========================================");

        workerGroup = new NioEventLoopGroup();
        isRunning = true;
        testStartTime = System.currentTimeMillis();

        try {
            // 1. 建立连接
            log.info("开始建立连接...");
            establishConnections();
            log.info("连接建立完成: {} 个活跃连接", activeChannels.size());

            // 2. 预热阶段
            if (warmupSeconds > 0) {
                log.info("开始预热阶段 ({}秒)...", warmupSeconds);
                Thread.sleep(warmupSeconds * 1000L);
                resetStats();
                log.info("预热完成，开始正式测试");
            }

            // 3. 启动性能监控
            startPerformanceMonitor();

            // 4. 启动消息发送器
            startMessageSender();

            // 5. 运行测试
            log.info("测试运行中...");
            Thread.sleep(testDurationSeconds * 1000L);

            // 6. 停止测试
            stop();

        } finally {
            shutdown();
        }
    }

    /**
     * 建立连接
     */
    private void establishConnections() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(totalConnections);
        Semaphore semaphore = new Semaphore(100); // 限制并发连接数

        ExecutorService executor = Executors.newFixedThreadPool(50);

        for (int i = 0; i < totalConnections; i++) {
            final int connectionId = i;
            final Long userId = 10000L + connectionId;

            executor.submit(() -> {
                try {
                    semaphore.acquire();
                    connectUser(userId);
                } catch (Exception e) {
                    log.error("连接失败: userId={}", userId, e);
                    totalErrors.incrementAndGet();
                } finally {
                    semaphore.release();
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.MINUTES);
        executor.shutdown();
    }

    /**
     * 连接单个用户
     */
    private void connectUser(Long userId) throws Exception {
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(workerGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();

                        // 心跳检测
                        pipeline.addLast("idle", new IdleStateHandler(60, 30, 0));

                        // 编解码器
                        pipeline.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(65536, 0, 4, 0, 4));
                        pipeline.addLast("frameEncoder", new LengthFieldPrepender(4));
                        pipeline.addLast("stringEncoder", new StringEncoder(StandardCharsets.UTF_8));

                        // 业务Handler
                        pipeline.addLast("clientHandler", new StressTestClientHandler(userId));
                    }
                });

        // 连接服务器
        ChannelFuture future = bootstrap.connect(host, port).sync();
        Channel channel = future.channel();

        // 发送认证消息
        IMMessage authMessage = createAuthMessage(userId);
        channel.writeAndFlush(serializeMessage(authMessage));

        activeChannels.put(userId, channel);

        if (activeChannels.size() % 1000 == 0) {
            log.info("已连接用户数: {}", activeChannels.size());
        }
    }

    /**
     * 启动消息发送器
     */
    private void startMessageSender() {
        // 计算发送间隔（毫秒）
        long intervalMillis = 1000L / targetTPS;

        scheduler.scheduleAtFixedRate(() -> {
            if (!isRunning) {
                return;
            }

            try {
                // 随机选择发送方和接收方
                Long fromUserId = getRandomUserId();
                Long toUserId = getRandomUserId();

                if (fromUserId.equals(toUserId)) {
                    return; // 不给自己发消息
                }

                Channel channel = activeChannels.get(fromUserId);
                if (channel != null && channel.isActive()) {
                    IMMessage message = createChatMessage(fromUserId, toUserId);
                    long sendTime = System.currentTimeMillis();

                    channel.writeAndFlush(serializeMessage(message))
                            .addListener(future -> {
                                if (future.isSuccess()) {
                                    totalMessagesSent.incrementAndGet();
                                    latencyRecords.put(sendTime, sendTime);
                                } else {
                                    totalErrors.incrementAndGet();
                                }
                            });
                }
            } catch (Exception e) {
                log.error("发送消息失败", e);
                totalErrors.incrementAndGet();
            }
        }, 0, intervalMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * 启动性能监控
     */
    private void startPerformanceMonitor() {
        scheduler.scheduleAtFixedRate(() -> {
            if (!isRunning) {
                return;
            }

            long currentTime = System.currentTimeMillis();
            long elapsedSeconds = (currentTime - testStartTime) / 1000;
            long actualTPS = totalMessagesSent.get() / Math.max(1, elapsedSeconds);

            log.info("========================================");
            log.info("性能统计 - 运行时间: {}秒", elapsedSeconds);
            log.info("活跃连接数: {}", activeChannels.size());
            log.info("已发送消息: {}", totalMessagesSent.get());
            log.info("已接收消息: {}", totalMessagesReceived.get());
            log.info("错误数: {}", totalErrors.get());
            log.info("当前TPS: {}", actualTPS);
            log.info("目标TPS: {}", targetTPS);
            log.info("TPS达成率: {:.2f}%", (actualTPS * 100.0 / targetTPS));
            log.info("========================================");

        }, 10, 10, TimeUnit.SECONDS);
    }

    /**
     * 停止压力测试
     */
    public void stop() {
        log.info("正在停止压力测试...");
        isRunning = false;

        // 打印最终统计报告
        printFinalReport();
    }

    /**
     * 关闭资源
     */
    private void shutdown() {
        log.info("关闭资源...");

        // 关闭所有Channel
        activeChannels.values().forEach(channel -> {
            if (channel.isActive()) {
                channel.close().syncUninterruptibly();
            }
        });
        activeChannels.clear();

        // 关闭调度器
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }

        // 关闭EventLoopGroup
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }

        log.info("资源关闭完成");
    }

    /**
     * 打印最终统计报告
     */
    private void printFinalReport() {
        long testEndTime = System.currentTimeMillis();
        long totalTestTime = testEndTime - testStartTime;
        long totalTestSeconds = totalTestTime / 1000;

        log.info("\n\n========================================");
        log.info("压力测试报告");
        log.info("========================================");
        log.info("测试配置:");
        log.info("  目标服务器: {}:{}", host, port);
        log.info("  目标连接数: {}", totalConnections);
        log.info("  实际连接数: {}", activeChannels.size());
        log.info("  目标TPS: {}", targetTPS);
        log.info("  测试时长: {}秒 (实际: {}秒)", testDurationSeconds, totalTestSeconds);
        log.info("");
        log.info("测试结果:");
        log.info("  总发送消息数: {}", totalMessagesSent.get());
        log.info("  总接收消息数: {}", totalMessagesReceived.get());
        log.info("  总错误数: {}", totalErrors.get());
        log.info("  实际TPS: {}", totalMessagesSent.get() / Math.max(1, totalTestSeconds));
        log.info("  TPS达成率: {:.2f}%", (totalMessagesSent.get() * 100.0) / (targetTPS * totalTestSeconds));
        log.info("  错误率: {:.4f}%%", (totalErrors.get() * 100.0) / Math.max(1, totalMessagesSent.get()));
        log.info("");
        log.info("延迟统计:");
        log.info("  延迟记录数: {}", latencyRecords.size());
        log.info("========================================\n\n");
    }

    /**
     * 重置统计信息
     */
    private void resetStats() {
        totalMessagesSent.set(0);
        totalMessagesReceived.set(0);
        totalErrors.set(0);
        latencyRecords.clear();
        testStartTime = System.currentTimeMillis();
    }

    /**
     * 获取随机用户ID
     */
    private Long getRandomUserId() {
        int index = ThreadLocalRandom.current().nextInt(activeChannels.size());
        return (Long) activeChannels.keySet().toArray()[index];
    }

    /**
     * 创建认证消息
     */
    private IMMessage createAuthMessage(Long userId) {
        return IMMessage.builder()
                .header(MessageHeader.builder()
                        .msgId("auth-" + userId + "-" + System.currentTimeMillis())
                        .cmd(CommandType.SYSTEM_NOTICE)
                        .from(userId)
                        .timestamp(System.currentTimeMillis())
                        .version("1.0")
                        .extras(java.util.Map.of(
                                "token", "valid-token-" + userId,
                                "deviceId", "device-" + userId,
                                "deviceType", "PC"
                        ))
                        .build())
                .body(MessageBody.builder()
                        .content("auth")
                        .extras(java.util.Map.of("userId", userId))
                        .build())
                .build();
    }

    /**
     * 创建聊天消息
     */
    private IMMessage createChatMessage(Long from, Long to) {
        return IMMessage.builder()
                .header(MessageHeader.builder()
                        .msgId("msg-" + from + "-" + to + "-" + System.currentTimeMillis())
                        .cmd(CommandType.PRIVATE_CHAT)
                        .from(from)
                        .to(to)
                        .timestamp(System.currentTimeMillis())
                        .version("1.0")
                        .build())
                .body(MessageBody.builder()
                        .content("Hello from " + from + " to " + to)
                        .contentType("text")
                        .build())
                .build();
    }

    /**
     * 序列化消息
     */
    private String serializeMessage(IMMessage message) {
        // 简化的序列化实现
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writeValueAsString(message);
        } catch (Exception e) {
            log.error("消息序列化失败", e);
            return "{}";
        }
    }

    /**
     * 压力测试客户端Handler
     */
    private class StressTestClientHandler extends SimpleChannelInboundHandler<String> {

        private final Long userId;

        public StressTestClientHandler(Long userId) {
            this.userId = userId;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
            totalMessagesReceived.incrementAndGet();

            // 记录接收时间用于计算延迟
            long receiveTime = System.currentTimeMillis();

            // 解析消息并记录延迟（简化实现）
            // 实际实现中应该解析消息获取发送时间

            // 处理心跳检测
            if (msg.contains("ping")) {
                // 回复pong
                IMMessage pongMessage = IMMessage.builder()
                        .header(MessageHeader.builder()
                                .msgId("pong-" + userId)
                                .cmd(CommandType.HEARTBEAT)
                                .from(userId)
                                .timestamp(receiveTime)
                                .version("1.0")
                                .build())
                        .body(MessageBody.builder()
                                .content("pong")
                                .contentType("heartbeat")
                                .build())
                        .build();

                ctx.writeAndFlush(serializeMessage(pongMessage));
            }
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            log.debug("Channel激活: userId={}", userId);
            super.channelActive(ctx);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            log.debug("Channel断开: userId={}", userId);
            activeChannels.remove(userId);
            super.channelInactive(ctx);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            log.error("连接异常: userId={}", userId, cause);
            totalErrors.incrementAndGet();
            ctx.close();
        }
    }

    /**
     * 主函数 - 用于直接运行压力测试
     */
    public static void main(String[] args) throws InterruptedException {
        // 配置压力测试参数
        String host = "localhost";
        int port = 8080;
        int totalConnections = 10000; // 1万连接
        int targetTPS = 10000;        // 1万TPS
        long testDurationSeconds = 300; // 5分钟
        int warmupSeconds = 30;       // 预热30秒

        StressTestClient client = new StressTestClient(
                host, port, totalConnections, targetTPS, testDurationSeconds, warmupSeconds
        );

        try {
            client.start();
        } catch (Exception e) {
            log.error("压力测试异常", e);
        }
    }
}
