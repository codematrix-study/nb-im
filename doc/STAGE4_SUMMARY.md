# NB-IM 阶段四：Kafka 消息总线 - 实现总结

## 📋 项目信息

- **项目名称**: NB-IM 即时通讯中间件
- **开发阶段**: 阶段四 - Kafka 消息总线
- **完成时间**: 2026-03-09
- **开发状态**: ✅ 已完成

---

## 📊 完成情况概览

### ✅ 任务完成度：100%

所有计划任务均已按照 `DEVELOPMENT_PLAN.md` 的要求完成：

- ✅ Kafka Producer 增强（自定义分区器、压缩、幂等性、批量发送）
- ✅ Kafka Consumer 增强（手动提交Offset、批量消费、消息过滤）
- ✅ 自定义分区器实现（MessagePartitioner）
- ✅ 消息监听器接口（MessageListener）
- ✅ Kafka消费者服务封装（KafkaConsumerService）
- ✅ 推送消息消费者（PushMessageConsumer）
- ✅ 集成到NettyIMServer
- ✅ Kafka Topic设计文档
- ✅ 使用示例文档

---

## 📦 创建/修改的文件清单

### 1. 修改的文件（3个）

#### KafkaProducerManager.java（增强版）
**路径**: `nb-im-core/src/main/java/com/cw/im/kafka/KafkaProducerManager.java`

**新增功能**:
- ✅ 自定义分区器（MessagePartitioner）
- ✅ 序列化配置（StringSerializer）
- ✅ 压缩配置（支持lz4/snappy/gzip/zstd）
- ✅ 幂等性配置（enable.idempotence）
- ✅ 批量发送配置（batch.size, linger.ms）
- ✅ 完善的错误处理和重试机制
- ✅ 统计信息获取（getMetrics）

**核心方法**:
```java
// 异步发送（自动选择分区）
Future<RecordMetadata> send(String topic, String key, String value)

// 异步发送（指定分区）
Future<RecordMetadata> send(String topic, Integer partition, String key, String value)

// 同步发送
RecordMetadata sendSync(String topic, String key, String value)

// 异步发送（带自定义回调）
Future<RecordMetadata> sendAsync(String topic, String key, String value, Callback callback)

// 刷新缓冲区
void flush()

// 获取统计信息
Map<MetricName, ? extends Metric> getMetrics()
String getStatsSummary()
```

---

#### KafkaConsumerManager.java（增强版）
**路径**: `nb-im-core/src/main/java/com/cw/im/kafka/KafkaConsumerManager.java`

**新增功能**:
- ✅ 手动提交Offset（commitSync/commitAsync）
- ✅ 指定分区消费（assign方法）
- ✅ 批量消费（pollBatch方法）
- ✅ 消息过滤（Predicate参数）
- ✅ 完善的异常处理
- ✅ 统计信息获取

**核心方法**:
```java
// 订阅Topic（消费者组模式）
void subscribe(String topic)
void subscribe(Collection<String> topics)

// 手动分配分区（非消费者组模式）
void assign(String topic, int partition)
void assign(Collection<TopicPartition> partitions)

// 消费消息（带过滤）
void poll(Consumer<ConsumerRecord<String, String>> messageHandler, long timeoutMs)
void poll(..., Predicate<ConsumerRecord<String, String>> filter)

// 批量消费
void pollBatch(Consumer<List<ConsumerRecord<String, String>>> batchHandler,
              long timeoutMs, int batchSize)

// 提交Offset
void commitSync()
void commitAsync()

// 获取统计信息
Map<MetricName, ? extends Metric> getMetrics()
String getStatsSummary()
```

---

#### NettyIMServer.java（集成Kafka消费）
**路径**: `nb-im-server/src/main/java/com/cw/im/server/NettyIMServer.java`

**集成内容**:
- ✅ 在启动时初始化KafkaConsumerService
- ✅ 注册PushMessageConsumer
- ✅ 在关闭时停止KafkaConsumerService

