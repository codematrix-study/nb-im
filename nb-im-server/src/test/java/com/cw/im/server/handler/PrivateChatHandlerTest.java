package com.cw.im.server.handler;

import com.cw.im.common.model.MessageBody;
import com.cw.im.common.model.MessageHeader;
import com.cw.im.common.protocol.CommandType;
import com.cw.im.common.protocol.IMMessage;
import com.cw.im.core.MessageDeduplicator;
import com.cw.im.kafka.KafkaProducerManager;
import com.cw.im.redis.OnlineStatusService;
import com.cw.im.server.channel.ChannelManager;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PrivateChatHandler 单元测试
 *
 * @author cw
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PrivateChatHandler 单元测试")
class PrivateChatHandlerTest {

    @Mock
    private ChannelManager channelManager;

    @Mock
    private OnlineStatusService onlineStatusService;

    @Mock
    private KafkaProducerManager kafkaProducer;

    @Mock
    private MessageDeduplicator messageDeduplicator;

    @Mock
    private ChannelHandlerContext ctx;

    @Mock
    private Channel channel;

    private PrivateChatHandler privateChatHandler;

    @BeforeEach
    void setUp() {
        privateChatHandler = new PrivateChatHandler(
                channelManager,
                onlineStatusService,
                kafkaProducer,
                messageDeduplicator
        );

        when(ctx.channel()).thenReturn(channel);
        when(channel.remoteAddress()).thenReturn("127.0.0.1:8080");
    }

    @Test
    @DisplayName("测试处理私聊消息 - 目标用户在线且在同一网关")
    void testHandlePrivateChat_TargetOnlineInSameGateway() {
        // Given
        Long fromUserId = 1001L;
        Long toUserId = 1002L;

        IMMessage message = createPrivateChatMessage(fromUserId, toUserId, "Hello");

        Channel targetChannel = mock(Channel.class);
        when(targetChannel.isActive()).thenReturn(true);
        when(channelManager.getChannel(toUserId)).thenReturn(targetChannel);
        when(onlineStatusService.isUserOnline(toUserId)).thenReturn(true);
        when(messageDeduplicator.markAsProcessed(anyString())).thenReturn(true);
        when(targetChannel.writeAndFlush(any(IMMessage.class))).thenReturn(io.netty.channel.ChannelFuture.SUCCEEDEDfuture);

        // When
        privateChatHandler.handle(ctx, message);

        // Then
        verify(targetChannel).writeAndFlush(message);
        verify(kafkaProducer, never()).sendAsync(anyString(), anyString(), any(IMMessage.class));
    }

    @Test
    @DisplayName("测试处理私聊消息 - 目标用户在线但在不同网关")
    void testHandlePrivateChat_TargetOnlineInDifferentGateway() {
        // Given
        Long fromUserId = 1001L;
        Long toUserId = 1002L;

        IMMessage message = createPrivateChatMessage(fromUserId, toUserId, "Hello");

        when(channelManager.getChannel(toUserId)).thenReturn(null);
        when(onlineStatusService.isUserOnline(toUserId)).thenReturn(true);
        when(messageDeduplicator.markAsProcessed(anyString())).thenReturn(true);

        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        when(kafkaProducer.sendAsync(anyString(), anyString(), any(IMMessage.class)))
                .thenReturn(future);

        // When
        privateChatHandler.handle(ctx, message);

        // Then
        verify(kafkaProducer).sendAsync(eq("im-msg-push"), anyString(), eq(message));
    }

    @Test
    @DisplayName("测试处理私聊消息 - 目标用户离线")
    void testHandlePrivateChat_TargetOffline() {
        // Given
        Long fromUserId = 1001L;
        Long toUserId = 1002L;

        IMMessage message = createPrivateChatMessage(fromUserId, toUserId, "Hello");

        when(channelManager.getChannel(toUserId)).thenReturn(null);
        when(onlineStatusService.isUserOnline(toUserId)).thenReturn(false);
        when(messageDeduplicator.markAsProcessed(anyString())).thenReturn(true);

        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        when(kafkaProducer.sendAsync(anyString(), anyString(), any(IMMessage.class)))
                .thenReturn(future);

        // When
        privateChatHandler.handle(ctx, message);

        // Then
        verify(kafkaProducer).sendAsync(eq("im-msg-send"), anyString(), eq(message));
    }

