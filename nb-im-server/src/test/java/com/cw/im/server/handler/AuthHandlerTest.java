package com.cw.im.server.handler;

import com.cw.im.common.model.MessageBody;
import com.cw.im.common.model.MessageHeader;
import com.cw.im.common.protocol.CommandType;
import com.cw.im.common.protocol.IMMessage;
import com.cw.im.redis.OnlineStatusService;
import com.cw.im.server.attributes.ChannelAttributes;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.timeout.IdleStateEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AuthHandler 单元测试
 *
 * @author cw
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthHandler 单元测试")
class AuthHandlerTest {

    @Mock
    private OnlineStatusService onlineStatusService;

    @Mock
    private ChannelHandlerContext ctx;

    @Mock
    private Channel channel;

    private AuthHandler authHandler;
    private String gatewayId = "test-gateway";

    @BeforeEach
    void setUp() {
        authHandler = new AuthHandler(onlineStatusService, gatewayId);

        // Setup Channel mock
        when(ctx.channel()).thenReturn(channel);
        when(channel.remoteAddress()).thenReturn("127.0.0.1:8080");
        when(channel.id()).thenReturn(io.netty.channel.channelId.Ids.of(1));
    }

    @Test
    @DisplayName("测试认证成功 - 从消息头提取用户信息")
    void testSuccessfulAuthentication_FromHeader() {
        // Given
        Long userId = 1001L;
        String token = "valid-token-12345";
        String deviceId = "device-001";
        String deviceType = "PC";

        IMMessage message = IMMessage.builder()
                .header(MessageHeader.builder()
                        .msgId("msg-001")
                        .cmd(CommandType.PRIVATE_CHAT)
                        .from(userId)
                        .to(1002L)
                        .timestamp(System.currentTimeMillis())
                        .version("1.0")
                        .extras(createAuthExtras(userId, token, deviceId, deviceType))
                        .build())
                .body(MessageBody.builder()
                        .content("Hello")
                        .build())
                .build();

        // Setup channel attributes mock
        setupChannelAttributesMock();

        // When
        authHandler.channelRead0(ctx, message);

        // Then
        verify(channel).attr(ChannelAttributes.USER_ID_KEY);
        verify(channel).attr(ChannelAttributes.DEVICE_ID_KEY);
        verify(channel).attr(ChannelAttributes.DEVICE_TYPE_KEY);
        verify(channel).attr(ChannelAttributes.AUTHENTICATED_KEY);
        verify(channel).attr(ChannelAttributes.GATEWAY_ID_KEY);
        verify(channel).attr(ChannelAttributes.CONNECT_TIME_KEY);
        verify(channel).attr(ChannelAttributes.REMOTE_ADDRESS_KEY);
        verify(channel).attr(ChannelAttributes.HEARTBEAT_TIME_KEY);

        verify(ctx).writeAndFlush(any(IMMessage.class));
        verify(ctx).fireChannelRead(message);
    }

    @Test
    @DisplayName("测试认证成功 - 从消息体提取用户信息")
    void testSuccessfulAuthentication_FromBody() {
        // Given
        Long userId = 1002L;
        String token = "valid-token-67890";

        IMMessage message = IMMessage.builder()
                .header(MessageHeader.builder()
                        .msgId("msg-002")
                        .cmd(CommandType.PRIVATE_CHAT)
                        .from(userId)
                        .to(1003L)
                        .timestamp(System.currentTimeMillis())
                        .version("1.0")
                        .build())
                .body(MessageBody.builder()
                        .content("Hi")
                        .extras(createAuthExtras(userId, token, null, null))
                        .build())
                .build();

        setupChannelAttributesMock();

        // When
        authHandler.channelRead0(ctx, message);

        // Then
        verify(ctx).writeAndFlush(any(IMMessage.class));
        verify(ctx).fireChannelRead(message);
    }

