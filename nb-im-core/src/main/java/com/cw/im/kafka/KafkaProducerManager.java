package com.cw.im.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Kafka 生产者管理器 - 最小版本
 *
 * @author cw
 */
@Slf4j
public class KafkaProducerManager {

    private static final String DEFAULT_BOOTSTRAP_SERVERS = "192.168.215.2:9092";

    private KafkaProducer<String, String> producer;

    public KafkaProducerManager() {
        this(DEFAULT_BOOTSTRAP_SERVERS);
    }

    public KafkaProducerManager(String bootstrapServers) {
        init(bootstrapServers);
    }

    /**
     * 初始化 Kafka Producer
     */
    private void init(String bootstrapServers) {
        Properties props = new Properties();

        // Kafka 服务器地址
        props.put("bootstrap.servers", bootstrapServers);

        // Key 序列化器
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");

        // Value 序列化器
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

        // ACK 确认级别
        props.put("acks", "all");

        // 启用幂等性（防止重复）
        props.put("enable.idempotence", "true");


        // 重试次数
        props.put("retries", "3");

        // 批量发送大小（字节）
        props.put("batch.size", "16384");

        // 等待批量发送的时间（毫秒）
        props.put("linger.ms", "10");

        this.producer = new KafkaProducer<>(props);
        log.info("Kafka Producer 初始化成功, bootstrap.servers={}", bootstrapServers);
    }

    /**
     * 异步发送消息
     */
    public Future<RecordMetadata> sendAsync(String topic, String key, String value) {
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
     * 同步发送消息
     */
    public RecordMetadata sendSync(String topic, String key, String value) {
        try {
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);
            Future<RecordMetadata> future = producer.send(record);
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            log.error("同步发送消息失败: topic={}, key={}", topic, key, e);
            throw new RuntimeException("发送失败", e);
        }
    }

    /**
     * 关闭 Producer
     */
    public void close() {
        if (producer != null) {
            producer.close();
            log.info("Kafka Producer 已关闭");
        }
    }
}
