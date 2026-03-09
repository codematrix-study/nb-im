package com.cw.im.server;

import com.cw.im.common.codec.IMMessageDecoder;
import com.cw.im.common.codec.IMMessageEncoder;
import com.cw.im.kafka.KafkaProducerManager;
import com.cw.im.kafka.KafkaConsumerService;
import com.cw.im.redis.OnlineStatusService;
import com.cw.im.redis.RedisManager;
import com.cw.im.server.handler.AuthHandler;
import com.cw.im.server.handler.ConnectionHandler;
import com.cw.im.server.handler.HeartbeatHandler;
import com.cw.im.server.handler.RouteHandler;
import com.cw.im.server.channel.ChannelManager;
import com.cw.im.server.consumer.PushMessageConsumer;
import com.cw.im.server.consumer.GroupMessagePushConsumer;
import com.cw.im.server.consumer.PublicBroadcastConsumer;
import com.cw.im.server.consumer.AckConsumer;
import com.cw.im.common.constants.KafkaTopics;
import com.cw.im.core.MessageDeduplicator;
import com.cw.im.core.MessagePersistenceStore;
import com.cw.im.core.MessageStatusTracker;
import com.cw.im.core.MessageRetryManager;
import com.cw.im.core.DeadLetterQueue;
import com.cw.im.core.AckTimeoutMonitor;
import com.cw.im.core.ReliabilityMetrics;
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
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;

/**
 * Netty IM 服务器 - 完整版本
 *
 * <p>集成完整的 Handler Pipeline，支持认证、心跳、路由等功能</p>
 *
 * <h3>Handler Pipeline 配置</h3>
 * <pre>
 * 1. IdleStateHandler（心跳检测，60秒读空闲）
 * 2. LengthFieldPrepender/FrameDecoder（拆包/粘包处理）
 * 3. IMMessageEncoder/IMMessageDecoder（编解码）
 * 4. ConnectionHandler（连接管理）
 * 5. AuthHandler（认证）
 * 6. HeartbeatHandler（心跳处理）
 * 7. RouteHandler（消息路由）
 * </pre>
 *
 * @author cw
 * @since 1.0.0
 */
@Slf4j
public class NettyIMServer {

    private static final int DEFAULT_PORT = 8080;

    // ==================== 配置参数 ====================

    private final int port;
    private final int bossThreads;
    private final int workerThreads;
    private final int soBacklog;
    private final int heartbeatTimeoutSeconds;

    // ==================== Netty 组件 ====================

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    // ==================== 业务组件 ====================

    private RedisManager redisManager;
    private OnlineStatusService onlineStatusService;
    private ChannelManager channelManager;
    private KafkaProducerManager kafkaProducer;
    private KafkaConsumerService kafkaConsumerService;
    private ConnectionHandler connectionHandler;
    private MessageDeduplicator messageDeduplicator;

    // ==================== 可靠性组件 ====================

    private MessagePersistenceStore messagePersistenceStore;
    private MessageStatusTracker messageStatusTracker;
    private MessageRetryManager messageRetryManager;
    private DeadLetterQueue deadLetterQueue;
    private AckTimeoutMonitor ackTimeoutMonitor;
    private ReliabilityMetrics reliabilityMetrics;

    // ==================== 构造函数 ====================

    /**
     * 默认构造函数
     */
    public NettyIMServer() {
        this(DEFAULT_PORT);
    }

    /**
     * 指定端口构造函数
     *
     * @param port 监听端口
     */
    public NettyIMServer(int port) {
        this(port, 1, 0, 128, 60);
    }

