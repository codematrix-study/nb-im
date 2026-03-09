package com.cw.im.common.config;

import lombok.Data;

/**
 * IM应用配置类
 *
 * <p>封装应用级别的配置参数</p>
 *
 * @author cw
 * @since 1.0.0
 */
@Data
public class IMAppConfig {

    /**
     * 网关ID（可选，默认自动生成）
     */
    private String gatewayId;

    /**
     * 环境配置（dev, test, prod）
     */
    private String env = "dev";

    /**
     * 心跳间隔（秒）
     */
    private int heartbeatInterval = 30;

    /**
     * 心跳超时（秒）
     */
    private int heartbeatTimeout = 60;

    /**
     * 消息ACK超时（秒）
     */
    private int messageAckTimeout = 30;

    /**
     * 消息重试次数
     */
    private int messageRetryTimes = 3;

    /**
     * 离线消息保存天数
     */
    private int offlineMessageRetentionDays = 7;

    /**
     * Netty配置
     */
    private NettyConfig netty = NettyConfig.defaultConfig();

    /**
     * Redis配置
     */
    private RedisConfig redis = RedisConfig.defaultConfig();

    /**
     * Kafka配置
     */
    private KafkaConfig kafka = KafkaConfig.defaultConfig();

    /**
     * 默认配置
     */
    public static IMAppConfig defaultConfig() {
        return new IMAppConfig();
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
        private final IMAppConfig config;

        private Builder() {
            this.config = new IMAppConfig();
        }

        public Builder gatewayId(String gatewayId) {
            config.setGatewayId(gatewayId);
            return this;
        }

        public Builder env(String env) {
            config.setEnv(env);
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

        public Builder messageAckTimeout(int messageAckTimeout) {
            config.setMessageAckTimeout(messageAckTimeout);
            return this;
        }

        public Builder messageRetryTimes(int messageRetryTimes) {
            config.setMessageRetryTimes(messageRetryTimes);
            return this;
        }

        public Builder offlineMessageRetentionDays(int offlineMessageRetentionDays) {
            config.setOfflineMessageRetentionDays(offlineMessageRetentionDays);
            return this;
        }

        public Builder netty(NettyConfig netty) {
            config.setNetty(netty);
            return this;
        }

        public Builder redis(RedisConfig redis) {
            config.setRedis(redis);
            return this;
        }

        public Builder kafka(KafkaConfig kafka) {
            config.setKafka(kafka);
            return this;
        }

        public IMAppConfig build() {
            return config;
        }
    }

    /**
     * 验证配置是否有效
     *
     * @return true-有效, false-无效
     */
    public boolean isValid() {
        return netty != null && redis != null && kafka != null;
    }
}
