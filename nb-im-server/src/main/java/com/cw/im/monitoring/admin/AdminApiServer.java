package com.cw.im.monitoring.admin;

import com.cw.im.monitoring.MetricsCollector;
import com.cw.im.monitoring.alerting.AlertingEngine;
import com.cw.im.monitoring.health.HealthCheckService;
import com.cw.im.monitoring.health.NettyHealthChecker;
import com.cw.im.monitoring.health.RedisHealthChecker;
import com.cw.im.monitoring.health.KafkaHealthChecker;
import com.cw.im.monitoring.performance.PerformanceMonitor;
import com.cw.im.server.channel.ChannelManager;
import com.cw.im.core.ReliabilityMetrics;
import com.cw.im.redis.RedisManager;
import com.cw.im.kafka.KafkaProducerManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.CharsetUtil;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 管理API服务器
 *
 * <p>提供HTTP REST API接口用于系统管理和监控</p>
 *
 * <h3>API端点</h3>
 * <ul>
 *     <li>GET /health - 健康检查</li>
 *     <li>GET /metrics - 所有指标</li>
 *     <li>GET /metrics/performance - 性能指标</li>
 *     <li>GET /metrics/reliability - 可靠性指标</li>
 *     <li>GET /alerts - 活跃告警</li>
 *     <li>GET /overview - 系统概览</li>
 *     <li>GET /stats - 统计摘要</li>
 *     <li>POST /reset/:type - 重置指标</li>
 * </ul>
 *
 * @author cw
 * @since 1.0.0
 */
@Slf4j
public class AdminApiServer {

    private final int port;
    private final MonitoringService monitoringService;
    private final ObjectMapper objectMapper;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public AdminApiServer(
            int port,
            List<MetricsCollector> metricsCollectors,
            HealthCheckService healthCheckService,
            PerformanceMonitor performanceMonitor,
            AlertingEngine alertingEngine,
            ChannelManager channelManager,
            ReliabilityMetrics reliabilityMetrics) {
        this.port = port;
        this.monitoringService = new MonitoringService(
                metricsCollectors,
                healthCheckService,
                performanceMonitor,
                alertingEngine,
                channelManager,
                reliabilityMetrics
        );
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 启动服务器
     */
    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(new HttpServerCodec());
                            pipeline.addLast(new HttpObjectAggregator(65536));
                            pipeline.addLast(new AdminApiHandler());
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            ChannelFuture future = bootstrap.bind(port).sync();
            serverChannel = future.channel();

            log.info("管理API服务器启动成功: port={}", port);
            log.info("访问地址: http://localhost:{}/health", port);
            log.info("访问地址: http://localhost:{}/metrics", port);
            log.info("访问地址: http://localhost:{}/overview", port);

        } catch (Exception e) {
            log.error("管理API服务器启动失败", e);
            shutdown();
            throw e;
        }
    }

    /**
     * 关闭服务器
     */
    public void shutdown() {
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        log.info("管理API服务器已关闭");
    }

    /**
     * API处理器
     */
    @ChannelHandler.Sharable
    private class AdminApiHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
            try {
                String uri = request.uri();
                HttpMethod method = request.method();

                log.debug("收到请求: method={}, uri={}", method, uri);

                // 处理CORS
                if (method.equals(HttpMethod.OPTIONS)) {
                    sendCorsResponse(ctx);
                    return;
                }

                Object result;
                switch (uri.split("\\?")[0]) {
                    case "/health":
                        result = monitoringService.getHealthStatus();
                        break;

                    case "/metrics":
                        result = monitoringService.getAllMetrics();
                        break;

                    case "/metrics/performance":
                        result = monitoringService.getPerformanceMetrics();
                        break;

                    case "/metrics/reliability":
                        result = monitoringService.getReliabilityMetrics();
                        break;

                    case "/alerts":
                        result = monitoringService.getActiveAlerts();
                        break;

                    case "/overview":
                        result = monitoringService.getSystemOverview();
                        break;

                    case "/stats":
                        result = monitoringService.getStats();
                        break;

                    case "/":
                        result = Map.of(
                                "name", "NB-IM Admin API",
                                "version", "1.0.0",
                                "endpoints", List.of(
                                        "/health",
                                        "/metrics",
                                        "/metrics/performance",
                                        "/metrics/reliability",
                                        "/alerts",
                                        "/overview",
                                        "/stats"
                                )
                        );
                        break;

                    default:
                        sendResponse(ctx, HttpResponse.notFound());
                        return;
                }

                sendResponse(ctx, HttpResponse.success(result));

            } catch (Exception e) {
                log.error("处理请求异常", e);
                sendResponse(ctx, HttpResponse.serverError(e.getMessage()));
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.error("API处理器异常", cause);
            ctx.close();
        }

        /**
         * 发送JSON响应
         */
        private void sendResponse(ChannelHandlerContext ctx, HttpResponse response) {
            try {
                String json = objectMapper.writeValueAsString(response);
                FullHttpResponse httpResponse = new DefaultFullHttpResponse(
                        HttpVersion.HTTP_1_1,
                        HttpResponseStatus.valueOf(response.getCode()),
                        Unpooled.copiedBuffer(json, CharsetUtil.UTF_8)
                );

                httpResponse.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
                httpResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, httpResponse.content().readableBytes());
                httpResponse.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);

                // 添加CORS头
                addCorsHeaders(httpResponse);

                ctx.writeAndFlush(httpResponse).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);

            } catch (Exception e) {
                log.error("发送响应失败", e);
                ctx.close();
            }
        }

        /**
         * 发送CORS响应
         */
        private void sendCorsResponse(ChannelHandlerContext ctx) {
            FullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    HttpResponseStatus.OK
            );
            addCorsHeaders(response);
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
        }

        /**
         * 添加CORS头
         */
        private void addCorsHeaders(FullHttpResponse response) {
            response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS, "*");
            response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS, "GET, POST, PUT, DELETE, OPTIONS");
        }
    }
}
