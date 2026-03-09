package com.cw.im.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Kafka 生产者管理器 - 增强版
 *
 * <p>支持自定义分区器、序列化配置、压缩配置、幂等性配置、批量发送配置和完善的重试机制</p>
 *
 * <h3>核心功能</h3>
 * <ul>
 *     <li>同步/异步发送消息</li>
 *     <li>指定分区发送</li>
 *     <li>自定义分区器（MessagePartitioner）</li>
 *     <li>压缩支持（lz4/snappy/gzip）</li>
 *     <li>幂等性保证</li>
 *     <li>批量发送优化</li>
 *     <li>完善的错误处理和重试机制</li>
 * </ul>
 *
 * @author cw
 * @since 1.0.0
 */
@Slf4j
public class KafkaProducerManager {

    private static final String DEFAULT_BOOTSTRAP_SERVERS = "192.168.215.2:9092";
    private static final String DEFAULT_ACKS = "1";
    private static final int DEFAULT_RETRIES = 3;
    private static final int DEFAULT_BATCH_SIZE = 16384; // 16KB
    private static final int DEFAULT_LINGER_MS = 10; // 10ms
    private static final String DEFAULT_COMPRESSION_TYPE = "lz4";
    private static final boolean DEFAULT_ENABLE_IDEMPOTENCE = true;

    private final KafkaProducer<String, String> producer;
    private final String bootstrapServers;

    /**
     * 默认构造函数
     */
    public KafkaProducerManager() {
        this(DEFAULT_BOOTSTRAP_SERVERS);
    }

    /**
     * 指定服务器地址构造函数
     *
     * @param bootstrapServers Kafka集群地址
     */
    public KafkaProducerManager(String bootstrapServers) {
        this(bootstrapServers, DEFAULT_ACKS, DEFAULT_RETRIES, DEFAULT_BATCH_SIZE,
             DEFAULT_LINGER_MS, DEFAULT_COMPRESSION_TYPE, DEFAULT_ENABLE_IDEMPOTENCE);
    }

    /**
     * 完整参数构造函数
     *
     * @param bootstrapServers    Kafka集群地址
     * @param acks                ACK确认级别（0/1/all/-1）
     * @param retries             重试次数
     * @param batchSize           批量发送大小（字节）
     * @param lingerMs            等待批量发送的时间（毫秒）
     * @param compressionType     压缩类型（none/gzip/snappy/lz4/zstd）
     * @param enableIdempotence   是否启用幂等性
     */
    public KafkaProducerManager(String bootstrapServers, String acks, int retries,
                                int batchSize, int lingerMs, String compressionType,
                                boolean enableIdempotence) {
        this.bootstrapServers = bootstrapServers;
        this.producer = initProducer(bootstrapServers, acks, retries, batchSize,
                                    lingerMs, compressionType, enableIdempotence);
    }

    /**
     * 初始化 Kafka Producer
     */
    private KafkaProducer<String, String> initProducer(String bootstrapServers, String acks,
                                                       int retries, int batchSize,
                                                       int lingerMs, String compressionType,
                                                       boolean enableIdempotence) {
        Properties props = new Properties();

        // ==================== 基础配置 ====================

        // Kafka 服务器地址
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        // Key 序列化器
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        // Value 序列化器
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        // ==================== 可靠性配置 ====================

        // ACK 确认级别
        // 0: 不等待确认
        // 1: leader确认
        // all/-1: 所有副本确认
        props.put(ProducerConfig.ACKS_CONFIG, acks);

        // 启用幂等性（防止重复）
        // 幂等性可以保证单分区单会话的 exactly-once 语义
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, enableIdempotence);

        // 重试次数
        props.put(ProducerConfig.RETRIES_CONFIG, retries);