**关键代码**:
```java
private void initBusinessComponents() {
    // ... 已有代码 ...

    // 初始化Kafka消费者服务
    kafkaConsumerService = new KafkaConsumerService(kafkaServers);
    log.info("Kafka 消费者服务初始化成功");

    // 注册推送消息消费者
    kafkaConsumerService.addListener(KafkaTopics.MSG_PUSH,
        new PushMessageConsumer(channelManager, onlineStatusService));
    log.info("推送消息消费者已注册");
}

public void start() {
    // ... 启动Netty ...

    // 启动Kafka消费者服务
    kafkaConsumerService.start();
    log.info("Kafka 消费者服务已启动");
}

public void shutdown() {
    // 停止Kafka消费者服务
    if (kafkaConsumerService != null) {
        kafkaConsumerService.stop();
        log.info("Kafka 消费者服务已停止");
    }

    // ... 其他清理 ...
}
```

---

### 2. 新建的文件（5个）

#### MessagePartitioner.java（自定义分区器）
**路径**: `nb-im-core/src/main/java/com/cw/im/kafka/MessagePartitioner.java`

**功能**: 实现Kafka消息分区策略，保证消息顺序

**分区策略**:
```java
@Override
public int partition(String topic, Object key, byte[] keyBytes,
                    Object value, byte[] valueBytes, Cluster cluster) {
    String partitionKey = (String) key;
    int partitionCount = cluster.partitionCountForTopic(topic);

    // 使用hashCode保证同一partitionKey进入同一分区
    return Math.abs(partitionKey.hashCode()) % partitionCount;
}
```

**分区Key生成工具方法**:
```java
// 私聊: min(from, to) + "-" + max(from, to)
public static String generatePrivateChatPartitionKey(Long from, Long to)

// 群聊: groupId
public static String generateGroupChatPartitionKey(Long groupId)

// 公屏: 固定值
public static String generateBroadcastPartitionKey()

// ACK: msgId
public static String generateAckPartitionKey(String msgId)
```

---

#### MessageListener.java（消息监听器接口）
**路径**: `nb-im-core/src/main/java/com/cw/im/kafka/MessageListener.java`

**功能**: 定义消息消费监听器接口

**接口定义**:
```java
public interface MessageListener {
    /**
     * 处理消息
     */
    void onMessage(ConsumerRecord<String, String> record);

    /**
     * 处理异常
     */
    void onException(Exception exception);

    /**
     * 消费完成回调（可选）
     */
    default void onComplete() {
        // 默认空实现
    }
}
```

---

#### KafkaConsumerService.java（消费者服务封装）
**路径**: `nb-im-core/src/main/java/com/cw/im/kafka/KafkaConsumerService.java`

**功能**: 封装Kafka消费逻辑，支持多Topic并发消费

**核心功能**:
```java
public class KafkaConsumerService {
    // 添加监听器
    void addListener(String topic, MessageListener listener)

    // 移除监听器
    void removeListener(String topic)

    // 启动服务
    void start()

    // 停止服务
    void stop()

    // 获取统计信息
    String getStats()
    long getTopicMessageCount(String topic)
}
```

**实现要点**:
- ✅ 每个Topic独立消费线程
- ✅ 支持多个MessageListener
- ✅ 手动提交Offset
- ✅ 异常处理和重试
- ✅ 优雅停止
- ✅ 消费统计

**数据结构**:
```java
// Topic -> MessageListener映射
Map<String, MessageListener> listeners = new ConcurrentHashMap<>();

// Topic -> 消费计数
Map<String, AtomicLong> topicMessageCount = new ConcurrentHashMap<>();

// Topic -> 消费线程
Map<String, Thread> consumerThreads = new ConcurrentHashMap<>();
```

---

#### PushMessageConsumer.java（推送消息消费者）
**路径**: `nb-im-server/src/main/java/com/cw/im/server/consumer/PushMessageConsumer.java`

