package com.cw.im.server.handler;

import com.cw.im.common.protocol.CommandType;
import com.cw.im.common.protocol.IMMessage;
import com.cw.im.redis.OnlineStatusService;
import com.cw.im.server.attributes.ChannelAttributes;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 认证 Handler
 *
 * <p>负责处理客户端的认证请求，验证 Token 并提取用户信息</p>
 *
 * <h3>认证流程</h3>
 * <ol>
 *     <li>客户端发送认证消息（包含 token）</li>
 *     <li>Handler 验证 Token 有效性</li>
 *     <li>提取用户ID和设备信息</li>
 *     <li>设置 Channel 属性</li>
 *     <li>通过后传递给下一个 Handler</li>
 * </ol>
 *
 * <h3>认证失败处理</h3>
 * <ul>
 *     <li>Token 无效：关闭连接</li>
 *     <li>认证超时：关闭连接</li>
 *     <li>重复认证：忽略</li>
 * </ul>
 *
 * @author cw
 * @since 1.0.0
 */
@Slf4j
public class AuthHandler extends SimpleChannelInboundHandler<IMMessage> {

    /**
     * 认证超时时间（秒）
     */
    private static final int AUTH_TIMEOUT_SECONDS = 30;

    /**
     * 在线状态服务
     */
    private final OnlineStatusService onlineStatusService;

    /**
     * 网关ID
     */
    private final String gatewayId;

    /**
     * 构造函数
     *
     * @param onlineStatusService 在线状态服务
     * @param gatewayId           网关ID
     */
    public AuthHandler(OnlineStatusService onlineStatusService, String gatewayId) {
        this.onlineStatusService = onlineStatusService;
        this.gatewayId = gatewayId;
    }

    /**
     * 构造函数
     *
     * @param onlineStatusService 在线状态服务
     */
    public AuthHandler(OnlineStatusService onlineStatusService) {
        this(onlineStatusService, "default-gateway");
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, IMMessage msg) throws Exception {
        // 1. 检查是否已认证
        if (isAuthenticated(ctx)) {
            // 已认证，直接传递给下一个 Handler
            ctx.fireChannelRead(msg);
            return;
        }

        // 2. 未认证，只处理认证消息（心跳消息也可以通过）
        if (msg.getCmd() == CommandType.HEARTBEAT) {
            // 心跳消息传递给下一个 Handler
            ctx.fireChannelRead(msg);
            return;
        }

        // 3. 尝试从消息中提取认证信息
        Long userId = extractUserId(msg);
        String deviceId = extractDeviceId(msg);
        String deviceType = extractDeviceType(msg);
        String token = extractToken(msg);

        // 4. 验证认证信息
        if (userId == null || token == null) {
            log.warn("认证失败: 缺少必要信息, remoteAddress={}, userId={}, token={}",
                    ctx.channel().remoteAddress(), userId, token != null);
            ctx.close();
            return;
        }

        // 5. 验证 Token（示例实现，实际应该调用认证服务）
        if (!validateToken(userId, token)) {
            log.warn("认证失败: Token无效, userId={}, token={}", userId, token);
            sendAuthFailResponse(ctx, "Token无效");
            ctx.close();
            return;
        }

        // 6. 认证成功，设置 Channel 属性
        ChannelAttributes.setUserId(ctx.channel(), userId);
        ChannelAttributes.setDeviceId(ctx.channel(), deviceId != null ? deviceId : generateDeviceId(userId));
        ChannelAttributes.setDeviceType(ctx.channel(), deviceType != null ? deviceType : "UNKNOWN");
        ChannelAttributes.setAuthenticated(ctx.channel(), true);
        ChannelAttributes.setGatewayId(ctx.channel(), gatewayId);
        ChannelAttributes.setConnectTime(ctx.channel(), System.currentTimeMillis());
        ChannelAttributes.setRemoteAddress(ctx.channel(), ctx.channel().remoteAddress().toString());
        ChannelAttributes.updateHeartbeatTime(ctx.channel());

        log.info("认证成功: userId={}, deviceId={}, deviceType={}, remoteAddress={}",
                userId, deviceId, deviceType, ctx.channel().remoteAddress());

        // 7. 发送认证成功响应
        sendAuthSuccessResponse(ctx, userId);

        // 8. 传递消息给下一个 Handler
        ctx.fireChannelRead(msg);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            // 认证超时，关闭连接
            log.warn("认证超时: remoteAddress={}, 超时时间={}s",
                    ctx.channel().remoteAddress(), AUTH_TIMEOUT_SECONDS);
            ctx.close();
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // 连接断开，清理资源
        if (isAuthenticated(ctx)) {
            Long userId = ChannelAttributes.getUserId(ctx.channel());
            String channelId = ctx.channel().id().asLongText();
            log.info("已认证用户断开连接: userId={}, channelId={}", userId, channelId);
        } else {
            log.info("未认证用户断开连接: remoteAddress={}", ctx.channel().remoteAddress());
        }
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("认证Handler异常: remoteAddress={}", ctx.channel().remoteAddress(), cause);
        ctx.close();
    }

    // ==================== 私有方法 ====================

    /**
     * 检查是否已认证
     *
     * @param ctx ChannelHandlerContext
     * @return true-已认证, false-未认证
     */
    private boolean isAuthenticated(ChannelHandlerContext ctx) {
        return ChannelAttributes.isAuthenticated(ctx.channel());
    }

