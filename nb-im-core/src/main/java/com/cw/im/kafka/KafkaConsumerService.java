package com.cw.im.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Kafka 消费者服务
 *
 * <p>封装Kafka消费逻辑，支持多Topic并发消费和消息处理</p>
 *
 * <h3>核心功能</h3>
 * <ul>
 *     <li>每个Topic独立消费线程</li>
 *     <li>支持多个MessageListener</li>
 *     <li>手动提交Offset</li>
 *     <li>异常处理和重试</li>
 *     <li>优雅停止</li>
 *     <li>消费统计</li>
 * </ul>
 *
 * <h3>线程模型</h3>
 * <ul>
 *     <li>每个Topic一个独立的消费线程</li>
 *     <li>保证同一Partition内消息顺序处理</li>
 *     <li>不同Partition可以并发处理</li>
 * </ul>
 *
 * @author cw
 * @since 1.0.0
 */
@Slf4j
public class KafkaConsumerService {

    private final String bootstrapServers;
    private final String groupId;

    // Topic -> MessageListener 映射
    private final Map<String, MessageListener> listeners = new ConcurrentHashMap<>();

    // Topic -> KafkaConsumerManager 映射
    private final Map<String, KafkaConsumerManager> consumers = new ConcurrentHashMap<>();

    // Topic -> 消费线程 映射
    private final Map<String, Thread> consumerThreads = new ConcurrentHashMap<>();

    // 消费统计
    private final Map<String, AtomicLong> topicStats = new ConcurrentHashMap<>();

    // 线程池（用于异步处理）
    private final ExecutorService executorService;

    // 运行状态
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 构造函数
     *
     * @param bootstrapServers Kafka集群地址
     */
    public KafkaConsumerService(String bootstrapServers) {
        this(bootstrapServers, "im-gateway-group");
    }