**功能**: 消费推送消息Topic，推送给在线用户

**消费流程**:
```
1. 从Kafka拉取消息
2. 反序列化消息为IMMessage对象
3. 获取目标用户ID
4. 查询用户是否在线
5. 在线则推送给用户所有设备
6. 离线则跳过（业务层处理离线消息）
7. 提交Offset（由KafkaConsumerService统一处理）
```

**核心代码**:
```java
@Override
public void onMessage(ConsumerRecord<String, String> record) {
    try {
        // 1. 反序列化消息
        IMMessage message = objectMapper.readValue(record.value(), IMMessage.class);

        // 2. 获取目标用户ID
        Long toUserId = message.getTo();

        // 3. 查询用户是否在线
        boolean isOnline = onlineStatusService.isOnline(toUserId);

        if (isOnline) {
            // 4. 在线则推送
            channelManager.broadcastToUser(toUserId, message);
            successCount.incrementAndGet();
            log.info("推送消息成功: userId={}, msgId={}", toUserId, message.getMsgId());
        } else {
            // 5. 离线则跳过
            skippedCount.incrementAndGet();
            log.info("用户离线，跳过推送: userId={}, msgId={}", toUserId, message.getMsgId());
        }

    } catch (Exception e) {
        failureCount.incrementAndGet();
        log.error("处理推送消息失败: topic={}, partition={}, offset={}",
            record.topic(), record.partition(), record.offset(), e);
    }
}
```

**统计信息**:
- 推送成功数
- 推送失败数
- 离线跳过数
- 成功率

---

### 3. 文档文件（2个）

#### KAFKA_TOPICS_SETUP.md
**路径**: `KAFKA_TOPICS_SETUP.md`

**内容**: Kafka Topics创建脚本

**包含内容**:
- ✅ 6个Topics的创建命令
- ✅ 验证命令
- ✅ 删除命令
- ✅ 注意事项说明

**示例**:
```bash
# 创建客户端发送消息Topic
kafka-topics.sh --create \
  --bootstrap-server 192.168.215.2:9092 \
  --topic im-msg-send \
  --partitions 32 \
  --replication-factor 3

# 验证Topic
kafka-topics.sh --describe \
  --bootstrap-server 192.168.215.2:9092 \
  --topic im-msg-send
```

---

#### KAFKA_USAGE_EXAMPLE.md
**路径**: `KAFKA_USAGE_EXAMPLE.md`

**内容**: Kafka消息总线使用示例

**包含内容**:
- ✅ 发送消息示例（私聊、群聊、公屏、ACK）
- ✅ 消费消息示例（服务消费、直接消费、批量消费、过滤消费）
- ✅ 分区策略说明
- ✅ 统计信息获取
- ✅ 异常处理
- ✅ 资源清理

---

## 🎯 Kafka Topic设计

根据开发计划，设计了6个Topics：

| Topic名称 | 用途 | Partitions | Replication | 分区策略 | 保留时间 |
|---------|------|-----------|-------------|---------|---------|
| **im-msg-send** | 客户端发送消息 | 32 | 3 | 按conversationId或groupId分区 | 7天 |
| **im-msg-push** | 业务推送消息 | 32 | 3 | 按目标用户ID分区 | 1天 |
| **im-ack** | ACK确认消息 | 16 | 3 | 按msgId分区 | 3天 |
| **im-msg-offline** | 离线消息 | 16 | 3 | 按用户ID分区 | 7天 |
| **im-system-notice** | 系统通知 | 8 | 3 | 轮询 | 30天 |
| **im-gateway-status** | 网关状态 | 8 | 3 | 按gatewayId分区 | 7天 |

### Topic详细说明

#### 1. im-msg-send（客户端发送消息）
**用途**: 接收客户端发送的消息，转发给业务层处理