    /**
     * 完整参数构造函数
     *
     * @param port                    监听端口
     * @param bossThreads             Boss线程数
     * @param workerThreads           Worker线程数（0表示使用CPU核心数*2）
     * @param soBacklog               连接队列大小
     * @param heartbeatTimeoutSeconds 心跳超时时间（秒）
     */
    public NettyIMServer(int port, int bossThreads, int workerThreads, int soBacklog, int heartbeatTimeoutSeconds) {
        this.port = port;
        this.bossThreads = bossThreads;
        this.workerThreads = workerThreads;
        this.soBacklog = soBacklog;
        this.heartbeatTimeoutSeconds = heartbeatTimeoutSeconds;
    }

    /**
     * 启动 Netty 服务器
     */
    public void start() {
        log.info("========================================");
        log.info("Netty IM 服务器正在启动...");
        log.info("监听端口: {}", port);
        log.info("Boss 线程数: {}", bossThreads);
        log.info("Worker 线程数: {}", workerThreads == 0 ? "CPU*2" : workerThreads);
        log.info("心跳超时: {}s", heartbeatTimeoutSeconds);
        log.info("========================================");

        try {
            // 1. 初始化业务组件
            initBusinessComponents();

            // 2. 初始化 Netty 组件
            initNettyComponents();

            // 3. 配置 ServerBootstrap
            ServerBootstrap bootstrap = configureBootstrap();

            // 4. 绑定端口并启动
            ChannelFuture future = bootstrap.bind(port).sync();
            log.info("========================================");
            log.info("Netty IM 服务器启动成功!");
            log.info("监听端口: {}", port);
            log.info("网关ID: {}", channelManager.getGatewayId());
            log.info("========================================");

            // 5. 注册推送消息消费者（私聊）
            kafkaConsumerService.addListener(KafkaTopics.MSG_PUSH,
                    new PushMessageConsumer(channelManager, onlineStatusService));
            log.info("推送消息消费者已注册: topic={}", KafkaTopics.MSG_PUSH);

            // 6. 注册ACK消息消费者
            kafkaConsumerService.addListener(KafkaTopics.ACK, new AckConsumer());
            log.info("ACK消息消费者已注册: topic={}", KafkaTopics.ACK);

            // 7. 注册群聊消息推送消费者（可选）
            // kafkaConsumerService.addListener(KafkaTopics.GROUP_MSG_PUSH,
            //         new GroupMessagePushConsumer(channelManager, onlineStatusService));
            // log.info("群聊推送消息消费者已注册: topic={}", KafkaTopics.GROUP_MSG_PUSH);

            // 8. 注册公屏广播消费者（可选）
            // kafkaConsumerService.addListener(KafkaTopics.PUBLIC_BROADCAST,
            //         new PublicBroadcastConsumer(channelManager));
            // log.info("公屏广播消费者已注册: topic={}", KafkaTopics.PUBLIC_BROADCAST);

            // 9. 启动Kafka消费者服务
            kafkaConsumerService.start();
            log.info("Kafka 消费者服务已启动");

            // 10. 启动ACK超时监控器
            ackTimeoutMonitor.start();
            log.info("ACK 超时监控器已启动");

            // 11. 定期保存可靠性指标到Redis（每5分钟）
            ScheduledExecutorService metricsScheduler = Executors.newSingleThreadScheduledExecutor();
            metricsScheduler.scheduleAtFixedRate(
                    () -> {
                        try {
                            reliabilityMetrics.saveToRedis();
                            log.debug("可靠性指标已保存到Redis");
                        } catch (Exception e) {
                            log.error("保存可靠性指标异常", e);
                        }
                    },
                    5,
                    5,
                    TimeUnit.MINUTES
            );

            // 12. 等待服务器 Socket 关闭
            future.channel().closeFuture().sync();

        } catch (Exception e) {
            log.error("Netty 服务器启动异常", e);
            throw new RuntimeException("服务器启动失败", e);
        } finally {
            shutdown();
        }
    }

