package com.cw.im.server.handler;

import com.cw.im.common.model.MessageBody;
import com.cw.im.common.model.MessageHeader;
import com.cw.im.common.protocol.CommandType;
import com.cw.im.common.protocol.IMMessage;
import com.cw.im.redis.OnlineStatusService;
import com.cw.im.server.attributes.ChannelAttributes;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * HeartbeatHandler 单元测试
 *
 * @author cw
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HeartbeatHandler 单元测试")
class HeartbeatHandlerTest {

    @Mock
    private OnlineStatusService onlineStatusService;

    @Mock
    private ChannelHandlerContext ctx;

    @Mock
    private Channel channel;

    private HeartbeatHandler heartbeatHandler;

    @BeforeEach
    void setUp() {
        heartbeatHandler = new HeartbeatHandler(onlineStatusService);

        // Setup Channel mock
        when(ctx.channel()).thenReturn(channel);
        when(channel.remoteAddress()).thenReturn("127.0.0.1:8080");
        when(channel.id()).thenReturn(io.netty.channel.channelId.Ids.of(1));
    }

    @Test
    @DisplayName("测试处理ping心跳 - 发送pong响应")
    void testHandleHeartbeat_Ping() {
        // Given
        Long userId = 1001L;
        IMMessage pingMessage = IMMessage.builder()
                .header(MessageHeader.builder()
                        .msgId("msg-001")
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

        setupChannelAttributesMock(userId);

        // When
        heartbeatHandler.channelRead(ctx, pingMessage);

        // Then
        ArgumentCaptor<IMMessage> messageCaptor = ArgumentCaptor.forClass(IMMessage.class);
        verify(ctx).writeAndFlush(messageCaptor.capture());

        IMMessage response = messageCaptor.getValue();
        assertThat(response.getCmd()).isEqualTo(CommandType.HEARTBEAT);
        assertThat(response.getContent()).isEqualTo("pong");
        assertThat(response.getContentType()).isEqualTo("heartbeat");

        verify(onlineStatusService).heartbeat(eq(userId), anyString());
    }

    @Test
    @DisplayName("测试处理pong心跳 - 更新心跳时间")
    void testHandleHeartbeat_Pong() {
        // Given
        Long userId = 1002L;
        IMMessage pongMessage = IMMessage.builder()
                .header(MessageHeader.builder()
                        .msgId("msg-002")
                        .cmd(CommandType.HEARTBEAT)
                        .from(userId)
                        .timestamp(System.currentTimeMillis())
                        .version("1.0")
                        .build())
                .body(MessageBody.builder()
                        .content("pong")
                        .contentType("heartbeat")
                        .build())
                .build();

        setupChannelAttributesMock(userId);

        // When
        heartbeatHandler.channelRead(ctx, pongMessage);

        // Then
        verify(ctx, never()).writeAndFlush(any(IMMessage.class));
        verify(onlineStatusService).heartbeat(eq(userId), anyString());
    }

    @Test
    @DisplayName("测试处理非心跳消息 - 更新心跳时间并传递")
    void testHandleNonHeartbeatMessage_UpdateAndPassThrough() {
        // Given
        Long userId = 1003L;
        IMMessage chatMessage = IMMessage.builder()
                .header(MessageHeader.builder()
                        .msgId("msg-003")
                        .cmd(CommandType.PRIVATE_CHAT)
                        .from(userId)
                        .to(1004L)
                        .timestamp(System.currentTimeMillis())
                        .version("1.0")
                        .build())
                .body(MessageBody.builder()
                        .content("Hello")
                        .build())
                .build();

        setupChannelAttributesMock(userId);

        // When
        heartbeatHandler.channelRead(ctx, chatMessage);

        // Then
        verify(ctx).fireChannelRead(chatMessage);
        verify(onlineStatusService).heartbeat(eq(userId), anyString());
    }

    @Test
    @DisplayName("测试读空闲超时 - 发送ping检测")
    void testReaderIdle_SendPing() {
        // Given
        Long userId = 1005L;
        long lastHeartbeatTime = System.currentTimeMillis() - 30000; // 30秒前
        setupChannelAttributesMock(userId, lastHeartbeatTime);

        IdleStateEvent idleEvent = IdleStateEvent.READER_IDLE_EVENT;

        // When
        heartbeatHandler.userEventTriggered(ctx, idleEvent);

        // Then
        ArgumentCaptor<IMMessage> messageCaptor = ArgumentCaptor.forClass(IMMessage.class);
        verify(ctx).writeAndFlush(messageCaptor.capture());

        IMMessage pingMessage = messageCaptor.getValue();
        assertThat(pingMessage.getCmd()).isEqualTo(CommandType.HEARTBEAT);
        assertThat(pingMessage.getContent()).isEqualTo("ping");

        // 验证没有关闭连接
        verify(ctx, never()).close();
    }

    @Test
    @DisplayName("测试读空闲超时 - 心跳超时关闭连接")
    void testReaderIdle_HeartbeatTimeout() {
        // Given
        Long userId = 1006L;
        long lastHeartbeatTime = System.currentTimeMillis() - 130000; // 130秒前，超过120秒超时
        setupChannelAttributesMock(userId, lastHeartbeatTime);

        IdleStateEvent idleEvent = IdleStateEvent.READER_IDLE_EVENT;

        // When
        heartbeatHandler.userEventTriggered(ctx, idleEvent);

        // Then
        verify(ctx).close();
        verify(ctx, never()).writeAndFlush(any(IMMessage.class));
    }

    @Test
    @DisplayName("测试写空闲事件 - 仅记录日志")
    void testWriterIdle_LogOnly() {
        // Given
        Long userId = 1007L;
        setupChannelAttributesMock(userId);

        IdleStateEvent idleEvent = IdleStateEvent.WRITER_IDLE_EVENT;

        // When
        heartbeatHandler.userEventTriggered(ctx, idleEvent);

        // Then
        verify(ctx, never()).writeAndFlush(any(IMMessage.class));
        verify(ctx, never()).close();
    }

    @Test
    @DisplayName("测试读写空闲事件 - 仅记录日志")
    void testAllIdle_LogOnly() {
        // Given
        Long userId = 1008L;
        setupChannelAttributesMock(userId);

        IdleStateEvent idleEvent = IdleStateEvent.ALL_IDLE_EVENT;

        // When
        heartbeatHandler.userEventTriggered(ctx, idleEvent);

        // Then
        verify(ctx, never()).writeAndFlush(any(IMMessage.class));
        verify(ctx, never()).close();
    }

    @Test
    @DisplayName("测试非IdleStateEvent - 传递给父类")
    void testNonIdleStateEvent_PassToSuper() throws Exception {
        // Given
        Object nonIdleEvent = new Object();

        // When
        heartbeatHandler.userEventTriggered(ctx, nonIdleEvent);

        // Then
        verify(ctx, never()).writeAndFlush(any(IMMessage.class));
        verify(ctx, never()).close();
    }

    @Test
    @DisplayName("测试连接断开 - 记录日志")
    void testChannelInactive_LogUserInfo() throws Exception {
        // Given
        Long userId = 1009L;
        setupChannelAttributesMock(userId);

        // When
        heartbeatHandler.channelInactive(ctx);

        // Then
        verify(ctx).fireChannelInactive();
    }

    @Test
    @DisplayName("测试连接断开 - 无用户ID")
    void testChannelInactive_NoUserId() throws Exception {
        // Given
        setupChannelAttributesMock(null);

        // When
        heartbeatHandler.channelInactive(ctx);

        // Then
        verify(ctx).fireChannelInactive();
    }

    @Test
    @DisplayName("测试异常处理 - 关闭连接")
    void testExceptionCaught_CloseConnection() throws Exception {
        // Given
        Throwable cause = new RuntimeException("Test exception");

        // When
        heartbeatHandler.exceptionCaught(ctx, cause);

        // Then
        verify(ctx).close();
    }

    @Test
    @DisplayName("测试非IMMessage对象 - 传递给下一个Handler")
    void testNonIMMessageObject_PassThrough() {
        // Given
        Object nonMessage = "Not a message";

        // When
        heartbeatHandler.channelRead(ctx, nonMessage);

        // Then
        verify(ctx).fireChannelRead(nonMessage);
        verify(ctx, never()).writeAndFlush(any(IMMessage.class));
    }

    @Test
    @DisplayName("测试心跳更新失败 - 不影响消息处理")
    void testHeartbeatUpdateFailure_DoesNotAffectMessageProcessing() {
        // Given
        Long userId = 1010L;
        IMMessage chatMessage = IMMessage.builder()
                .header(MessageHeader.builder()
                        .msgId("msg-004")
                        .cmd(CommandType.PRIVATE_CHAT)
                        .from(userId)
                        .to(1011L)
                        .timestamp(System.currentTimeMillis())
                        .version("1.0")
                        .build())
                .body(MessageBody.builder()
                        .content("Hello")
                        .build())
                .build();

        setupChannelAttributesMock(userId);

        // Mock onlineStatusService to throw exception
        doThrow(new RuntimeException("Redis error")).when(onlineStatusService)
                .heartbeat(any(Long.class), anyString());

        // When
        heartbeatHandler.channelRead(ctx, chatMessage);

        // Then
        verify(ctx).fireChannelRead(chatMessage); // 消息仍然被传递
    }

    @Test
    @DisplayName("测试其他心跳消息内容")
    void testOtherHeartbeatContent_UpdateHeartbeat() {
        // Given
        Long userId = 1012L;
        IMMessage customHeartbeat = IMMessage.builder()
                .header(MessageHeader.builder()
                        .msgId("msg-005")
                        .cmd(CommandType.HEARTBEAT)
                        .from(userId)
                        .timestamp(System.currentTimeMillis())
                        .version("1.0")
                        .build())
                .body(MessageBody.builder()
                        .content("custom-heartbeat")
                        .contentType("heartbeat")
                        .build())
                .build();

        setupChannelAttributesMock(userId);

        // When
        heartbeatHandler.channelRead(ctx, customHeartbeat);

        // Then
        verify(onlineStatusService).heartbeat(eq(userId), anyString());
        verify(ctx, never()).writeAndFlush(any(IMMessage.class)); // 不发送响应
    }

    @Test
    @DisplayName("测试发送ping失败 - 记录错误但不抛出异常")
    void testSendPingFailure_LogError() {
        // Given
        Long userId = 1013L;
        long lastHeartbeatTime = System.currentTimeMillis() - 30000;
        setupChannelAttributesMock(userId, lastHeartbeatTime);

        IdleStateEvent idleEvent = IdleStateEvent.READER_IDLE_EVENT;

        // Mock writeAndFlush to throw exception
        doThrow(new RuntimeException("Write failed")).when(ctx).writeAndFlush(any(IMMessage.class));

        // When
        heartbeatHandler.userEventTriggered(ctx, idleEvent);

        // Then
        verify(ctx).writeAndFlush(any(IMMessage.class)); // 尝试发送
        verify(ctx, never()).close(); // 不关闭连接
    }

    @Test
    @DisplayName("测试获取读空闲时间")
    void testGetReaderIdleTimeSeconds() {
        // When
        int idleTime = HeartbeatHandler.getReaderIdleTimeSeconds();

        // Then
        assertThat(idleTime).isEqualTo(60);
    }

    @Test
    @DisplayName("测试获取心跳超时时间")
    void testGetHeartbeatTimeoutSeconds() {
        // When
        int timeout = HeartbeatHandler.getHeartbeatTimeoutSeconds();

        // Then
        assertThat(timeout).isEqualTo(120);
    }

    // ==================== 辅助方法 ====================

    private void setupChannelAttributesMock(Long userId) {
        setupChannelAttributesMock(userId, System.currentTimeMillis());
    }

    private void setupChannelAttributesMock(Long userId, long lastHeartbeatTime) {
        // Mock Channel.attr() to return Attribute mocks
        io.netty.util.Attribute<Long> userIdAttr = mock(io.netty.util.Attribute.class);
        when(userIdAttr.get()).thenReturn(userId);

        io.netty.util.Attribute<Long> heartbeatTimeAttr = mock(io.netty.util.Attribute.class);
        when(heartbeatTimeAttr.get()).thenReturn(lastHeartbeatTime);

        io.netty.util.Attribute<String> deviceIdAttr = mock(io.netty.util.Attribute.class);
        when(deviceIdAttr.get()).thenReturn("device-001");

        when(channel.attr(ChannelAttributes.USER_ID_KEY)).thenReturn(userIdAttr);
        when(channel.attr(ChannelAttributes.LAST_HEARTBEAT_TIME_KEY)).thenReturn(heartbeatTimeAttr);
        when(channel.attr(ChannelAttributes.DEVICE_ID_KEY)).thenReturn(deviceIdAttr);

        // Mock other attributes
        when(channel.attr(any(io.netty.util.AttributeKey.class)))
                .thenReturn(mock(io.netty.util.Attribute.class));
    }
}
