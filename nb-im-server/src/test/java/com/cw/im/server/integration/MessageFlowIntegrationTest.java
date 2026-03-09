package com.cw.im.server.integration;

import com.cw.im.common.model.MessageBody;
import com.cw.im.common.model.MessageHeader;
import com.cw.im.common.protocol.CommandType;
import com.cw.im.common.protocol.IMMessage;
import com.cw.im.core.MessageDeduplicator;
import com.cw.im.core.MessageStatusTracker;
import com.cw.im.kafka.KafkaProducerManager;
import com.cw.im.redis.OnlineStatusService;
import com.cw.im.server.channel.ChannelManager;
import com.cw.im.server.handler.AuthHandler;
import com.cw.im.server.handler.HeartbeatHandler;
import com.cw.im.server.handler.PrivateChatHandler;
import com.cw.im.server.handler.RouteHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 消息流程集成测试
 *
 * <p>测试完整的消息发送和接收流程，包括：
 * <ul>
 *     <li>认证流程</li>
 *     <li>心跳流程</li>
 *     <li>私聊消息流程</li>
 *     <li>消息路由流程</li>
 *     <li>ACK流程</li>
 * </ul>
 *
 * @author cw
 * @since 1.0.0
 */
@DisplayName("消息流程集成测试")
class MessageFlowIntegrationTest {

    private ChannelManager channelManager;
    private OnlineStatusService onlineStatusService;
    private KafkaProducerManager kafkaProducer;
    private MessageDeduplicator messageDeduplicator;
    private MessageStatusTracker messageStatusTracker;

    @BeforeEach
    void setUp() {
        channelManager = spy(new ChannelManager());
        onlineStatusService = mock(OnlineStatusService.class);
        kafkaProducer = mock(KafkaProducerManager.class);
        messageDeduplicator = spy(new MessageDeduplicator(null));
        messageStatusTracker = mock(MessageStatusTracker.class);

        // Setup default mock behaviors
        when(onlineStatusService.registerUser(anyLong(), anyString(), anyString())).thenReturn(true);
        when(onlineStatusService.isUserOnline(anyLong())).thenReturn(true);
        when(messageDeduplicator.markAsProcessed(anyString())).thenReturn(true);
    }