    /**
     * 从消息中提取用户ID
     *
     * @param msg IM消息
     * @return 用户ID
     */
    private Long extractUserId(IMMessage msg) {
        // 优先从消息头获取
        if (msg.getHeader() != null && msg.getHeader().getFrom() != null) {
            return msg.getHeader().getFrom();
        }

        // 从 extras 获取
        if (msg.getBody() != null && msg.getBody().getExtras() != null) {
            Object userIdObj = msg.getBody().getExtras().get("userId");
            if (userIdObj instanceof Number) {
                return ((Number) userIdObj).longValue();
            }
        }

        return null;
    }

    /**
     * 从消息中提取设备ID
     *
     * @param msg IM消息
     * @return 设备ID
     */
    private String extractDeviceId(IMMessage msg) {
        if (msg.getHeader() != null && msg.getHeader().getExtras() != null) {
            Object deviceIdObj = msg.getHeader().getExtras().get("deviceId");
            if (deviceIdObj != null) {
                return deviceIdObj.toString();
            }
        }

        if (msg.getBody() != null && msg.getBody().getExtras() != null) {
            Object deviceIdObj = msg.getBody().getExtras().get("deviceId");
            if (deviceIdObj != null) {
                return deviceIdObj.toString();
            }
        }

        return null;
    }

    /**
     * 从消息中提取设备类型
     *
     * @param msg IM消息
     * @return 设备类型
     */
    private String extractDeviceType(IMMessage msg) {
        if (msg.getHeader() != null && msg.getHeader().getExtras() != null) {
            Object deviceTypeObj = msg.getHeader().getExtras().get("deviceType");
            if (deviceTypeObj != null) {
                return deviceTypeObj.toString();
            }
        }

        if (msg.getBody() != null && msg.getBody().getExtras() != null) {
            Object deviceTypeObj = msg.getBody().getExtras().get("deviceType");
            if (deviceTypeObj != null) {
                return deviceTypeObj.toString();
            }
        }

        return null;
    }

    /**
     * 从消息中提取 Token
     *
     * @param msg IM消息
     * @return Token
     */
    private String extractToken(IMMessage msg) {
        if (msg.getHeader() != null && msg.getHeader().getExtras() != null) {
            Object tokenObj = msg.getHeader().getExtras().get("token");
            if (tokenObj != null) {
                return tokenObj.toString();
            }
        }

        if (msg.getBody() != null && msg.getBody().getExtras() != null) {
            Object tokenObj = msg.getBody().getExtras().get("token");
            if (tokenObj != null) {
                return tokenObj.toString();
            }
        }

        return null;
    }

    /**
     * 验证 Token（示例实现）
     *
     * @param userId 用户ID
     * @param token  Token
     * @return true-验证通过, false-验证失败
     */
    private boolean validateToken(Long userId, String token) {
        // TODO: 实际应该调用认证服务验证 Token
        // 这里只是一个示例实现

        // 1. 检查 Token 是否为空
        if (token == null || token.trim().isEmpty()) {
            return false;
        }

        // 2. 简单验证：Token 长度至少 10 个字符
        if (token.length() < 10) {
            return false;
        }

        // 3. 可以在这里添加更复杂的验证逻辑
        // 例如：调用认证服务 API、验证 Token 签名等

        return true;
    }

    /**
     * 生成设备ID
     *
     * @param userId 用户ID
     * @return 设备ID
     */
    private String generateDeviceId(Long userId) {
        return "device-" + userId + "-" + System.currentTimeMillis();
    }

    /**
     * 发送认证成功响应
     *
     * @param ctx    ChannelHandlerContext
     * @param userId 用户ID
     */
    private void sendAuthSuccessResponse(ChannelHandlerContext ctx, Long userId) {
        try {
            IMMessage response = IMMessage.builder()
                    .header(com.cw.im.common.model.MessageHeader.builder()
                            .msgId(java.util.UUID.randomUUID().toString())
                            .cmd(CommandType.SYSTEM_NOTICE)
                            .from(0L)  // 系统消息
                            .to(userId)
                            .timestamp(System.currentTimeMillis())
                            .version("1.0")
                            .build())
                    .body(com.cw.im.common.model.MessageBody.builder()
                            .content("认证成功")
                            .contentType("auth")
                            .extras(Map.of(
                                    "status", "success",
                                    "userId", userId,
                                    "gatewayId", gatewayId
                            ))
                            .build())
                    .build();

            ctx.writeAndFlush(response);
            log.debug("发送认证成功响应: userId={}", userId);
        } catch (Exception e) {
            log.error("发送认证成功响应失败: userId={}", userId, e);
        }
    }

    /**
     * 发送认证失败响应
     *
     * @param ctx     ChannelHandlerContext
     * @param message 失败原因
     */
    private void sendAuthFailResponse(ChannelHandlerContext ctx, String message) {
        try {
            IMMessage response = IMMessage.builder()
                    .header(com.cw.im.common.model.MessageHeader.builder()
                            .msgId(java.util.UUID.randomUUID().toString())
                            .cmd(CommandType.SYSTEM_NOTICE)
                            .from(0L)  // 系统消息
                            .to(0L)
                            .timestamp(System.currentTimeMillis())
                            .version("1.0")
                            .build())
                    .body(com.cw.im.common.model.MessageBody.builder()
                            .content("认证失败: " + message)
                            .contentType("auth")
                            .extras(Map.of(
                                    "status", "failed",
                                    "reason", message
                            ))
                            .build())
                    .build();

            ctx.writeAndFlush(response);
            log.debug("发送认证失败响应: message={}", message);
        } catch (Exception e) {
            log.error("发送认证失败响应异常: message={}", message, e);
        }
    }
}
