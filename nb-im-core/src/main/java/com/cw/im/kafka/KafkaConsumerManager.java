package com.cw.im.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Kafka 消费者管理器 - 增强版
 *
 * <p>支持手动提交Offset、指定分区消费、批量消费、消息过滤和完善异常处理</p>
 *
 * <h3>核心功能</h3>
 * <ul>
 *     <li>订阅Topic（消费者组模式）</li>
 *     <li>手动分配分区（非消费者组模式）</li>
 *     <li>手动提交Offset（同步/异步）</li>
 *     <li>批量消费消息</li>
 *     <li>消息过滤</li>
 *     <li>完善的异常处理</li>
 * </ul>
 *
 * @author cw
 * @since 1.0.0
 */
@Slf4j
public class KafkaConsumerManager {

    private static final String DEFAULT_BOOTSTRAP_SERVERS = "192.168.1.48:9092";
    private static final String DEFAULT_GROUP_ID = "im-gateway-group";
    private static final String DEFAULT_AUTO_OFFSET_RESET = "earliest";
    private static final int DEFAULT_MAX_POLL_RECORDS = 500;
    private static final int DEFAULT_MAX_POLL_INTERVAL_MS = 300000; // 5分钟
    private static final int DEFAULT_SESSION_TIMEOUT_MS = 30000; // 30秒
    private static final int DEFAULT_HEARTBEAT_INTERVAL_MS = 10000; // 10秒

    private final KafkaConsumer<String, String> consumer;
    private final String bootstrapServers;
    private final String groupId;
    private volatile boolean running = false;

    /**
     * 默认构造函数
     */
    public KafkaConsumerManager() {
        this(DEFAULT_BOOTSTRAP_SERVERS, DEFAULT_GROUP_ID);
    }

    /**
     * 指定服务器和组ID构造函数
     *
     * @param bootstrapServers Kafka集群地址
     * @param groupId          消费者组ID
     */
    public KafkaConsumerManager(String bootstrapServers, String groupId) {
        this(bootstrapServers, groupId, false, DEFAULT_AUTO_OFFSET_RESET,
             DEFAULT_MAX_POLL_RECORDS, DEFAULT_MAX_POLL_INTERVAL_MS,
             DEFAULT_SESSION_TIMEOUT_MS, DEFAULT_HEARTBEAT_INTERVAL_MS);
    }

    /**
     * 完整参数构造函数
     *
     * @param bootstrapServers     Kafka集群地址
     * @param groupId              消费者组ID
     * @param enableAutoCommit     是否自动提交Offset
     * @param autoOffsetReset      Offset重置策略（earliest/latest/none）
     * @param maxPollRecords       单次poll最大记录数
     * @param maxPollIntervalMs    poll最大间隔时间（毫秒）
     * @param sessionTimeoutMs     会话超时时间（毫秒）
     * @param heartbeatIntervalMs  心跳间隔时间（毫秒）
     */
    public KafkaConsumerManager(String bootstrapServers, String groupId,
                                boolean enableAutoCommit, String autoOffsetReset,
                                int maxPollRecords, int maxPollIntervalMs,
                                int sessionTimeoutMs, int heartbeatIntervalMs) {
        this.bootstrapServers = bootstrapServers;
        this.groupId = groupId;
        this.consumer = initConsumer(bootstrapServers, groupId, enableAutoCommit,
                                    autoOffsetReset, maxPollRecords, maxPollIntervalMs,
                                    sessionTimeoutMs, heartbeatIntervalMs);
    }

    /**
     * 初始化 Kafka Consumer
     */
    private KafkaConsumer<String, String> initConsumer(String bootstrapServers, String groupId,
                                                       boolean enableAutoCommit,
                                                       String autoOffsetReset,
                                                       int maxPollRecords, int maxPollIntervalMs,
                                                       int sessionTimeoutMs, int heartbeatIntervalMs) {
        Properties props = new Properties();

        // ==================== 基础配置 ====================

        // Kafka 服务器地址
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        // 消费者组 ID
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);

        // Key 反序列化器
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        // Value 反序列化器
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        // ==================== Offset 配置 ====================