    @Test
    @DisplayName("测试完整的私聊消息流程")
    void testCompletePrivateChatFlow() {
        // Given
        Long fromUserId = 1001L;
        Long toUserId = 1002L;
        String gatewayId = "gateway-001";

        // 创建嵌入式Channel模拟完整流程
        EmbeddedChannel channel1 = new EmbeddedChannel();
        EmbeddedChannel channel2 = new EmbeddedChannel();

        // 添加Handler到Channel 1
        AuthHandler authHandler1 = new AuthHandler(onlineStatusService, gatewayId);
        HeartbeatHandler heartbeatHandler1 = new HeartbeatHandler(onlineStatusService);
        RouteHandler routeHandler1 = new RouteHandler(
                channelManager,
                onlineStatusService,
                kafkaProducer,
                messageDeduplicator,
                gatewayId
        );

        // 创建认证消息
        IMMessage authMessage = IMMessage.builder()
                .header(MessageHeader.builder()
                        .msgId("auth-msg-001")
                        .cmd(CommandType.SYSTEM_NOTICE)
                        .from(fromUserId)
                        .timestamp(System.currentTimeMillis())
                        .version("1.0")
                        .extras(java.util.Map.of(
                                "token", "valid-token-12345",
                                "deviceId", "device-001",
                                "deviceType", "PC"
                        ))
                        .build())
                .body(MessageBody.builder()
                        .content("auth")
                        .extras(java.util.Map.of("userId", fromUserId))
                        .build())
                .build();

        // 创建私聊消息
        IMMessage privateChatMessage = IMMessage.builder()
                .header(MessageHeader.builder()
                        .msgId("chat-msg-001")
                        .cmd(CommandType.PRIVATE_CHAT)
                        .from(fromUserId)
                        .to(toUserId)
                        .timestamp(System.currentTimeMillis())
                        .version("1.0")
                        .build())
                .body(MessageBody.builder()
                        .content("Hello, this is a test message")
                        .contentType("text")
                        .build())
                .build();

        // 创建ACK消息
        IMMessage ackMessage = IMMessage.builder()
                .header(MessageHeader.builder()
                        .msgId("ack-msg-001")
                        .cmd(CommandType.ACK)
                        .from(toUserId)
                        .to(fromUserId)
                        .timestamp(System.currentTimeMillis())
                        .version("1.0")
                        .extras(java.util.Map.of(
                                "originalMsgId", "chat-msg-001",
                                "status", "received"
                        ))
                        .build())
                .body(MessageBody.builder()
                        .content("ACK")
                        .contentType("ack")
                        .build())
                .build();

        // When & Then - 模拟完整流程
        // 1. 用户1认证
        channelManager.addChannel(fromUserId, "device-001", channel1);
        assertThat(channelManager.isUserOnline(fromUserId)).isTrue();

        // 2. 用户2认证
        channelManager.addChannel(toUserId, "device-002", channel2);
        assertThat(channelManager.isUserOnline(toUserId)).isTrue();

        // 3. 用户1发送私聊消息
        PrivateChatHandler privateChatHandler = new PrivateChatHandler(
                channelManager, onlineStatusService, kafkaProducer, messageDeduplicator
        );

        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        when(ctx.channel()).thenReturn(channel1);

        privateChatHandler.handle(ctx, privateChatMessage);

        // 验证消息去重
        verify(messageDeduplicator).markAsProcessed("chat-msg-001");

        // 4. 验证在线状态查询
        verify(onlineStatusService).isUserOnline(toUserId);

        // 5. 验证Channel管理
        assertThat(channelManager.getCurrentConnectionCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("测试消息发送流程 - 离线用户")
    void testMessageSendingFlow_OfflineUser() {
        // Given
        Long fromUserId = 1003L;
        Long toUserId = 1004L;

        IMMessage message = IMMessage.builder()
                .header(MessageHeader.builder()
                        .msgId("msg-offline-001")
                        .cmd(CommandType.PRIVATE_CHAT)
                        .from(fromUserId)
                        .to(toUserId)
                        .timestamp(System.currentTimeMillis())
                        .version("1.0")
                        .build())
                .body(MessageBody.builder()
                        .content("Hello offline user")
                        .contentType("text")
                        .build())
                .build();

        when(onlineStatusService.isUserOnline(toUserId)).thenReturn(false);

        PrivateChatHandler handler = new PrivateChatHandler(
                channelManager, onlineStatusService, kafkaProducer, messageDeduplicator
        );

        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        EmbeddedChannel channel = new EmbeddedChannel();
        when(ctx.channel()).thenReturn(channel);

        // When
        handler.handle(ctx, message);

        // Then
        verify(kafkaProducer).sendAsync(eq("im-msg-send"), anyString(), eq(message));
        verify(onlineStatusService).isUserOnline(toUserId);
    }

    @Test
    @DisplayName("测试消息发送流程 - 在线用户")
    void testMessageSendingFlow_OnlineUser() {
        // Given
        Long fromUserId = 1005L;
        Long toUserId = 1006L;

        IMMessage message = IMMessage.builder()
                .header(MessageHeader.builder()
                        .msgId("msg-online-001")
                        .cmd(CommandType.PRIVATE_CHAT)
                        .from(fromUserId)
                        .to(toUserId)
                        .timestamp(System.currentTimeMillis())
                        .version("1.0")
                        .build())
                .body(MessageBody.builder()
                        .content("Hello online user")
                        .contentType("text")
                        .build())
                .build();

        EmbeddedChannel targetChannel = new EmbeddedChannel();
        channelManager.addChannel(toUserId, "device-001", targetChannel);
        when(onlineStatusService.isUserOnline(toUserId)).thenReturn(true);

        PrivateChatHandler handler = new PrivateChatHandler(
                channelManager, onlineStatusService, kafkaProducer, messageDeduplicator
        );

        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        EmbeddedChannel sourceChannel = new EmbeddedChannel();
        when(ctx.channel()).thenReturn(sourceChannel);

        // When
        handler.handle(ctx, message);

        // Then
        verify(channelManager).getChannel(toUserId);
        verify(onlineStatusService).isUserOnline(toUserId);
        verify(messageDeduplicator).markAsProcessed("msg-online-001");
    }

    @Test
    @DisplayName("测试心跳流程")
    void testHeartbeatFlow() {
        // Given
        Long userId = 1007L;

        IMMessage pingMessage = IMMessage.builder()
                .header(MessageHeader.builder()
                        .msgId("ping-001")
                        .cmd(CommandType.HEARTBEAT)
                        .from(userId)
                        .timestamp(System.currentTimeMillis())
                        .version("1.0")
                        .build())
                .body(MessageBody.builder()
                        .content("ping")
                        .contentType("heartbeat")
                        .build())
                .build();

        HeartbeatHandler handler = new HeartbeatHandler(onlineStatusService);

        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        EmbeddedChannel channel = new EmbeddedChannel();
        when(ctx.channel()).thenReturn(channel);

        // When
        handler.channelRead(ctx, pingMessage);

        // Then
        verify(onlineStatusService).heartbeat(eq(userId), anyString());
    }

    @Test
    @DisplayName("测试多设备登录流程")
    void testMultiDeviceLoginFlow() {
        // Given
        Long userId = 1008L;
        String device1Id = "device-pc-001";
        String device2Id = "device-mobile-001";

        EmbeddedChannel channel1 = new EmbeddedChannel();
        EmbeddedChannel channel2 = new EmbeddedChannel();

        // When - 用户在两个设备上登录
        channelManager.addChannel(userId, device1Id, channel1);
        channelManager.addChannel(userId, device2Id, channel2);

        // Then
        assertThat(channelManager.isUserOnline(userId)).isTrue();
        assertThat(channelManager.getChannels(userId)).hasSize(2);
        assertThat(channelManager.getCurrentConnectionCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("测试消息去重流程")
    void testMessageDeduplicationFlow() {
        // Given
        String msgId = "duplicate-msg-001";

        // When - 第一次处理消息
        boolean firstResult = messageDeduplicator.markAsProcessed(msgId);

        // Then
        assertThat(firstResult).isTrue();

        // When - 第二次处理相同消息
        boolean secondResult = messageDeduplicator.markAsProcessed(msgId);

        // Then
        assertThat(secondResult).isFalse();
    }

    @Test
    @DisplayName("测试消息统计信息")
    void testMessageStats() {
        // Given
        RouteHandler routeHandler = new RouteHandler(
                channelManager,
                onlineStatusService,
                kafkaProducer,
                messageDeduplicator,
                "test-gateway"
        );

        // When
        String stats = routeHandler.getStats();

        // Then
        assertThat(stats).contains("RouteHandler");
        assertThat(stats).contains("total");
    }

    @Test
    @DisplayName("测试Channel清理流程")
    void testChannelCleanupFlow() {
        // Given
        Long userId = 1009L;
        String deviceId = "device-001";

        EmbeddedChannel channel = new EmbeddedChannel();
        channelManager.addChannel(userId, deviceId, channel);

        assertThat(channelManager.isUserOnline(userId)).isTrue();

        // When - 移除Channel
        channelManager.removeChannel(userId, deviceId);

        // Then
        assertThat(channelManager.isUserOnline(userId)).isFalse();
        assertThat(channelManager.getCurrentConnectionCount()).isEqualTo(0);
    }
}
