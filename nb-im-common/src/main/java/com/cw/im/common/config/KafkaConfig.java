package com.cw.im.common.config;

import lombok.Data;

/**
 * Kafka配置类
 *
 * <p>封装Kafka Producer和Consumer相关配置参数</p>
 *
 * @author cw
 * @since 1.0.0
 */
@Data
public class KafkaConfig {

    /**
     * Kafka集群地址
     */
    private String bootstrapServers = "localhost:9092";

    /**
     * Producer配置
     */
    private ProducerConfig producer = new ProducerConfig();

    /**
     * Consumer配置
     */
    private ConsumerConfig consumer = new ConsumerConfig();

    /**
     * 监听器配置
     */
    private ListenerConfig listener = new ListenerConfig();

    /**
     * Producer配置
     */
    @Data
    public static class ProducerConfig {
        /**
         * Key序列化器
         */
        private String keySerializer = "org.apache.kafka.common.serialization.StringSerializer";

        /**
         * Value序列化器
         */
        private String valueSerializer = "org.apache.kafka.common.serialization.StringSerializer";

        /**
         * ACK级别（0, 1, all/-1）
         */
        private String acks = "1";

        /**
         * 重试次数
         */
        private int retries = 3;

        /**
         * 批量发送大小（字节）
         */
        private int batchSize = 16384;

        /**
         * 批量发送延迟时间（毫秒）
         */
        private int lingerMs = 10;

        /**
         * 缓冲区大小（字节）
         */
        private long bufferMemory = 33554432;

        /**
         * 是否启用幂等性
         */
        private boolean enableIdempotence = true;

        /**
         * 压缩类型（none, gzip, snappy, lz4, zstd）
         */
        private String compressionType = "lz4";
    }

    /**
     * Consumer配置
     */
    @Data
    public static class ConsumerConfig {
        /**
         * Key反序列化器
         */
        private String keyDeserializer = "org.apache.kafka.common.serialization.StringDeserializer";

        /**
         * Value反序列化器
         */
        private String valueDeserializer = "org.apache.kafka.common.serialization.StringDeserializer";

        /**
         * 消费者组ID
         */
        private String groupId = "im-gateway-group";

        /**
         * 自动提交offset（false表示手动提交）
         */
        private boolean enableAutoCommit = false;

        /**
         * 当没有初始offset时的策略（earliest, latest, none）
         */
        private String autoOffsetReset = "latest";

        /**
         * 单次拉取最大条数
         */
        private int maxPollRecords = 100;

        /**
         * 拉取最小字节数
         */
        private int fetchMinBytes = 1024;

        /**
         * 会话超时（毫秒）
         */
        private int sessionTimeoutMs = 30000;

        /**
         * 心跳间隔（毫秒）
         */
        private int heartbeatIntervalMs = 10000;
    }

    /**
     * 监听器配置
     */
    @Data
    public static class ListenerConfig {
        /**
         * 消费者线程数
         */
        private int concurrency = 4;

        /**
         * 推送消息Topic
         */
        private String pushTopic = "im-msg-push";

        /**
         * ACK消息Topic
         */
        private String ackTopic = "im-ack";
    }

    /**
     * 默认配置
     */
    public static KafkaConfig defaultConfig() {
        return new KafkaConfig();
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
        private final KafkaConfig config;

        private Builder() {
            this.config = new KafkaConfig();
        }

        public Builder bootstrapServers(String bootstrapServers) {
            config.setBootstrapServers(bootstrapServers);
            return this;
        }

        public Builder producer(ProducerConfig producer) {
            config.setProducer(producer);
            return this;
        }

        public Builder consumer(ConsumerConfig consumer) {
            config.setConsumer(consumer);
            return this;
        }

        public Builder listener(ListenerConfig listener) {
            config.setListener(listener);
            return this;
        }

        public KafkaConfig build() {
            return config;
        }
    }
}