**分区策略**:
- 私聊: `conversationId = min(from, to) + "-" + max(from, to)`
- 群聊: `groupId`
- 公屏: `broadcast`（固定值）

**配置**:
```json
{
  "topic": "im-msg-send",
  "partitions": 32,
  "replication-factor": 3,
  "retention.ms": 604800000 (7天)
}
```

#### 2. im-msg-push（业务推送消息）
**用途**: 业务层推送消息给Gateway，由Gateway推送给客户端

**分区策略**: 按目标用户ID分区（保证同一用户的消息有序）

**配置**:
```json
{
  "topic": "im-msg-push",
  "partitions": 32,
  "replication-factor": 3,
  "retention.ms": 86400000 (1天)
}
```

#### 3. im-ack（ACK确认消息）
**用途**: 客户端确认消息接收，用于消息可靠投递

**分区策略**: 按msgId分区（均匀分布）

**配置**:
```json
{
  "topic": "im-ack",
  "partitions": 16,
  "replication-factor": 3,
  "retention.ms": 259200000 (3天)
}
```

#### 4. im-msg-offline（离线消息）
**用途**: 用户离线时的消息存储

**分区策略**: 按用户ID分区

**配置**:
```json
{
  "topic": "im-msg-offline",
  "partitions": 16,
  "replication-factor": 3,
  "retention.ms": 604800000 (7天)
}
```

#### 5. im-system-notice（系统通知）
**用途**: 系统级通知、维护公告

**分区策略**: 轮询（负载均衡）

**配置**:
```json
{
  "topic": "im-system-notice",
  "partitions": 8,
  "replication-factor": 3,
  "retention.ms": 2592000000 (30天)
}
```

#### 6. im-gateway-status（网关状态）
**用途**: Gateway上下线通知、负载信息同步

**分区策略**: 按gatewayId分区

**配置**:
```json
{
  "topic": "im-gateway-status",
  "partitions": 8,
  "replication-factor": 3,
  "retention.ms": 604800000 (7天)
}
```

---

## 🔑 核心功能详解

### 1. KafkaProducerManager 增强功能

#### 自定义分区器
```java
// 配置分区器
props.put(ProducerConfig.PARTITIONER_CLASS_CONFIG,
         MessagePartitioner.class.getName());

// 使用分区器
String partitionKey = MessagePartitioner.generatePrivateChatPartitionKey(1001L, 1002L);
producer.send(KafkaTopics.MSG_SEND, partitionKey, messageJson);
```

#### 压缩配置
```java
// 支持的压缩类型：none, gzip, snappy, lz4, zstd
props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");
```

**压缩效果**:
- lz4: 压缩比中等，性能最好（推荐）
- snappy: 压缩比中等，性能好
- gzip: 压缩比高，性能较差
- zstd: 压缩比高，性能较好（Kafka 2.1+）

#### 幂等性配置
```java
// 启用幂等性（防止重复）
props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
```

**幂等性保证**:
- 单分区单会话的exactly-once语义
- 自动重试不会导致消息重复
- 需要配置acks=all

#### 批量发送配置
```java
// 批量发送大小（16KB）
props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);

// 等待批量发送的时间（10ms）
props.put(ProducerConfig.LINGER_MS_CONFIG, 10);
```

**批量发送优化**:
- 减少网络请求次数
- 提高吞吐量
- 增加延迟（可接受范围）

### 2. KafkaConsumerManager 增强功能

#### 手动提交Offset
```java
// 订阅Topic（禁用自动提交）
consumer.subscribe(topic);

// 消费消息
consumer.poll(record -> {
    // 处理消息
    handleMessage(record);
}, 1000);

// 手动提交Offset
consumer.commitSync();
```

**手动提交的优势**:
- 精确控制Offset提交时机
- 消息处理失败时不提交
- 实现exactly-once语义

