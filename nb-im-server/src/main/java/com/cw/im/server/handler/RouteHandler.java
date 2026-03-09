package com.cw.im.server.handler;

import com.cw.im.common.protocol.CommandType;
import com.cw.im.common.protocol.IMMessage;
import com.cw.im.core.MessageDeduplicator;
import com.cw.im.kafka.KafkaProducerManager;
import com.cw.im.redis.OnlineStatusService;
import com.cw.im.server.channel.ChannelManager;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * 路由 Handler（增强版）
 *
 * <p>负责根据消息类型进行路由分发，是消息处理的核心 Handler</p>
 *
 * <h3>消息路由策略</h3>
 * <ul>
 *     <li>私聊消息：分发到 PrivateChatHandler 处理</li>
 *     <li>群聊消息：分发到 GroupChatHandler 处理</li>
 *     <li>公屏消息：分发到 PublicChatHandler 处理</li>
 *     <li>ACK消息：分发到 AckHandler 处理</li>
 *     <li>心跳消息：已在 HeartbeatHandler 中处理，这里忽略</li>
 * </ul>
 *
 * <h3>路由流程</h3>
 * <pre>
 * 1. 接收消息
 * 2. 验证消息有效性
 * 3. 检查消息是否重复（消息去重）
 * 4. 根据消息类型分发到对应的Handler
 * 5. 记录处理统计
 * </pre>
 *
 * @author cw
 * @since 1.0.0
 */
@Slf4j
public class RouteHandler extends SimpleChannelInboundHandler<IMMessage> {

    /**
     * Channel 管理器
     */
    private final ChannelManager channelManager;

    /**
     * 在线状态服务
     */
    private final OnlineStatusService onlineStatusService;

    /**
     * Kafka 生产者
     */
    private final KafkaProducerManager kafkaProducer;

    /**
     * 消息去重器
     */
    private final MessageDeduplicator messageDeduplicator;

    /**
     * 网关ID
     */
    private final String gatewayId;

    /**
     * 专门的消息Handler
     */
    private final PrivateChatHandler privateChatHandler;
    private final GroupChatHandler groupChatHandler;
    private final PublicChatHandler publicChatHandler;
    private final AckHandler ackHandler;

    /**
     * 路由统计
     */
    private long totalMessageCount = 0;
    private long privateChatCount = 0;
    private long groupChatCount = 0;
    private long publicChatCount = 0;
    private long ackCount = 0;
    private long invalidMessageCount = 0;
    private long duplicateMessageCount = 0;

    /**
     * 构造函数（完整版）
     *
     * @param channelManager         Channel管理器
     * @param onlineStatusService    在线状态服务
     * @param kafkaProducer          Kafka生产者
     * @param messageDeduplicator    消息去重器
     * @param gatewayId              网关ID
     */
    public RouteHandler(ChannelManager channelManager,
                        OnlineStatusService onlineStatusService,
                        KafkaProducerManager kafkaProducer,
                        MessageDeduplicator messageDeduplicator,
                        String gatewayId) {
        this.channelManager = channelManager;
        this.onlineStatusService = onlineStatusService;
        this.kafkaProducer = kafkaProducer;
        this.messageDeduplicator = messageDeduplicator;
        this.gatewayId = gatewayId;

        // 初始化专门的消息Handler
        this.privateChatHandler = new PrivateChatHandler(
                channelManager, onlineStatusService, kafkaProducer, messageDeduplicator);
        this.groupChatHandler = new GroupChatHandler(
                channelManager, kafkaProducer, messageDeduplicator);
        this.publicChatHandler = new PublicChatHandler(
                channelManager, kafkaProducer, messageDeduplicator);
        this.ackHandler = new AckHandler(
                kafkaProducer, messageDeduplicator, gatewayId);

        log.info("RouteHandler 初始化完成: gatewayId={}", gatewayId);
    }