    /**
     * 初始化业务组件
     */
    private void initBusinessComponents() {
        log.info("正在初始化业务组件...");

        try {
            // 1. 初始化 Redis 管理器
            String redisHost = System.getenv().getOrDefault("REDIS_HOST", "192.168.215.2");
            int redisPort = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));
            redisManager = new RedisManager(redisHost, redisPort, "123456", 0);
            log.info("Redis 管理器初始化成功: {}:{}", redisHost, redisPort);

            // 2. 初始化在线状态服务
            onlineStatusService = new OnlineStatusService(redisManager, 30);
            onlineStatusService.init();
            log.info("在线状态服务启动成功");

            // 3. 初始化 Channel 管理器
            channelManager = new ChannelManager(onlineStatusService);
            log.info("Channel 管理器初始化成功, gatewayId={}", channelManager.getGatewayId());

            // 4. 初始化 Kafka 生产者
            String kafkaServers = System.getenv().getOrDefault("KAFKA_SERVERS", "192.168.215.2:9092");
            kafkaProducer = new KafkaProducerManager(kafkaServers);
            log.info("Kafka 生产者初始化成功: {}", kafkaServers);

            // 5. 初始化 Kafka 消费者服务
            kafkaConsumerService = new KafkaConsumerService(kafkaServers);
            log.info("Kafka 消费者服务初始化成功");

            // 6. 初始化连接管理 Handler
            connectionHandler = new ConnectionHandler(channelManager);
            log.info("连接管理 Handler 初始化成功");

            // 7. 初始化消息去重器
            messageDeduplicator = new MessageDeduplicator(redisManager);
            log.info("消息去重器初始化成功");

            // 8. 初始化消息持久化存储
            messagePersistenceStore = new MessagePersistenceStore(redisManager);
            log.info("消息持久化存储初始化成功");

            // 9. 初始化消息状态跟踪器
            messageStatusTracker = new MessageStatusTracker(redisManager);
            log.info("消息状态跟踪器初始化成功");

            // 10. 初始化死信队列
            deadLetterQueue = new DeadLetterQueue(redisManager);
            log.info("死信队列初始化成功");

            // 11. 初始化消息重试管理器
            messageRetryManager = new MessageRetryManager(
                    redisManager,
                    messageStatusTracker,
                    messagePersistenceStore,
                    deadLetterQueue
            );
            messageRetryManager.start();
            log.info("消息重试管理器初始化并启动成功");

            // 12. 初始化ACK超时监控器
            ackTimeoutMonitor = new AckTimeoutMonitor(
                    redisManager,
                    messageStatusTracker,
                    messagePersistenceStore,
                    messageRetryManager
            );
            log.info("ACK超时监控器初始化成功");

            // 13. 初始化可靠性指标
            reliabilityMetrics = new ReliabilityMetrics(redisManager);
            log.info("可靠性指标监控初始化成功");