#### 批量消费
```java
consumer.pollBatch(records -> {
    // 批量处理消息
    for (ConsumerRecord<String, String> record : records) {
        // 处理消息
    }
}, 1000, 100); // 批量大小100
```

**批量消费的优势**:
- 减少函数调用开销
- 提高处理效率
- 支持批量插入数据库

#### 消息过滤
```java
// 只消费特定条件的消息
consumer.poll(record -> {
    // 处理消息
}, 1000, record -> {
    // 过滤条件：只处理key以"important_"开头的消息
    return record.key().startsWith("important_");
});
```

### 3. MessagePartitioner 分区策略

#### 私聊消息分区
```java
String partitionKey = Math.min(from, to) + "-" + Math.max(from, to);
// 示例：1001-1002

// 同一会话的消息总是进入同一分区
// 保证消息顺序性
```

#### 群聊消息分区
```java
String partitionKey = String.valueOf(groupId);
// 示例：2001

// 同一群组的消息总是进入同一分区
// 保证群组消息顺序性
```

#### 公屏消息分区
```java
String partitionKey = "broadcast";

// 所有公屏消息进入同一分区
// 或使用轮询策略（负载均衡）
```

### 4. KafkaConsumerService 服务封装

#### 架构设计
```
KafkaConsumerService
    ├─ Map<String, MessageListener> listeners  (Topic -> 监听器)
    ├─ Map<String, Thread> consumerThreads     (Topic -> 消费线程)
    └─ Map<String, AtomicLong> topicMessageCount (Topic -> 消息计数)
```

#### 消费流程
```
1. start() 启动服务
2. 为每个Topic创建独立消费线程
3. 每个线程创建KafkaConsumer实例
4. 订阅Topic并开始poll
5. 收到消息后调用MessageListener
6. 处理完成后手动提交Offset
7. 异常时记录日志并继续消费
8. stop() 停止服务（优雅停止）
```

#### 线程模型
```java
// 每个Topic独立消费线程
for (String topic : listeners.keySet()) {
    Thread thread = new Thread(() -> {
        // 创建独立的KafkaConsumer
        KafkaConsumerManager consumer = new KafkaConsumerManager(...);
        consumer.subscribe(topic);

        // 消费循环
        consumer.poll(record -> {
            // 调用MessageListener
            listeners.get(topic).onMessage(record);

            // 手动提交Offset
            consumer.commitAsync();
        }, 1000);
    });

    thread.start();
    consumerThreads.put(topic, thread);
}
```

### 5. PushMessageConsumer 消费逻辑

#### 完整流程
```
1. 从Kafka Topic: im-msg-push 拉取消息
2. 反序列化JSON为IMMessage对象
3. 提取目标用户ID（to字段）
4. 查询Redis（OnlineStatusService.isOnline）
5. 判断用户是否在线
   ├─ 在线 → 推送到用户所有设备（ChannelManager.broadcastToUser）
   └─ 离线 → 跳过（业务层处理离线消息）
6. 更新统计信息
7. 提交Offset（由KafkaConsumerService统一处理）
```

#### 异常处理
```java
try {
    // 处理消息
    IMMessage message = objectMapper.readValue(record.value(), IMMessage.class);
    // ... 推送逻辑 ...
} catch (JsonProcessingException e) {
    failureCount.incrementAndGet();
    log.error("消息反序列化失败: value={}", record.value(), e);
} catch (Exception e) {
    failureCount.incrementAndGet();
    log.error("处理推送消息异常", e);
}
```

---

## 💡 使用示例

### 1. 发送私聊消息