    /**
     * 构造函数（使用默认网关ID）
     *
     * @param channelManager         Channel管理器
     * @param onlineStatusService    在线状态服务
     * @param kafkaProducer          Kafka生产者
     * @param messageDeduplicator    消息去重器
     */
    public RouteHandler(ChannelManager channelManager,
                        OnlineStatusService onlineStatusService,
                        KafkaProducerManager kafkaProducer,
                        MessageDeduplicator messageDeduplicator) {
        this(channelManager, onlineStatusService, kafkaProducer,
                messageDeduplicator, "default-gateway");
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, IMMessage msg) throws Exception {
        totalMessageCount++;

        try {
            // 1. 验证消息有效性
            if (!msg.isValid()) {
                log.warn("消息无效: msgId={}, cmd={}", msg.getMsgId(), msg.getCmd());
                invalidMessageCount++;
                return;
            }

            // 2. 记录接收消息
            log.info("收到消息: msgId={}, cmd={}, from={}, to={}, content={}",
                    msg.getMsgId(), msg.getCmd(), msg.getFrom(), msg.getTo(), msg.getContent());

            // 3. 检查消息是否重复（全局去重）
            String msgId = msg.getMsgId();
            if (messageDeduplicator.isProcessed(msgId)) {
                log.warn("消息重复，跳过处理: msgId={}, cmd={}", msgId, msg.getCmd());
                duplicateMessageCount++;
                return;
            }

            // 4. 根据消息类型分发到对应的Handler
            switch (msg.getCmd()) {
                case PRIVATE_CHAT:
                    privateChatHandler.handle(ctx, msg);
                    privateChatCount++;
                    break;

                case GROUP_CHAT:
                    groupChatHandler.handle(ctx, msg);
                    groupChatCount++;
                    break;

                case PUBLIC_CHAT:
                    publicChatHandler.handle(ctx, msg);
                    publicChatCount++;
                    break;

                case ACK:
                    ackHandler.handle(ctx, msg);
                    ackCount++;
                    break;

                case HEARTBEAT:
                    // 心跳消息已在 HeartbeatHandler 中处理
                    log.debug("忽略心跳消息: msgId={}", msgId);
                    break;

                case SYSTEM_NOTICE:
                    // 系统通知，通常不需要处理
                    log.debug("忽略系统通知: msgId={}", msgId);
                    break;

                default:
                    log.warn("未知消息类型: cmd={}, msgId={}", msg.getCmd(), msgId);
                    invalidMessageCount++;
                    break;
            }

        } catch (Exception e) {
            log.error("处理消息异常: msgId={}, cmd={}", msg.getMsgId(), msg.getCmd(), e);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("路由Handler异常: remoteAddress={}", ctx.channel().remoteAddress(), cause);
        ctx.close();
    }

    // ==================== 统计方法 ====================

    /**
     * 获取统计信息
     *
     * @return 统计信息字符串
     */
    public String getStats() {
        return String.format(
                "RouteHandler{total=%d, privateChat=%d(%.2f%%), groupChat=%d(%.2f%%), " +
                        "publicChat=%d(%.2f%%), ack=%d(%.2f%%), invalid=%d, duplicate=%d(%.2f%%)}",
                totalMessageCount,
                privateChatCount, getRate(privateChatCount),
                groupChatCount, getRate(groupChatCount),
                publicChatCount, getRate(publicChatCount),
                ackCount, getRate(ackCount),
                invalidMessageCount,
                duplicateMessageCount, getRate(duplicateMessageCount)
        );
    }

    /**
     * 计算百分比
     *
     * @param count 计数
     * @return 百分比
     */
    private double getRate(long count) {
        if (totalMessageCount == 0) {
            return 0.0;
        }
        return (double) count / totalMessageCount * 100;
    }

    /**
     * 重置统计信息
     */
    public void resetStats() {
        totalMessageCount = 0;
        privateChatCount = 0;
        groupChatCount = 0;
        publicChatCount = 0;
        ackCount = 0;
        invalidMessageCount = 0;
        duplicateMessageCount = 0;
        log.info("路由消息统计信息已重置");
    }

    /**
     * 获取所有Handler的统计信息
     *
     * @return 完整统计信息字符串
     */
    public String getAllHandlerStats() {
        StringBuilder sb = new StringBuilder();
        sb.append("========================================\n");
        sb.append("RouteHandler 统计信息:\n");
        sb.append(getStats()).append("\n");
        sb.append("========================================\n");
        sb.append("PrivateChatHandler 统计信息:\n");
        sb.append(privateChatHandler.getStats()).append("\n");
        sb.append("========================================\n");
        sb.append("GroupChatHandler 统计信息:\n");
        sb.append(groupChatHandler.getStats()).append("\n");
        sb.append("========================================\n");
        sb.append("PublicChatHandler 统计信息:\n");
        sb.append(publicChatHandler.getStats()).append("\n");
        sb.append("========================================\n");
        sb.append("AckHandler 统计信息:\n");
        sb.append(ackHandler.getStats()).append("\n");
        sb.append("========================================\n");
        sb.append("MessageDeduplicator 统计信息:\n");
        sb.append(messageDeduplicator.getStats()).append("\n");
        sb.append("========================================");
        return sb.toString();
    }

    /**
     * 重置所有Handler的统计信息
     */
    public void resetAllHandlerStats() {
        resetStats();
        privateChatHandler.resetStats();
        groupChatHandler.resetStats();
        publicChatHandler.resetStats();
        ackHandler.resetStats();
        messageDeduplicator.resetStats();
        log.info("所有Handler的统计信息已重置");
    }

    // ==================== Getter 方法 ====================

    public PrivateChatHandler getPrivateChatHandler() {
        return privateChatHandler;
    }

    public GroupChatHandler getGroupChatHandler() {
        return groupChatHandler;
    }

    public PublicChatHandler getPublicChatHandler() {
        return publicChatHandler;
    }

    public AckHandler getAckHandler() {
        return ackHandler;
    }

    public MessageDeduplicator getMessageDeduplicator() {
        return messageDeduplicator;
    }
}