    @Test
    @DisplayName("测试处理私聊消息 - 发送到Kafka失败")
    void testHandlePrivateChat_KafkaSendFailure() {
        // Given
        Long fromUserId = 1001L;
        Long toUserId = 1002L;

        IMMessage message = createPrivateChatMessage(fromUserId, toUserId, "Hello");

        when(channelManager.getChannel(toUserId)).thenReturn(null);
        when(onlineStatusService.isUserOnline(toUserId)).thenReturn(false);
        when(messageDeduplicator.markAsProcessed(anyString())).thenReturn(true);

        CompletableFuture<Void> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka error"));
        when(kafkaProducer.sendAsync(anyString(), anyString(), any(IMMessage.class)))
                .thenReturn(failedFuture);

        // When
        privateChatHandler.handle(ctx, message);

        // Then
        verify(kafkaProducer).sendAsync(anyString(), anyString(), eq(message));
    }

    @Test
    @DisplayName("测试处理私聊消息 - 消息去重失败")
    void testHandlePrivateChat_MessageDeduplicationFailure() {
        // Given
        Long fromUserId = 1001L;
        Long toUserId = 1002L;

        IMMessage message = createPrivateChatMessage(fromUserId, toUserId, "Hello");

        when(messageDeduplicator.markAsProcessed(anyString())).thenReturn(false);

        // When
        privateChatHandler.handle(ctx, message);

        // Then
        verify(channelManager, never()).getChannel(anyLong());
        verify(onlineStatusService, never()).isUserOnline(anyLong());
        verify(kafkaProducer, never()).sendAsync(anyString(), anyString(), any(IMMessage.class));
    }

    @Test
    @DisplayName("测试处理私聊消息 - 目标Channel不活跃")
    void testHandlePrivateChat_TargetChannelNotActive() {
        // Given
        Long fromUserId = 1001L;
        Long toUserId = 1002L;

        IMMessage message = createPrivateChatMessage(fromUserId, toUserId, "Hello");

        Channel targetChannel = mock(Channel.class);
        when(targetChannel.isActive()).thenReturn(false);
        when(channelManager.getChannel(toUserId)).thenReturn(targetChannel);
        when(onlineStatusService.isUserOnline(toUserId)).thenReturn(true);
        when(messageDeduplicator.markAsProcessed(anyString())).thenReturn(true);

        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        when(kafkaProducer.sendAsync(anyString(), anyString(), any(IMMessage.class)))
                .thenReturn(future);

        // When
        privateChatHandler.handle(ctx, message);

        // Then
        verify(kafkaProducer).sendAsync(eq("im-msg-push"), anyString(), eq(message));
        verify(targetChannel, never()).writeAndFlush(any(IMMessage.class));
    }

    @Test
    @DisplayName("测试处理私聊消息 - 计算会话ID")
    void testHandlePrivateChat_ConversationIdCalculation() {
        // Given
        Long fromUserId = 2000L;
        Long toUserId = 1000L;

        IMMessage message = createPrivateChatMessage(fromUserId, toUserId, "Hello");

        when(channelManager.getChannel(toUserId)).thenReturn(null);
        when(onlineStatusService.isUserOnline(toUserId)).thenReturn(false);
        when(messageDeduplicator.markAsProcessed(anyString())).thenReturn(true);

        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        when(kafkaProducer.sendAsync(anyString(), anyString(), any(IMMessage.class)))
                .thenReturn(future);

        // When
        privateChatHandler.handle(ctx, message);

        // Then
        // 验证会话ID格式：较小的userId_较大的userId
        verify(kafkaProducer).sendAsync(
                eq("im-msg-send"),
                eq("1000_2000"), // min(from, to) + "_" + max(from, to)
                eq(message)
        );
    }

    @Test
    @DisplayName("测试获取统计信息")
    void testGetStats() {
        // Given - 无需额外设置

        // When
        String stats = privateChatHandler.getStats();

        // Then
        assertThat(stats).contains("PrivateChatHandler");
        assertThat(stats).contains("total");
        assertThat(stats).contains("online");
        assertThat(stats).contains("offline");
    }

    @Test
    @DisplayName("测试重置统计信息")
    void testResetStats() {
        // Given - 处理一些消息以增加计数
        Long fromUserId = 1001L;
        Long toUserId = 1002L;

        IMMessage message = createPrivateChatMessage(fromUserId, toUserId, "Hello");

        when(channelManager.getChannel(toUserId)).thenReturn(null);
        when(onlineStatusService.isUserOnline(toUserId)).thenReturn(false);
        when(messageDeduplicator.markAsProcessed(anyString())).thenReturn(true);

        CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
        when(kafkaProducer.sendAsync(anyString(), anyString(), any(IMMessage.class)))
                .thenReturn(future);

        privateChatHandler.handle(ctx, message);

        // When
        privateChatHandler.resetStats();

        // Then
        String stats = privateChatHandler.getStats();
        assertThat(stats).contains("total=0");
    }

    // ==================== 辅助方法 ====================

    private IMMessage createPrivateChatMessage(Long from, Long to, String content) {
        return IMMessage.builder()
                .header(MessageHeader.builder()
                        .msgId("msg-" + System.currentTimeMillis())
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