        // 重试间隔（毫秒）
        props.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 100);

        // ==================== 性能配置 ====================

        // 批量发送大小（字节）
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, batchSize);

        // 等待批量发送的时间（毫秒）
        props.put(ProducerConfig.LINGER_MS_CONFIG, lingerMs);

        // 生产者缓冲区大小（字节）
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432); // 32MB

        // 最大请求大小（字节）
        props.put(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, 1048576); // 1MB

        // ==================== 压缩配置 ====================

        // 压缩类型（none/gzip/snappy/lz4/zstd）
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, compressionType);

        // ==================== 分区器配置 ====================

        // 自定义分区器
        props.put(ProducerConfig.PARTITIONER_CLASS_CONFIG, MessagePartitioner.class.getName());

        // ==================== 超时配置 ====================

        // 请求超时时间（毫秒）
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000); // 30s

        // 消息发送超时时间（毫秒）
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000); // 120s

        // 创建 Producer
        KafkaProducer<String, String> producer = new KafkaProducer<>(props);
        log.info("Kafka Producer 初始化成功: bootstrap.servers={}, acks={}, retries={}, " +
                "batch.size={}, linger.ms={}, compression={}, idempotence={}",
                bootstrapServers, acks, retries, batchSize, lingerMs, compressionType,
                enableIdempotence);

        return producer;
    }

    // ==================== 发送方法 ====================

    /**
     * 异步发送消息（自动选择分区）
     *
     * @param topic Topic名称
     * @param key   消息Key（用于分区）
     * @param value 消息内容
     * @return Future对象，可用于获取发送结果
     */
    public Future<RecordMetadata> send(String topic, String key, String value) {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);
        return producer.send(record, (metadata, exception) -> {
            if (exception != null) {
                log.error("消息发送失败: topic={}, key={}, value={}",
                        topic, key, value, exception);
            } else {
                log.debug("消息发送成功: topic={}, partition={}, offset={}",
                        metadata.topic(), metadata.partition(), metadata.offset());
            }
        });
    }

    /**
     * 异步发送消息（指定分区）
     *
     * @param topic     Topic名称
     * @param partition 分区号
     * @param key       消息Key
     * @param value     消息内容
     * @return Future对象，可用于获取发送结果
     */
    public Future<RecordMetadata> send(String topic, Integer partition, String key, String value) {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, partition, key, value);
        return producer.send(record, (metadata, exception) -> {
            if (exception != null) {
                log.error("消息发送失败: topic={}, partition={}, key={}",
                        topic, partition, key, exception);
            } else {
                log.debug("消息发送成功: topic={}, partition={}, offset={}",
                        metadata.topic(), metadata.partition(), metadata.offset());
            }
        });
    }

    /**
     * 同步发送消息（阻塞等待结果）
     *
     * @param topic Topic名称
     * @param key   消息Key
     * @param value 消息内容
     * @return RecordMetadata 元数据信息
     * @throws RuntimeException 发送失败时抛出异常
     */
    public RecordMetadata sendSync(String topic, String key, String value) {
        try {
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);
            Future<RecordMetadata> future = producer.send(record);
            RecordMetadata metadata = future.get();
            log.debug("同步发送成功: topic={}, partition={}, offset={}",
                    metadata.topic(), metadata.partition(), metadata.offset());
            return metadata;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("同步发送被中断: topic={}, key={}", topic, key, e);
            throw new RuntimeException("发送被中断", e);
        } catch (ExecutionException e) {
            log.error("同步发送失败: topic={}, key={}", topic, key, e);
            throw new RuntimeException("发送失败: " + e.getCause().getMessage(), e.getCause());
        }
    }

    /**
     * 异步发送消息（带自定义回调）
     *
     * @param topic     Topic名称
     * @param key       消息Key
     * @param value     消息内容
     * @param callback  自定义回调函数
     * @return Future对象
     */
    public Future<RecordMetadata> sendAsync(String topic, String key, String value, Callback callback) {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);
        return producer.send(record, callback);
    }

    /**
     * 刷新缓冲区（阻塞等待所有消息发送完成）
     */
    public void flush() {
        producer.flush();
        log.debug("Kafka Producer 缓冲区已刷新");
    }

    // ==================== 统计方法 ====================

    /**
     * 获取 Producer 统计信息
     *
     * @return MetricName -> Metric 映射
     */
    public Map<MetricName, ? extends Metric> getMetrics() {
        return producer.metrics();
    }

    /**
     * 获取统计信息摘要
     *
     * @return 统计信息字符串
     */
    public String getStatsSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("KafkaProducerManager{\n");
        sb.append("  bootstrapServers='").append(bootstrapServers).append("'\n");

        Map<MetricName, ? extends Metric> metrics = getMetrics();
        int recordSendTotal = 0;
        int recordErrorTotal = 0;
        double requestLatencyAvg = 0.0;

        for (Map.Entry<MetricName, ? extends Metric> entry : metrics.entrySet()) {
            MetricName name = entry.getKey();
            Metric metric = entry.getValue();
            String metricName = name.name();

            switch (metricName) {
                case "record-send-total":
                    recordSendTotal = (int) metric.metricValue();
                    break;
                case "record-error-total":
                    recordErrorTotal = (int) metric.metricValue();
                    break;
                case "request-latency-avg":
                    requestLatencyAvg = (double) metric.metricValue();
                    break;
            }
        }

        sb.append("  recordSendTotal=").append(recordSendTotal).append("\n");
        sb.append("  recordErrorTotal=").append(recordErrorTotal).append("\n");
        sb.append("  requestLatencyAvg=").append(String.format("%.2f", requestLatencyAvg)).append("ms\n");
        sb.append("}");

        return sb.toString();
    }

    // ==================== 资源管理 ====================

    /**
     * 关闭 Producer（阻塞等待消息发送完成）
     */
    public void close() {
        if (producer != null) {
            producer.close();
            log.info("Kafka Producer 已关闭");
        }
    }

    /**
     * 关闭 Producer（带超时）
     *
     * @param timeoutMs 超时时间（毫秒）
     */
    public void close(long timeoutMs) {
        if (producer != null) {
            producer.close(java.time.Duration.ofMillis(timeoutMs));
            log.info("Kafka Producer 已关闭（超时: {}ms）", timeoutMs);
        }
    }
}