            log.info("业务组件初始化完成");

        } catch (Exception e) {
            log.error("初始化业务组件失败", e);
            throw new RuntimeException("业务组件初始化失败", e);
        }
    }

    /**
     * 初始化 Netty 组件
     */
    private void initNettyComponents() {
        log.info("正在初始化 Netty 组件...");

        // Boss 线程组：处理客户端连接
        bossGroup = new NioEventLoopGroup(bossThreads);
        log.info("Boss 线程组已创建: threads={}", bossThreads);

        // Worker 线程组：处理 I/O 操作
        int workerCount = workerThreads == 0 ? Runtime.getRuntime().availableProcessors() * 2 : workerThreads;
        workerGroup = new NioEventLoopGroup(workerCount);
        log.info("Worker 线程组已创建: threads={}", workerCount);

        log.info("Netty 组件初始化完成");
    }

    /**
     * 配置 ServerBootstrap
     *
     * @return ServerBootstrap
     */
    private ServerBootstrap configureBootstrap() {
        ServerBootstrap bootstrap = new ServerBootstrap();

        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, soBacklog)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        log.info("新连接接入: {}", ch.id());

                        // 配置 Handler Pipeline
                        configurePipeline(ch);
                    }
                });

        return bootstrap;
    }

    /**
     * 配置 Channel Pipeline
     *
     * @param ch SocketChannel
     */
    private void configurePipeline(SocketChannel ch) {
        // 1. 心跳检测（60秒读空闲）
        ch.pipeline().addLast("idleStateHandler", new IdleStateHandler(
                heartbeatTimeoutSeconds, 0, 0, TimeUnit.SECONDS));

        // 2. 拆包/粘包处理
        ch.pipeline().addLast("frameDecoder", new LengthFieldBasedFrameDecoder(
                65536,   // 最大帧长度
                0,       // 长度字段偏移量
                4,       // 长度字段长度
                0,       // 长度字段后的字节数
                4));     // 需要剥离的字节数
        ch.pipeline().addLast("frameEncoder", new LengthFieldPrepender(4));

        // 3. 编解码
        ch.pipeline().addLast("decoder", new IMMessageDecoder());
        ch.pipeline().addLast("encoder", new IMMessageEncoder());

        // 4. 连接管理
        ch.pipeline().addLast("connectionHandler", connectionHandler);

        // 5. 认证
        ch.pipeline().addLast("authHandler", new AuthHandler(
                onlineStatusService, channelManager.getGatewayId()));

        // 6. 心跳处理
        ch.pipeline().addLast("heartbeatHandler", new HeartbeatHandler(onlineStatusService));

        // 7. 路由处理（增强版，集成消息去重和专门Handler）
        ch.pipeline().addLast("routeHandler", new RouteHandler(
                channelManager, onlineStatusService, kafkaProducer,
                messageDeduplicator, channelManager.getGatewayId()));

        log.debug("Pipeline 配置完成: channelId={}", ch.id());
    }

    /**
     * 优雅关闭服务器
     */
    public void shutdown() {
        log.info("========================================");
        log.info("正在关闭 Netty IM 服务器...");

        try {
            // 1. 停止ACK超时监控器
            if (ackTimeoutMonitor != null) {
                ackTimeoutMonitor.stop();
                log.info("ACK 超时监控器已停止");
            }

            // 2. 停止消息重试管理器
            if (messageRetryManager != null) {
                messageRetryManager.stop();
                log.info("消息重试管理器已停止");
            }

            // 3. 保存最终可靠性指标
            if (reliabilityMetrics != null) {
                reliabilityMetrics.saveToRedis();
                log.info("可靠性指标已保存");
            }

            // 4. 停止Kafka消费者服务
            if (kafkaConsumerService != null) {
                kafkaConsumerService.stop();
                log.info("Kafka 消费者服务已停止");
            }

            // 5. 关闭在线状态服务
            if (onlineStatusService != null) {
                onlineStatusService.shutdown();
                log.info("在线状态服务已关闭");
            }

            // 6. 关闭 Channel 管理器
            if (channelManager != null) {
                channelManager.shutdown();
                log.info("Channel 管理器已关闭");
            }

            // 7. 关闭 Kafka 生产者
            if (kafkaProducer != null) {
                kafkaProducer.close();
                log.info("Kafka 生产者已关闭");
            }

            // 8. 关闭 Redis 连接
            if (redisManager != null) {
                redisManager.close();
                log.info("Redis 连接已关闭");
            }

            // 9. 关闭 Netty
            if (bossGroup != null) {
                bossGroup.shutdownGracefully();
            }
            if (workerGroup != null) {
                workerGroup.shutdownGracefully();
            }
            log.info("Netty 已关闭");

            // 10. 输出统计信息
            outputStatistics();

            log.info("Netty IM 服务器已完全关闭");
            log.info("========================================");

        } catch (Exception e) {
            log.error("关闭服务器时发生异常", e);
        }
    }

    /**
     * 获取服务器统计信息
     *
     * @return 统计信息字符串
     */
    public String getStats() {
        StringBuilder sb = new StringBuilder();
        sb.append("NettyIMServer{\n");
        sb.append("  port=").append(port).append("\n");
        sb.append("  gatewayId=").append(channelManager != null ? channelManager.getGatewayId() : "N/A").append("\n");
        if (connectionHandler != null) {
            sb.append("  ").append(connectionHandler.getStats().replace("ConnectionHandler{", "").replace("}", "")).append("\n");
        }
        if (channelManager != null) {
            sb.append("  onlineUsers=").append(channelManager.getOnlineUserCount()).append("\n");
        }
        if (messageDeduplicator != null) {
            sb.append("  ").append(messageDeduplicator.getStats()).append("\n");
        }
        if (messagePersistenceStore != null) {
            sb.append("  ").append(messagePersistenceStore.getStats()).append("\n");
        }
        if (messageStatusTracker != null) {
            sb.append("  ").append(messageStatusTracker.getStats()).append("\n");
        }
        if (messageRetryManager != null) {
            sb.append("  ").append(messageRetryManager.getStats()).append("\n");
        }
        if (deadLetterQueue != null) {
            sb.append("  ").append(deadLetterQueue.getStats()).append("\n");
        }
        if (ackTimeoutMonitor != null) {
            sb.append("  ").append(ackTimeoutMonitor.getStats()).append("\n");
        }
        if (reliabilityMetrics != null) {
            sb.append("  ").append(reliabilityMetrics.getStats()).append("\n");
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * 输出详细统计信息
     */
    private void outputStatistics() {
        log.info("========================================");
        log.info("可靠性组件统计信息:");

        if (connectionHandler != null) {
            log.info("连接统计: {}", connectionHandler.getStats());
        }

        if (messageDeduplicator != null) {
            log.info("消息去重统计: {}", messageDeduplicator.getStats());
        }

        if (messagePersistenceStore != null) {
            log.info("消息持久化统计: {}", messagePersistenceStore.getStats());
        }

        if (messageStatusTracker != null) {
            log.info("消息状态跟踪统计: {}", messageStatusTracker.getStats());
        }

        if (messageRetryManager != null) {
            log.info("消息重试统计: {}", messageRetryManager.getStats());
        }

        if (deadLetterQueue != null) {
            log.info("死信队列统计: {}", deadLetterQueue.getStats());
        }

        if (ackTimeoutMonitor != null) {
            log.info("ACK超时监控统计: {}", ackTimeoutMonitor.getStats());
        }

        if (reliabilityMetrics != null) {
            log.info("可靠性指标: {}", reliabilityMetrics.getStats());
        }

        if (kafkaConsumerService != null) {
            log.info("Kafka消费统计: {}", kafkaConsumerService.getStats());
        }

        log.info("========================================");
    }

    // ==================== Getter方法 ====================

    public MessagePersistenceStore getMessagePersistenceStore() {
        return messagePersistenceStore;
    }

    public MessageStatusTracker getMessageStatusTracker() {
        return messageStatusTracker;
    }

    public MessageRetryManager getMessageRetryManager() {
        return messageRetryManager;
    }

    public DeadLetterQueue getDeadLetterQueue() {
        return deadLetterQueue;
    }

    public AckTimeoutMonitor getAckTimeoutMonitor() {
        return ackTimeoutMonitor;
    }

    public ReliabilityMetrics getReliabilityMetrics() {
        return reliabilityMetrics;
    }

    /**
     * 主入口
     */
    public static void main(String[] args) {
        // 从环境变量读取配置
        int port = Integer.parseInt(System.getenv().getOrDefault("SERVER_PORT", "8080"));

        NettyIMServer server = new NettyIMServer(port);

        // 添加关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("收到关闭信号，正在优雅关闭...");
            server.shutdown();
        }));

        try {
            server.start();
        } catch (Exception e) {
            log.error("服务器启动失败", e);
            System.exit(1);
        }
    }
}