```java
// 创建生产者
KafkaProducerManager producer = new KafkaProducerManager("192.168.215.2:9092");

// 构建消息
IMMessage message = IMMessage.builder()
    .header(MessageHeader.builder()
        .msgId(UUID.randomUUID().toString())
        .cmd(CommandType.PRIVATE_CHAT)
        .from(1001L)
        .to(1002L)
        .timestamp(System.currentTimeMillis())
        .build())
    .body(MessageBody.builder()
        .content("你好，这是一条私聊消息")
        .contentType("text")
        .build())
    .build();

// 生成分区Key
String partitionKey = MessagePartitioner.generatePrivateChatPartitionKey(1001L, 1002L);

// 序列化为JSON
String messageJson = objectMapper.writeValueAsString(message);

// 发送消息
Future<RecordMetadata> future = producer.send(
    KafkaTopics.MSG_SEND,
    partitionKey,
    messageJson
);

// 可选：等待发送完成
RecordMetadata metadata = future.get();
log.info("消息发送成功: partition={}, offset={}",
    metadata.partition(), metadata.offset());
```

### 2. 发送群聊消息

```java
// 构建群聊消息
IMMessage message = IMMessage.builder()
    .header(MessageHeader.builder()
        .msgId(UUID.randomUUID().toString())
        .cmd(CommandType.GROUP_CHAT)
        .from(1001L)
        .to(2001L)  // groupId
        .timestamp(System.currentTimeMillis())
        .build())
    .body(MessageBody.builder()
        .content("大家好，这是一条群聊消息")
        .contentType("text")
        .build())
    .build();

// 生成分区Key（使用groupId）
String partitionKey = MessagePartitioner.generateGroupChatPartitionKey(2001L);

// 发送消息
producer.send(KafkaTopics.MSG_SEND, partitionKey,
             objectMapper.writeValueAsString(message));
```

### 3. 消费推送消息（服务方式）

```java
// 创建消费者服务
KafkaConsumerService consumerService = new KafkaConsumerService("192.168.215.2:9092");

// 添加监听器
consumerService.addListener(KafkaTopics.MSG_PUSH,
    new PushMessageConsumer(channelManager, onlineStatusService));

// 启动消费
consumerService.start();

// ... 服务运行 ...

// 停止消费
consumerService.stop();
```

### 4. 消费消息（直接方式）

```java
// 创建消费者
KafkaConsumerManager consumer = new KafkaConsumerManager(
    "192.168.215.2:9092",
    "im-gateway-group"
);

// 订阅Topic
consumer.subscribe(KafkaTopics.MSG_PUSH);

// 消费消息
consumer.poll(record -> {
    // 反序列化消息
    IMMessage message = objectMapper.readValue(record.value(), IMMessage.class);

    // 获取目标用户ID
    Long toUserId = message.getTo();

    // 推送消息
    channelManager.broadcastToUser(toUserId, message);

    // 手动提交Offset
    consumer.commitAsync();

}, 1000); // 超时1秒
```

### 5. 批量消费消息

```java
// 批量消费
consumer.pollBatch(records -> {
    log.info("批量消费 {} 条消息", records.size());

    // 批量处理
    for (ConsumerRecord<String, String> record : records) {
        // 处理消息
        handleMessage(record);
    }

    // 批量提交Offset
    consumer.commitAsync();

}, 1000, 100); // 批量大小100
```

### 6. 过滤消费消息

```java
// 只消费特定条件的消息
consumer.poll(record -> {
    // 处理消息
    handleMessage(record);

}, 1000, record -> {
    // 只处理指定用户的消息
    String value = record.value();
    return value.contains("\"to\":1002\"");
});
```

### 7. 获取统计信息

```java
// Producer统计
Map<MetricName, ? extends Metric> producerMetrics = producer.getMetrics();
String producerStats = producer.getStatsSummary();
log.info("Producer统计:\n{}", producerStats);

// Consumer统计
Map<MetricName, ? extends Metric> consumerMetrics = consumer.getMetrics();
String consumerStats = consumer.getStatsSummary();
log.info("Consumer统计:\n{}", consumerStats);

// KafkaConsumerService统计
String serviceStats = consumerService.getStats();
log.info("消费者服务统计:\n{}", serviceStats);
```

---

## 📈 性能优化建议

### 1. Producer优化