        // 是否自动提交 Offset
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, enableAutoCommit);

        // 自动提交间隔（毫秒，仅当enable.auto.commit=true时生效）
        props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, 5000);

        // 当没有初始 Offset 或 Offset 无效时的策略
        // earliest: 从最早的消息开始消费
        // latest: 从最新的消息开始消费
        // none: 抛出异常
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);

        // ==================== 性能配置 ====================

        // 单次 poll 最大拉取记录数
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecords);

        // poll 最大间隔时间（毫秒）
        // 如果超过这个时间没有poll，消费者将被认为失效
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, maxPollIntervalMs);

        // ==================== 会话配置 ====================

        // 会话超时时间（毫秒）
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, sessionTimeoutMs);

        // 心跳间隔时间（毫秒）
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, heartbeatIntervalMs);

        // ==================== 分区配置 ====================

        // 分区分配策略（范围分配）
        props.put(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG,
                "org.apache.kafka.clients.consumer.RangeAssignor");

        // 创建 Consumer
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        log.info("Kafka Consumer 初始化成功: bootstrap.servers={}, group.id={}, " +
                "enable.auto.commit={}, auto.offset.reset={}, max.poll.records={}",
                bootstrapServers, groupId, enableAutoCommit, autoOffsetReset, maxPollRecords);

        return consumer;
    }

    // ==================== 订阅方法 ====================

    /**
     * 订阅单个Topic（消费者组模式）
     *
     * @param topic Topic名称
     */
    public void subscribe(String topic) {
        consumer.subscribe(Collections.singletonList(topic));
        log.info("订阅 Topic 成功: topic={}, group={}", topic, groupId);
    }

    /**
     * 订阅多个Topic（消费者组模式）
     *
     * @param topics Topic集合
     */
    public void subscribe(Collection<String> topics) {
        consumer.subscribe(topics);
        log.info("订阅 Topics 成功: topics={}, group={}", topics, groupId);
    }

    /**
     * 手动分配分区（非消费者组模式）
     *
     * @param topic     Topic名称
     * @param partition 分区号
     */
    public void assign(String topic, int partition) {
        TopicPartition topicPartition = new TopicPartition(topic, partition);
        consumer.assign(Collections.singletonList(topicPartition));
        log.info("手动分配分区成功: topic={}, partition={}, group={}", topic, partition, groupId);
    }

    /**
     * 手动分配多个分区（非消费者组模式）
     *
     * @param partitions TopicPartition集合
     */
    public void assign(Collection<TopicPartition> partitions) {
        consumer.assign(partitions);
        log.info("手动分配多个分区成功: partitions={}, group={}", partitions, groupId);
    }

    // ==================== 消费方法 ====================

    /**
     * 消费消息（手动处理，带过滤）
     *
     * @param messageHandler 消息处理器
     * @param timeoutMs      超时时间（毫秒）
     */
    public void poll(Consumer<ConsumerRecord<String, String>> messageHandler, long timeoutMs) {
        poll(messageHandler, timeoutMs, null);
    }

    /**
     * 消费消息（手动处理，带过滤）
     *
     * @param messageHandler 消息处理器
     * @param timeoutMs      超时时间（毫秒）
     * @param filter         消息过滤器（null表示不过滤）
     */
    public void poll(Consumer<ConsumerRecord<String, String>> messageHandler,
                    long timeoutMs, Predicate<ConsumerRecord<String, String>> filter) {
        running = true;
        log.info("开始消费消息: group={}, timeout={}ms", groupId, timeoutMs);

        try {
            while (running) {
                try {
                    // 拉取消息
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(timeoutMs));

                    if (records.isEmpty()) {
                        continue;
                    }

                    log.debug("拉取到 {} 条消息", records.count());

                    // 处理消息
                    for (ConsumerRecord<String, String> record : records) {
                        try {
                            // 过滤消息
                            if (filter != null && !filter.test(record)) {
                                log.debug("消息被过滤: topic={}, partition={}, offset={}",
                                        record.topic(), record.partition(), record.offset());
                                continue;
                            }

                            // 调用消息处理器
                            messageHandler.accept(record);

                        } catch (Exception e) {
                            log.error("处理单条消息失败: topic={}, partition={}, offset={}",
                                    record.topic(), record.partition(), record.offset(), e);
                        }
                    }

                } catch (Exception e) {
                    log.error("消费消息异常", e);
                    // 可以选择继续或停止
                    // break;
                }
            }
        } finally {
            log.info("消费循环结束: group={}", groupId);
        }
    }

    /**
     * 批量消费消息
     *
     * @param batchHandler 批量消息处理器
     * @param timeoutMs    超时时间（毫秒）
     * @param batchSize    批量大小
     */
    public void pollBatch(Consumer<List<ConsumerRecord<String, String>>> batchHandler,
                         long timeoutMs, int batchSize) {
        running = true;
        log.info("开始批量消费消息: group={}, timeout={}ms, batchSize={}", groupId, timeoutMs, batchSize);

        try {
            List<ConsumerRecord<String, String>> buffer = new java.util.ArrayList<>(batchSize);

            while (running) {
                try {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(timeoutMs));

                    if (records.isEmpty()) {
                        // 刷新剩余消息
                        if (!buffer.isEmpty()) {
                            batchHandler.accept(buffer);
                            buffer.clear();
                        }
                        continue;
                    }

                    // 添加到缓冲区
                    for (ConsumerRecord<String, String> record : records) {
                        buffer.add(record);

                        // 达到批量大小，触发处理
                        if (buffer.size() >= batchSize) {
                            batchHandler.accept(buffer);
                            buffer.clear();
                        }
                    }

                } catch (Exception e) {
                    log.error("批量消费消息异常", e);
                }
            }

            // 刷新剩余消息
            if (!buffer.isEmpty()) {
                batchHandler.accept(buffer);
                buffer.clear();
            }

        } finally {
            log.info("批量消费循环结束: group={}", groupId);
        }
    }

    // ==================== Offset 提交方法 ====================

    /**
     * 同步提交当前Offset
     */
    public void commitSync() {
        try {
            consumer.commitSync();
            log.debug("同步提交 Offset 成功: group={}", groupId);
        } catch (Exception e) {
            log.error("同步提交 Offset 失败: group={}", groupId, e);
            throw new RuntimeException("提交Offset失败", e);
        }
    }

    /**
     * 同步提交指定Offset
     *
     * @param offsetMap Offset映射
     */
    public void commitSync(Map<TopicPartition, OffsetAndMetadata> offsetMap) {
        try {
            consumer.commitSync(offsetMap);
            log.debug("同步提交指定 Offset 成功: group={}, offsets={}", groupId, offsetMap);
        } catch (Exception e) {
            log.error("同步提交指定 Offset 失败: group={}", groupId, e);
            throw new RuntimeException("提交Offset失败", e);
        }
    }

    /**
     * 异步提交当前Offset
     */
    public void commitAsync() {
        consumer.commitAsync((offsets, exception) -> {
            if (exception != null) {
                log.error("异步提交 Offset 失败: group={}, offsets={}", groupId, offsets, exception);
            } else {
                log.debug("异步提交 Offset 成功: group={}, offsets={}", groupId, offsets);
            }
        });
    }

    /**
     * 异步提交指定Offset
     *
     * @param offsetMap Offset映射
     */
    public void commitAsync(Map<TopicPartition, OffsetAndMetadata> offsetMap) {
        consumer.commitAsync(offsetMap, (offsets, exception) -> {
            if (exception != null) {
                log.error("异步提交指定 Offset 失败: group={}, offsets={}", groupId, offsets, exception);
            } else {
                log.debug("异步提交指定 Offset 成功: group={}, offsets={}", groupId, offsets);
            }
        });
    }

    // ==================== 控制方法 ====================

    /**
     * 停止消费
     */
    public void stop() {
        running = false;
        log.info("停止消费: group={}", groupId);
    }

    /**
     * 获取消费者运行状态
     *
     * @return true-运行中, false-已停止
     */
    public boolean isRunning() {
        return running;
    }

    // ==================== 统计方法 ====================

    /**
     * 获取 Consumer 统计信息
     *
     * @return MetricName -> Metric 映射
     */
    public Map<MetricName, ? extends Metric> getMetrics() {
        return consumer.metrics();
    }

    /**
     * 获取统计信息摘要
     *
     * @return 统计信息字符串
     */
    public String getStatsSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("KafkaConsumerManager{\n");
        sb.append("  bootstrapServers='").append(bootstrapServers).append("'\n");
        sb.append("  groupId='").append(groupId).append("'\n");
        sb.append("  running=").append(running).append("\n");

        Map<MetricName, ? extends Metric> metrics = getMetrics();
        double recordsConsumedTotal = 0;
        double recordsLagMax = 0;
        double fetchRate = 0;

        for (Map.Entry<MetricName, ? extends Metric> entry : metrics.entrySet()) {
            MetricName name = entry.getKey();
            Metric metric = entry.getValue();
            String metricName = name.name();

            switch (metricName) {
                case "records-consumed-total":
                    recordsConsumedTotal = (double) metric.metricValue();
                    break;
                case "records-lag-max":
                    recordsLagMax = (double) metric.metricValue();
                    break;
                case "fetch-rate":
                    fetchRate = (double) metric.metricValue();
                    break;
            }
        }

        sb.append("  recordsConsumedTotal=").append((int) recordsConsumedTotal).append("\n");
        sb.append("  recordsLagMax=").append((int) recordsLagMax).append("\n");
        sb.append("  fetchRate=").append(String.format("%.2f", fetchRate)).append("/s\n");
        sb.append("}");

        return sb.toString();
    }

    // ==================== 资源管理 ====================

    /**
     * 关闭 Consumer
     */
    public void close() {
        if (consumer != null) {
            consumer.close();
            log.info("Kafka Consumer 已关闭: group={}", groupId);
        }
    }

    /**
     * 关闭 Consumer（带超时）
     *
     * @param timeoutMs 超时时间（毫秒）
     */
    public void close(long timeoutMs) {
        if (consumer != null) {
            consumer.close(Duration.ofMillis(timeoutMs));
            log.info("Kafka Consumer 已关闭: group={}, timeout={}ms", groupId, timeoutMs);
        }
    }

    /**
     * 唤醒消费者（主要用于退出阻塞的poll）
     */
    public void wakeup() {
        consumer.wakeup();
        log.info("唤醒消费者: group={}", groupId);
    }
}