    @Test
    @DisplayName("测试认证失败 - Token无效")
    void testAuthenticationFailure_InvalidToken() {
        // Given
        Long userId = 1003L;
        String invalidToken = "short";  // Token长度小于10

        IMMessage message = IMMessage.builder()
                .header(MessageHeader.builder()
                        .msgId("msg-003")
                        .cmd(CommandType.PRIVATE_CHAT)
                        .from(userId)
                        .to(1004L)
                        .timestamp(System.currentTimeMillis())
                        .version("1.0")
                        .extras(Map.of("token", invalidToken))
                        .build())
                .body(MessageBody.builder()
                        .content("Test")
                        .build())
                .build();

        // When
        authHandler.channelRead0(ctx, message);

        // Then
        verify(ctx).writeAndFlush(any(IMMessage.class));  // 发送认证失败响应
        verify(ctx).close();
        verify(ctx, never()).fireChannelRead(any());
    }

    @Test
    @DisplayName("测试认证失败 - 缺少用户ID")
    void testAuthenticationFailure_MissingUserId() {
        // Given
        IMMessage message = IMMessage.builder()
                .header(MessageHeader.builder()
                        .msgId("msg-004")
                        .cmd(CommandType.PRIVATE_CHAT)
                        .timestamp(System.currentTimeMillis())
                        .version("1.0")
                        .extras(Map.of("token", "valid-token-12345"))
                        .build())
                .body(MessageBody.builder()
                        .content("Test")
                        .build())
                .build();

        // When
        authHandler.channelRead0(ctx, message);

        // Then
        verify(ctx).close();
        verify(ctx, never()).fireChannelRead(any());
    }

    @Test
    @DisplayName("测试认证失败 - 缺少Token")
    void testAuthenticationFailure_MissingToken() {
        // Given
        IMMessage message = IMMessage.builder()
                .header(MessageHeader.builder()
                        .msgId("msg-005")
                        .cmd(CommandType.PRIVATE_CHAT)
                        .from(1005L)
                        .to(1006L)
                        .timestamp(System.currentTimeMillis())
                        .version("1.0")
                        .build())
                .body(MessageBody.builder()
                        .content("Test")
                        .extras(Map.of("userId", 1005L))
                        .build())
                .build();

        // When
        authHandler.channelRead0(ctx, message);

        // Then
        verify(ctx).close();
        verify(ctx, never()).fireChannelRead(any());
    }

    @Test
    @DisplayName("测试已认证用户 - 直接传递消息")
    void testAlreadyAuthenticated_PassThrough() {
        // Given
        Long userId = 1007L;

        IMMessage message = IMMessage.builder()
                .header(MessageHeader.builder()
                        .msgId("msg-006")
                        .cmd(CommandType.PRIVATE_CHAT)
                        .from(userId)
                        .to(1008L)
                        .timestamp(System.currentTimeMillis())
                        .version("1.0")
                        .build())
                .body(MessageBody.builder()
                        .content("Hello")
                        .extras(Map.of("token", "valid-token-12345"))
                        .build())
                .build();

        // Mock已认证状态
        when(channel.attr(ChannelAttributes.AUTHENTICATED_KEY).get()).thenReturn(true);

        // When
        authHandler.channelRead0(ctx, message);

        // Then
        verify(ctx).fireChannelRead(message);
        verify(ctx, never()).writeAndFlush(any(IMMessage.class));
    }

    @Test
    @DisplayName("测试心跳消息 - 未认证状态下通过")
    void testHeartbeatMessage_PassThroughWhenNotAuthenticated() {
        // Given
        IMMessage heartbeatMessage = IMMessage.builder()
                .header(MessageHeader.builder()
                        .msgId("msg-007")
                        .cmd(CommandType.HEARTBEAT)
                        .from(1009L)
                        .timestamp(System.currentTimeMillis())
                        .version("1.0")
                        .build())
                .body(MessageBody.builder()
                        .content("ping")
                        .build())
                .build();

        // When
        authHandler.channelRead0(ctx, heartbeatMessage);

        // Then
        verify(ctx).fireChannelRead(heartbeatMessage);
        verify(ctx, never()).close();
    }

    @Test
    @DisplayName("测试认证超时 - IdleStateEvent触发")
    void testAuthenticationTimeout() throws Exception {
        // Given
        IdleStateEvent idleEvent = mock(IdleStateEvent.class);

        // When
        authHandler.userEventTriggered(ctx, idleEvent);

        // Then
        verify(ctx).close();
    }