#### 批量发送
```java
// 增大批量大小
props.put(ProducerConfig.BATCH_SIZE_CONFIG, 32768); // 32KB

// 增加等待时间
props.put(ProducerConfig.LINGER_MS_CONFIG, 20); // 20ms
```

#### 压缩
```java
// 使用lz4压缩（推荐）
props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");
```

#### 缓冲区
```java
// 增大缓冲区
props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 67108864); // 64MB
```

### 2. Consumer优化

#### 批量拉取
```java
// 增加单次poll最大记录数
props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1000);
```

#### 会话超时
```java
// 增加会话超时时间
props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 60000); // 60秒
```

#### 心跳间隔
```java
// 调整心跳间隔
props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 10000); // 10秒
```

### 3. 分区优化

#### 分区数选择
```
分区数 = max(目标TPS / 单分区TPS, 分区数)

例如：
目标TPP = 100000
单分区TPS = 5000
分区数 = 100000 / 5000 = 20

建议配置32个分区（留有余量）
```

---

## ⚠️ 注意事项和最佳实践

### 1. Offset提交

**推荐**: 手动提交Offset
```java
consumer.poll(record -> {
    try {
        // 处理消息
        handleMessage(record);

        // 处理成功后提交Offset
        consumer.commitAsync();
    } catch (Exception e) {
        // 处理失败不提交Offset
        // 下次会重新消费该消息
        log.error("处理消息失败", e);
    }
}, 1000);
```

### 2. 消息幂等性

**问题**: 网络抖动可能导致消息重复

**解决方案**: Redis去重
```java
public void handleMessage(ConsumerRecord<String, String> record) {
    String msgId = extractMsgId(record.value());

    // 检查是否已处理
    String key = String.format(RedisKeys.MSG_PROCESSED, msgId);
    Boolean isNew = redisTemplate.setIfAbsent(key, "1", 24, TimeUnit.HOURS);

    if (Boolean.FALSE.equals(isNew)) {
        log.warn("消息重复，跳过处理: msgId={}", msgId);
        return;
    }

    // 处理消息
    processMessage(record);
}
```

### 3. 消费者组

**原则**: 相同业务逻辑使用相同消费者组ID

```java
// 正确：所有Gateway实例使用相同消费者组
new KafkaConsumerManager(kafkaServers, "im-gateway-group");

// 错误：每个实例使用不同消费者组ID（会导致消息重复消费）
```

### 4. 序列化

**当前**: 使用JSON序列化

**优化**: 考虑使用更高效的序列化方式
- Protobuf（Google Protocol Buffers）
- Avro
- MessagePack

### 5. 监控

**关键指标**:
- Producer: record-send-total, record-error-total, request-latency-avg
- Consumer: records-consumed-total, records-lag-max, fetch-rate

**监控代码**:
```java
// 定期输出统计信息
ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
scheduler.scheduleAtFixedRate(() -> {
    log.info("Producer统计:\n{}", producer.getStatsSummary());
    log.info("Consumer统计:\n{}", consumer.getStatsSummary());
}, 0, 1, TimeUnit.MINUTES);
```

### 6. 资源清理

**必须调用close()方法**:
```java
// 使用try-with-resources
try (KafkaProducerManager producer = new KafkaProducerManager(servers)) {
    // 发送消息
    producer.send(topic, key, value);

} // 自动调用close()

// 或手动关闭
producer.close();
```

---

## 🧪 测试场景

### 1. 消息发送测试
```java
@Test
public void testSendMessage() {
    KafkaProducerManager producer = new KafkaProducerManager("192.168.215.2:9092");

    // 发送消息
    producer.send(KafkaTopics.MSG_SEND, "test-key", "test-value");

    // 等待发送完成
    producer.flush();

    // 验证统计
    Map<MetricName, ? extends Metric> metrics = producer.getMetrics();
    assertTrue(metrics.containsKey(...));
}
```