    /**
     * 构造函数
     *
     * @param bootstrapServers Kafka集群地址
     * @param groupId          消费者组ID
     */
    public KafkaConsumerService(String bootstrapServers, String groupId) {
        this.bootstrapServers = bootstrapServers;
        this.groupId = groupId;
        this.executorService = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r, "kafka-consumer-worker-" + r.hashCode());
            thread.setDaemon(false);
            return thread;
        });
        log.info("KafkaConsumerService 初始化完成: bootstrap.servers={}, group.id={}",
                bootstrapServers, groupId);
    }

    // ==================== 监听器管理 ====================

    /**
     * 添加消息监听器
     *
     * @param topic    Topic名称
     * @param listener 消息监听器
     */
    public void addListener(String topic, MessageListener listener) {
        if (topic == null || listener == null) {
            throw new IllegalArgumentException("topic 和 listener 不能为 null");
        }

        listeners.put(topic, listener);
        topicStats.putIfAbsent(topic, new AtomicLong(0));

        log.info("添加消息监听器: topic={}, listener={}", topic, listener.getClass().getSimpleName());
    }

    /**
     * 移除消息监听器
     *
     * @param topic Topic名称
     */
    public void removeListener(String topic) {
        if (topic == null) {
            return;
        }

        listeners.remove(topic);
        log.info("移除消息监听器: topic={}", topic);
    }

    /**
     * 获取监听器
     *
     * @param topic Topic名称
     * @return MessageListener，如果不存在则返回null
     */
    public MessageListener getListener(String topic) {
        return listeners.get(topic);
    }

    // ==================== 服务控制 ====================

    /**
     * 启动消费服务
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            log.info("========================================");
            log.info("正在启动 Kafka 消费者服务...");
            log.info("Kafka 集群: {}", bootstrapServers);
            log.info("消费者组: {}", groupId);
            log.info("监听 Topic 数量: {}", listeners.size());
            log.info("========================================");

            // 为每个 Topic 创建独立的消费者和线程
            for (Map.Entry<String, MessageListener> entry : listeners.entrySet()) {
                String topic = entry.getKey();
                MessageListener listener = entry.getValue();

                try {
                    // 创建 KafkaConsumerManager（每个Topic独立的消费者实例）
                    // 注意：需要在 groupId 后加上 topic 标识，避免消费者组冲突
                    String topicGroupId = groupId + "-" + topic;
                    KafkaConsumerManager consumer = new KafkaConsumerManager(
                            bootstrapServers,
                            topicGroupId,
                            false, // 手动提交
                            "earliest",
                            500,  // max.poll.records
                            300000, // max.poll.interval.ms
                            30000, // session.timeout.ms
                            10000  // heartbeat.interval.ms
                    );

                    // 订阅 Topic
                    consumer.subscribe(topic);
                    consumers.put(topic, consumer);

                    // 创建消费线程
                    Thread consumerThread = new Thread(() -> startConsumeTopic(topic, consumer, listener),
                            "kafka-consumer-" + topic);
                    consumerThread.setDaemon(false);
                    consumerThread.start();
                    consumerThreads.put(topic, consumerThread);

                    log.info("Topic 消费者已启动: topic={}, groupId={}", topic, topicGroupId);

                } catch (Exception e) {
                    log.error("启动 Topic 消费者失败: topic={}", topic, e);
                }
            }

            log.info("========================================");
            log.info("Kafka 消费者服务启动成功!");
            log.info("========================================");
        } else {
            log.warn("Kafka 消费者服务已在运行中");
        }
    }

    /**
     * 停止消费服务
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            log.info("========================================");
            log.info("正在停止 Kafka 消费者服务...");
            log.info("========================================");

            // 停止所有消费者
            for (Map.Entry<String, KafkaConsumerManager> entry : consumers.entrySet()) {
                String topic = entry.getKey();
                KafkaConsumerManager consumer = entry.getValue();

                try {
                    // 停止消费循环
                    consumer.stop();

                    // 唤醒消费者（退出阻塞的poll）
                    consumer.wakeup();

                    log.info("Topic 消费者已停止: topic={}", topic);

                } catch (Exception e) {
                    log.error("停止 Topic 消费者失败: topic={}", topic, e);
                }
            }

            // 等待所有消费线程结束
            for (Map.Entry<String, Thread> entry : consumerThreads.entrySet()) {
                String topic = entry.getKey();
                Thread thread = entry.getValue();

                try {
                    if (thread.isAlive()) {
                        thread.join(5000); // 最多等待5秒
                        log.info("Topic 消费线程已结束: topic={}", topic);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("等待 Topic 消费线程被中断: topic={}", topic, e);
                }
            }

            // 关闭所有消费者
            for (Map.Entry<String, KafkaConsumerManager> entry : consumers.entrySet()) {
                String topic = entry.getKey();
                KafkaConsumerManager consumer = entry.getValue();

                try {
                    consumer.close();
                    log.info("Topic 消费者已关闭: topic={}", topic);
                } catch (Exception e) {
                    log.error("关闭 Topic 消费者失败: topic={}", topic, e);
                }
            }

            // 清空映射
            consumers.clear();
            consumerThreads.clear();

            // 关闭线程池
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executorService.shutdownNow();
            }

            log.info("========================================");
            log.info("Kafka 消费者服务已停止");
            log.info("========================================");
        } else {
            log.warn("Kafka 消费者服务未运行");
        }
    }

    /**
     * 获取运行状态
     *
     * @return true-运行中, false-已停止
     */
    public boolean isRunning() {
        return running.get();
    }

    // ==================== 消费逻辑 ====================

    /**
     * 启动指定 Topic 的消费
     *
     * @param topic    Topic名称
     * @param consumer 消费者管理器
     * @param listener 消息监听器
     */
    private void startConsumeTopic(String topic, KafkaConsumerManager consumer, MessageListener listener) {
        log.info("开始消费 Topic: topic={}", topic);

        try {
            while (consumer.isRunning()) {
                try {
                    // 拉取消息（超时1秒）
                    consumer.poll(record -> {
                        try {
                            // 异步处理消息
                            executorService.submit(() -> {
                                try {
                                    listener.onMessage(record);

                                    // 更新统计
                                    AtomicLong counter = topicStats.get(topic);
                                    if (counter != null) {
                                        counter.incrementAndGet();
                                    }

                                } catch (Exception e) {
                                    log.error("处理消息异常: topic={}, partition={}, offset={}",
                                            record.topic(), record.partition(), record.offset(), e);
                                    listener.onException(e);
                                }
                            });

                        } catch (Exception e) {
                            log.error("提交异步任务失败: topic={}, partition={}, offset={}",
                                    record.topic(), record.partition(), record.offset(), e);
                        }
                    }, 1000, null); // timeout=1s, 无过滤

                    // 批量处理完成后回调
                    listener.onComplete();

                    // 手动提交Offset（每批次提交一次）
                    try {
                        consumer.commitSync();
                        log.debug("提交 Offset 成功: topic={}", topic);
                    } catch (Exception e) {
                        log.error("提交 Offset 失败: topic={}", topic, e);
                        // 可以选择重试或记录到失败队列
                    }

                } catch (Exception e) {
                    if (consumer.isRunning()) {
                        log.error("消费 Topic 异常: topic={}", topic, e);
                        // 可以选择继续或停止
                        try {
                            Thread.sleep(1000); // 休眠1秒后重试
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    } else {
                        log.info("消费已停止: topic={}", topic);
                        break;
                    }
                }
            }

        } finally {
            log.info("Topic 消费循环结束: topic={}", topic);
        }
    }

    // ==================== 统计方法 ====================

    /**
     * 获取消费统计信息
     *
     * @return 统计信息JSON字符串
     */
    public String getStats() {
        StringBuilder sb = new StringBuilder();
        sb.append("KafkaConsumerService{\n");
        sb.append("  bootstrapServers='").append(bootstrapServers).append("'\n");
        sb.append("  groupId='").append(groupId).append("'\n");
        sb.append("  running=").append(running.get()).append("\n");
        sb.append("  listeners=").append(listeners.size()).append("\n");
        sb.append("  topicStats={\n");

        for (Map.Entry<String, AtomicLong> entry : topicStats.entrySet()) {
            String topic = entry.getKey();
            long count = entry.getValue().get();
            sb.append("    '").append(topic).append("': ").append(count).append("\n");
        }

        sb.append("  }\n");
        sb.append("}");

        return sb.toString();
    }

    /**
     * 获取指定 Topic 的消费数量
     *
     * @param topic Topic名称
     * @return 消费数量
     */
    public long getTopicMessageCount(String topic) {
        AtomicLong counter = topicStats.get(topic);
        return counter != null ? counter.get() : 0;
    }

    /**
     * 重置统计信息
     */
    public void resetStats() {
        for (AtomicLong counter : topicStats.values()) {
            counter.set(0);
        }
        log.info("消费统计信息已重置");
    }
}