    @Test
    @DisplayName("测试连接断开 - 已认证用户")
    void testChannelInactive_AuthenticatedUser() throws Exception {
        // Given
        when(channel.attr(ChannelAttributes.AUTHENTICATED_KEY).get()).thenReturn(true);
        when(channel.attr(ChannelAttributes.USER_ID_KEY).get()).thenReturn(1010L);
        when(channel.id().asLongText()).thenReturn("channel-001");

        // When
        authHandler.channelInactive(ctx);

        // Then
        verify(ctx).fireChannelInactive();
    }

    @Test
    @DisplayName("测试连接断开 - 未认证用户")
    void testChannelInactive_UnauthenticatedUser() throws Exception {
        // Given
        when(channel.attr(ChannelAttributes.AUTHENTICATED_KEY).get()).thenReturn(null);

        // When
        authHandler.channelInactive(ctx);

        // Then
        verify(ctx).fireChannelInactive();
    }

    @Test
    @DisplayName("测试异常处理")
    void testExceptionCaught() throws Exception {
        // Given
        Throwable cause = new RuntimeException("Test exception");

        // When
        authHandler.exceptionCaught(ctx, cause);

        // Then
        verify(ctx).close();
    }

    @Test
    @DisplayName("测试Token验证 - 空Token")
    void testTokenValidation_NullToken() {
        // Given
        IMMessage message = IMMessage.builder()
                .header(MessageHeader.builder()
                        .msgId("msg-008")
                        .cmd(CommandType.PRIVATE_CHAT)
                        .from(1011L)
                        .to(1012L)
                        .timestamp(System.currentTimeMillis())
                        .version("1.0")
                        .extras(Map.of("token", ""))
                        .build())
                .body(MessageBody.builder()
                        .content("Test")
                        .build())
                .build();

        // When
        authHandler.channelRead0(ctx, message);

        // Then
        verify(ctx).close();
    }

    @Test
    @DisplayName("测试Token验证 - 短Token")
    void testTokenValidation_ShortToken() {
        // Given
        IMMessage message = IMMessage.builder()
                .header(MessageHeader.builder()
                        .msgId("msg-009")
                        .cmd(CommandType.PRIVATE_CHAT)
                        .from(1013L)
                        .to(1014L)
                        .timestamp(System.currentTimeMillis())
                        .version("1.0")
                        .extras(Map.of("token", "abc"))
                        .build())
                .body(MessageBody.builder()
                        .content("Test")
                        .build())
                .build();

        // When
        authHandler.channelRead0(ctx, message);

        // Then
        verify(ctx).close();
    }

    @Test
    @DisplayName("测试设备ID生成 - 当消息中没有设备ID时")
    void testDeviceIdGeneration_WhenNotProvided() {
        // Given
        Long userId = 1015L;
        String token = "valid-token-device-test";

        IMMessage message = IMMessage.builder()
                .header(MessageHeader.builder()
                        .msgId("msg-010")
                        .cmd(CommandType.PRIVATE_CHAT)
                        .from(userId)
                        .to(1016L)
                        .timestamp(System.currentTimeMillis())
                        .version("1.0")
                        .extras(Map.of("token", token))
                        .build())
                .body(MessageBody.builder()
                        .content("Test")
                        .build())
                .build();

        setupChannelAttributesMock();

        // When
        authHandler.channelRead0(ctx, message);

        // Then
        verify(ctx).writeAndFlush(any(IMMessage.class));
        verify(ctx).fireChannelRead(message);
    }

    // ==================== 辅助方法 ====================

    private Map<String, Object> createAuthExtras(Long userId, String token, String deviceId, String deviceType) {
        Map<String, Object> extras = new HashMap<>();
        extras.put("token", token);
        if (deviceId != null) {
            extras.put("deviceId", deviceId);
        }
        if (deviceType != null) {
            extras.put("deviceType", deviceType);
        }
        return extras;
    }

    private void setupChannelAttributesMock() {
        // Mock Channel.attr() to return Attribute mocks
        io.netty.util.Attribute<Object> attrMock = mock(io.netty.util.Attribute.class);
        when(channel.attr(any())).thenReturn(attrMock);
    }
}