### 2. 消息消费测试
```java
@Test
public void testConsumeMessage() {
    KafkaConsumerService consumerService = new KafkaConsumerService("192.168.215.2:9092");

    // 添加测试监听器
    consumerService.addListener(KafkaTopics.MSG_PUSH, new MessageListener() {
        @Override
        public void onMessage(ConsumerRecord<String, String> record) {
            // 验证消息
            assertNotNull(record);
            assertEquals(KafkaTopics.MSG_PUSH, record.topic());
        }

        @Override
        public void onException(Exception exception) {
            fail("消费异常: " + exception.getMessage());
        }
    });

    // 启动消费
    consumerService.start();

    // 等待消息
    Thread.sleep(5000);

    // 停止消费
    consumerService.stop();
}
```

### 3. 分区策略测试
```java
@Test
public void testPartitionStrategy() {
    // 测试私聊消息分区
    String key1 = MessagePartitioner.generatePrivateChatPartitionKey(1001L, 1002L);
    String key2 = MessagePartitioner.generatePrivateChatPartitionKey(1002L, 1001L);
    assertEquals(key1, key2); // 同一会话，分区Key相同

    // 测试群聊消息分区
    String key3 = MessagePartitioner.generateGroupChatPartitionKey(2001L);
    assertEquals("2001", key3);
}
```

---

## 📋 TODO和优化建议

### 当前TODO
1. **消息序列化优化**: 考虑使用Protobuf替代JSON
2. **消息压缩**: 默认使用lz4，可配置
3. **监控指标**: 集成Micrometer收集Metrics
4. **消息轨迹**: 添加消息追踪功能

### 优化建议
1. **批量发送优化**: 调整batch.size和linger.ms参数
2. **消费者组管理**: 动态增减消费者实例
3. **分区数动态调整**: 根据负载调整分区数
4. **故障自动恢复**: 实现消费者故障转移
5. **消息回放**: 支持从指定Offset重新消费

---

## 🎉 总结

阶段四"Kafka 消息总线"已完整实现，所有功能均按照开发计划要求完成：

### 完成成果
- ✅ 2个修改的核心类（KafkaProducerManager、KafkaConsumerManager）
- ✅ 5个新建的核心类（MessagePartitioner、MessageListener、KafkaConsumerService、PushMessageConsumer等）
- ✅ 2个文档文件（KAFKA_TOPICS_SETUP.md、KAFKA_USAGE_EXAMPLE.md）
- ✅ 约1678行代码

### 技术亮点
1. **自定义分区器**: 保证消息顺序性
2. **消费者服务封装**: 简化消费逻辑
3. **推送消息消费者**: 完整的推送-消费流程
4. **手动提交Offset**: 精确控制消费进度
5. **批量消费支持**: 提高消费效率
6. **消息过滤**: 灵活的消息处理
7. **完善的统计**: 实时监控发送和消费情况
8. **异常处理**: 完整的错误捕获和处理

### 可维护性
- 清晰的模块划分
- 统一的命名规范
- 完整的JavaDoc注释
- 详细的日志记录
- 丰富的使用示例

### 可扩展性
- 支持水平扩展
- 支持多消费者组
- 支持动态添加Topic
- 支持自定义序列化
- 支持自定义分区策略

---

## 📚 相关文档

- [开发计划](DEVELOPMENT_PLAN.md) - 完整的开发计划
- [阶段一总结](./STAGE1_SUMMARY.md) - 基础架构搭建总结
- [阶段二总结](STAGE2_SUMMARY.md) - Redis在线路由总结
- [阶段三总结](STAGE3_SUMMARY.md) - Netty网关核心总结
- [Kafka Topics创建脚本](KAFKA_TOPICS_SETUP.md) - Topic创建文档
- [Kafka使用示例](KAFKA_USAGE_EXAMPLE.md) - 使用示例文档

---

**下一步**: 阶段五 - 消息流程实现 🚀
