package com.cw.im.common.config;

import lombok.Data;

/**
 * Netty配置类
 *
 * <p>封装Netty服务器相关配置参数</p>
 *
 * @author cw
 * @since 1.0.0
 */
@Data
public class NettyConfig {

    /**
     * 服务端口
     */
    private int port = 9090;

    /**
     * Boss线程数（建议1）
     */
    private int bossThreads = 1;

    /**
     * Worker线程数（0表示默认 CPU * 2）
     */
    private int workerThreads = 0;

    /**
     * TCP连接队列大小
     */
    private int soBacklog = 128;

    /**
     * TCP接收缓冲区大小（字节）
     */
    private int soRcvbuf = 32768;

    /**
     * TCP发送缓冲区大小（字节）
     */
    private int soSndbuf = 32768;

    /**
     * 是否启用TCP_NODELAY
     */
    private boolean tcpNoDelay = true;

    /**
     * 单个用户最大连接数
     */
    private int maxConnectionsPerUser = 10;

    /**
     * 网关最大连接数
     */
    private int maxConnections = 100000;

    /**
     * 心跳间隔（秒）
     */
    private int heartbeatInterval = 30;

    /**
     * 心跳超时时间（秒）
     */
    private int heartbeatTimeout = 60;

    /**
     * 默认配置
     */
    public static NettyConfig defaultConfig() {
        return new NettyConfig();
    }

    /**
     * 构建配置
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 配置构建器
     */
    public static class Builder {
        private final NettyConfig config;

        private Builder() {
            this.config = new NettyConfig();
        }

        public Builder port(int port) {
            config.setPort(port);
            return this;
        }

        public Builder bossThreads(int bossThreads) {
            config.setBossThreads(bossThreads);
            return this;
        }

        public Builder workerThreads(int workerThreads) {
            config.setWorkerThreads(workerThreads);
            return this;
        }

        public Builder soBacklog(int soBacklog) {
            config.setSoBacklog(soBacklog);
            return this;
        }

        public Builder soRcvbuf(int soRcvbuf) {
            config.setSoRcvbuf(soRcvbuf);
            return this;
        }

        public Builder soSndbuf(int soSndbuf) {
            config.setSoSndbuf(soSndbuf);
            return this;
        }

        public Builder tcpNoDelay(boolean tcpNoDelay) {
            config.setTcpNoDelay(tcpNoDelay);
            return this;
        }

        public Builder maxConnectionsPerUser(int maxConnectionsPerUser) {
            config.setMaxConnectionsPerUser(maxConnectionsPerUser);
            return this;
        }

        public Builder maxConnections(int maxConnections) {
            config.setMaxConnections(maxConnections);
            return this;
        }

        public Builder heartbeatInterval(int heartbeatInterval) {
            config.setHeartbeatInterval(heartbeatInterval);
            return this;
        }

        public Builder heartbeatTimeout(int heartbeatTimeout) {
            config.setHeartbeatTimeout(heartbeatTimeout);
            return this;
        }

        public NettyConfig build() {
            return config;
        }
    }
}
