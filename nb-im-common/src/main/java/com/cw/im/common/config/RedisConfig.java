package com.cw.im.common.config;

import lombok.Data;

/**
 * Redis配置类
 *
 * <p>封装Redis客户端相关配置参数</p>
 *
 * @author cw
 * @since 1.0.0
 */
@Data
public class RedisConfig {

    /**
     * 主机地址
     */
    private String host = "localhost";

    /**
     * 端口
     */
    private int port = 6379;

    /**
     * 密码
     */
    private String password = "";

    /**
     * 数据库索引
     */
    private int database = 0;

    /**
     * 连接超时时间（毫秒）
     */
    private int timeout = 3000;

    /**
     * 连接池配置
     */
    private PoolConfig pool = new PoolConfig();

    /**
     * 集群配置
     */
    private ClusterConfig cluster;

    /**
     * 连接池配置
     */
    @Data
    public static class PoolConfig {
        /**
         * 最大连接数
         */
        private int maxActive = 20;

        /**
         * 最大空闲连接数
         */
        private int maxIdle = 10;

        /**
         * 最小空闲连接数
         */
        private int minIdle = 5;

        /**
         * 连接等待超时（毫秒）
         */
        private long maxWait = 3000;
    }

    /**
     * 集群配置
     */
    @Data
    public static class ClusterConfig {
        /**
         * 集群节点列表
         */
        private String[] nodes;

        /**
         * 最大重定向次数
         */
        private int maxRedirects = 3;
    }

    /**
     * 默认配置
     */
    public static RedisConfig defaultConfig() {
        return new RedisConfig();
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
        private final RedisConfig config;

        private Builder() {
            this.config = new RedisConfig();
        }

        public Builder host(String host) {
            config.setHost(host);
            return this;
        }

        public Builder port(int port) {
            config.setPort(port);
            return this;
        }

        public Builder password(String password) {
            config.setPassword(password);
            return this;
        }

        public Builder database(int database) {
            config.setDatabase(database);
            return this;
        }

        public Builder timeout(int timeout) {
            config.setTimeout(timeout);
            return this;
        }

        public Builder pool(PoolConfig pool) {
            config.setPool(pool);
            return this;
        }

        public Builder cluster(ClusterConfig cluster) {
            config.setCluster(cluster);
            return this;
        }

        public RedisConfig build() {
            return config;
        }
    }

    /**
     * 判断是否为集群模式
     */
    public boolean isCluster() {
        return cluster != null && cluster.getNodes() != null && cluster.getNodes().length > 0;
    }
}
