package com.cw.im.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * Kafka 消费者管理器 - 最小版本
 *
 * @author cw
 */
@Slf4j
public class KafkaConsumerManager {

    private static final String DEFAULT_BOOTSTRAP_SERVERS = "192.168.215.2:9092";
    private static final String DEFAULT_GROUP_ID = "im-gateway-group";

    private KafkaConsumer<String, String> consumer;
    private volatile boolean running = false;

    public KafkaConsumerManager() {
        this(DEFAULT_BOOTSTRAP_SERVERS, DEFAULT_GROUP_ID);
    }

    public KafkaConsumerManager(String bootstrapServers, String groupId) {
        init(bootstrapServers, groupId);
    }

    /**
     * 初始化 Kafka Consumer
     */
    private void init(String bootstrapServers, String groupId) {
        Properties props = new Properties();

        // Kafka 服务器地址
        props.put("bootstrap.servers", bootstrapServers);

        // 消费者组 ID
        props.put("group.id", groupId);

        // Key 反序列化器
        props.put("key.deserializer", StringDeserializer.class.getName());

        // Value 反序列化器
        props.put("value.deserializer", StringDeserializer.class.getName());

        // 自动提交 Offset（生产环境建议手动提交）
        props.put("enable.auto.commit", "true");

        // 自动提交间隔（毫秒）
        props.put("auto.commit.interval.ms", "1000");

        // 当没有初始 Offset 或 Offset 无效时的策略
        // earliest: 从最早的消息开始消费
        // latest: 从最新的消息开始消费
        props.put("auto.offset.reset", "earliest");

        this.consumer = new KafkaConsumer<>(props);
        log.info("Kafka Consumer 初始化成功, bootstrap.servers={}, group.id={}",
            bootstrapServers, groupId);
    }

    /**
     * 订阅 Topic
     */
    public void subscribe(String topic) {
        consumer.subscribe(Collections.singletonList(topic));
        log.info("订阅 Topic 成功: {}", topic);
    }

    /**
     * 手动分配分区（不使用消费者组）
     */
    public void assign(String topic, int partition) {
        TopicPartition topicPartition = new TopicPartition(topic, partition);
        consumer.assign(Collections.singletonList(topicPartition));
        log.info("手动分配分区成功: topic={}, partition={}", topic, partition);
    }

    /**
     * 开始消费（阻塞方法）
     */
    public void startConsume(Consumer<ConsumerRecord<String, String>> messageHandler) {
        running = true;
        log.info("开始消费消息...");

        try {
            while (running) {
                // 拉取消息（超时时间 100ms）
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));

                for (ConsumerRecord<String, String> record : records) {
                    try {
                        log.debug("收到消息: topic={}, partition={}, offset={}, key={}, value={}",
                            record.topic(), record.partition(), record.offset(),
                            record.key(), record.value());

                        // 调用消息处理器
                        messageHandler.accept(record);

                    } catch (Exception e) {
                        log.error("处理消息失败: topic={}, partition={}, offset={}",
                            record.topic(), record.partition(), record.offset(), e);
                    }
                }
            }
        } finally {
            close();
        }
    }

    /**
     * 停止消费
     */
    public void stop() {
        running = false;
        log.info("停止消费");
    }

    /**
     * 关闭 Consumer
     */
    public void close() {
        if (consumer != null) {
            consumer.close();
            log.info("Kafka Consumer 已关闭");
        }
    }
}
