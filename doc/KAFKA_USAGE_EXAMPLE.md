# Kafka 消息总线使用示例

## 1. 发送消息示例

### 1.1 发送私聊消息

```java
import com.cw.im.common.protocol.IMMessage;
import com.cw.im.common.protocol.CommandType;
import com.cw.im.kafka.KafkaProducerManager;
import com.cw.im.common.constants.KafkaTopics;
import com.cw.im.kafka.MessagePartitioner;

// 创建 Kafka 生产者
KafkaProducerManager producer = new KafkaProducerManager("192.168.215.2:9092");

// 构建私聊消息
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

// 生成分区Key（会话ID）
String partitionKey = MessagePartitioner.generatePrivateChatPartitionKey(1001L, 1002L);

// 发送消息到 Kafka
producer.send(KafkaTopics.MSG_SEND, partitionKey, JSON.toJSONString(message));

// 或者同步发送
RecordMetadata metadata = producer.sendSync(KafkaTopics.MSG_SEND, partitionKey, JSON.toJSONString(message));
```

### 1.2 发送群聊消息

```java
// 构建群聊消息
IMMessage message = IMMessage.builder()
    .header(MessageHeader.builder()
        .msgId(UUID.randomUUID().toString())
        .cmd(CommandType.GROUP_CHAT)
        .from(1001L)
        .to(2001L)  // 这里 to 存储群组ID
        .timestamp(System.currentTimeMillis())
        .build())
    .body(MessageBody.builder()
        .content("大家好，这是一条群聊消息")
        .contentType("text")
        .build())
    .build();

// 生成分区Key（群组ID）
String partitionKey = MessagePartitioner.generateGroupChatPartitionKey(2001L);

// 发送消息
producer.send(KafkaTopics.MSG_SEND, partitionKey, JSON.toJSONString(message));
```

### 1.3 发送公屏消息

```java
// 构建公屏消息
IMMessage message = IMMessage.builder()
    .header(MessageHeader.builder()
        .msgId(UUID.randomUUID().toString())
        .cmd(CommandType.PUBLIC_CHAT)
        .from(1001L)
        .to(0L)  // 公屏消息 to 为 0
        .timestamp(System.currentTimeMillis())
        .build())
    .body(MessageBody.builder()
        .content("这是一条公屏消息")
        .contentType("text")
        .build())
    .build();

// 生成分区Key（固定值）
String partitionKey = MessagePartitioner.generateBroadcastPartitionKey();

// 发送消息
producer.send(KafkaTopics.MSG_SEND, partitionKey, JSON.toJSONString(message));
```

### 1.4 发送ACK确认消息

```java
// 构建ACK消息
IMMessage ackMessage = IMMessage.builder()
    .header(MessageHeader.builder()
        .msgId(UUID.randomUUID().toString())
        .cmd(CommandType.ACK)
        .from(1002L)
        .to(1001L)
        .timestamp(System.currentTimeMillis())
        .build())
    .body(MessageBody.builder()
        .content("original-msg-id-12345")  // 原始消息ID
        .contentType("ack")
        .build())
    .build();

// 生成分区Key（消息ID）
String partitionKey = MessagePartitioner.generateAckPartitionKey("original-msg-id-12345");

// 发送ACK
producer.send(KafkaTopics.ACK, partitionKey, JSON.toJSONString(ackMessage));
```

### 1.5 异步发送带回调

```java
// 异步发送，带自定义回调
producer.sendAsync(KafkaTopics.MSG_SEND, partitionKey, JSON.toJSONString(message),
    (metadata, exception) -> {
        if (exception != null) {
            // 发送失败处理
            log.error("消息发送失败: msgId={}, error={}", message.getMsgId(), exception.getMessage());

            // 可以重试或记录到失败队列
            // retryQueue.add(message);
        } else {
            // 发送成功处理
            log.info("消息发送成功: msgId={}, partition={}, offset={}",
                message.getMsgId(), metadata.partition(), metadata.offset());

            // 更新发送状态
            // messageService.markAsSent(message.getMsgId());
        }
    });
```

## 2. 消费消息示例

### 2.1 使用 KafkaConsumerService 消费

```java
import com.cw.im.kafka.KafkaConsumerService;
import com.cw.im.kafka.MessageListener;

// 创建 Kafka 消费者服务
KafkaConsumerService consumerService = new KafkaConsumerService("192.168.215.2:9092");

// 添加消息监听器
consumerService.addListener(KafkaTopics.MSG_PUSH, new MessageListener() {
    @Override
    public void onMessage(ConsumerRecord<String, String> record) {
        try {
            // 反序列化消息
            IMMessage message = JSON.parseObject(record.value(), IMMessage.class);

            // 处理消息
            Long toUserId = message.getTo();
            boolean isOnline = onlineStatusService.isOnline(toUserId);

            if (isOnline) {
                // 推送给用户
                channelManager.broadcastToUser(toUserId, message);
                log.info("推送消息成功: userId={}, msgId={}", toUserId, message.getMsgId());
            } else {
                log.info("用户离线，跳过推送: userId={}", toUserId);
            }

        } catch (Exception e) {
            log.error("处理消息失败", e);
            throw e;
        }
    }

    @Override
    public void onException(Exception exception) {
        log.error("消费异常", exception);
    }

    @Override
    public void onComplete() {
        // 批次处理完成
        log.debug("一批消息处理完成");
    }
});

// 启动消费服务
consumerService.start();

// ... 运行 ...

// 停止消费服务（优雅关闭）
consumerService.stop();
```

### 2.2 直接使用 KafkaConsumerManager 消费

```java
import com.cw.im.kafka.KafkaConsumerManager;

// 创建消费者（手动提交）
KafkaConsumerManager consumer = new KafkaConsumerManager(
    "192.168.215.2:9092",
    "my-consumer-group",
    false,  // 手动提交
    "earliest",
    500,    // max.poll.records
    300000, // max.poll.interval.ms
    30000,  // session.timeout.ms
    10000   // heartbeat.interval.ms
);

// 订阅 Topic
consumer.subscribe(KafkaTopics.MSG_PUSH);

// 开始消费
consumer.poll(record -> {
    try {
        // 处理消息
        IMMessage message = JSON.parseObject(record.value(), IMMessage.class);

        // 业务逻辑
        processMessage(message);

    } catch (Exception e) {
        log.error("处理消息失败: offset={}", record.offset(), e);
    }
}, 1000); // timeout=1s

// 手动提交 Offset
consumer.commitSync();
```

### 2.3 批量消费

```java
// 批量消费（每批处理100条）
consumer.pollBatch(records -> {
    for (ConsumerRecord<String, String> record : records) {
        try {
            IMMessage message = JSON.parseObject(record.value(), IMMessage.class);
            processMessage(message);
        } catch (Exception e) {
            log.error("处理消息失败", e);
        }
    }

    // 批次完成后提交
    consumer.commitSync();
}, 1000, 100); // timeout=1s, batchSize=100
```

### 2.4 带过滤的消费

```java
// 只处理特定类型的消息
consumer.poll(record -> {
    IMMessage message = JSON.parseObject(record.value(), IMMessage.class);

    // 只处理私聊消息
    if (message.getCmd() == CommandType.PRIVATE_CHAT) {
        processPrivateMessage(message);
    }
}, 1000, record -> {
    // 过滤条件：只处理私聊
    try {
        IMMessage msg = JSON.parseObject(record.value(), IMMessage.class);
        return msg.getCmd() == CommandType.PRIVATE_CHAT;
    } catch (Exception e) {
        return false;
    }
});
```

## 3. 分区策略说明

### 3.1 私聊消息分区

```java
// 私聊消息使用会话ID作为分区Key
// 规则: min(from, to) + "-" + max(from, to)
// 保证同一会话的消息进入同一分区，保证消息顺序

Long from = 1001L;
Long to = 1002L;
String partitionKey = MessagePartitioner.generatePrivateChatPartitionKey(from, to);
// 结果: "1001-1002"

// 无论谁发送，都是同一个分区Key
partitionKey = MessagePartitioner.generatePrivateChatPartitionKey(1002L, 1001L);
// 结果: "1001-1002" (相同)
```

### 3.2 群聊消息分区

```java
// 群聊消息使用群组ID作为分区Key
// 保证同一群组的消息进入同一分区

Long groupId = 2001L;
String partitionKey = MessagePartitioner.generateGroupChatPartitionKey(groupId);
// 结果: "2001"
```

### 3.3 公屏消息分区

```java
// 公屏消息使用固定值 "broadcast" 作为分区Key
// 所有公屏消息进入同一分区（保证顺序）

String partitionKey = MessagePartitioner.generateBroadcastPartitionKey();
// 结果: "broadcast"
```

### 3.4 ACK消息分区

```java
// ACK消息使用消息ID作为分区Key
// 保证同一消息的ACK进入同一分区

String msgId = "msg-uuid-12345";
String partitionKey = MessagePartitioner.generateAckPartitionKey(msgId);
// 结果: "msg-uuid-12345"
```

## 4. 统计信息获取

### 4.1 生产者统计

```java
// 获取生产者统计
Map<MetricName, ? extends Metric> metrics = producer.getMetrics();

// 获取统计摘要
String stats = producer.getStatsSummary();
log.info("生产者统计: {}", stats);
```

### 4.2 消费者统计

```java
// 获取消费者统计
Map<MetricName, ? extends Metric> metrics = consumer.getMetrics();

// 获取统计摘要
String stats = consumer.getStatsSummary();
log.info("消费者统计: {}", stats);
```

### 4.3 消费者服务统计

```java
// 获取消费者服务统计
String stats = kafkaConsumerService.getStats();
log.info("消费服务统计: {}", stats);

// 获取指定 Topic 的消费数量
long count = kafkaConsumerService.getTopicMessageCount(KafkaTopics.MSG_PUSH);
log.info("MSG_TOPIC 消费数量: {}", count);
```

## 5. 异常处理

### 5.1 发送失败重试

```java
int maxRetries = 3;
int retryCount = 0;

while (retryCount < maxRetries) {
    try {
        producer.sendSync(KafkaTopics.MSG_SEND, partitionKey, jsonMessage);
        break; // 成功则退出
    } catch (Exception e) {
        retryCount++;
        if (retryCount >= maxRetries) {
            log.error("发送失败，已达最大重试次数: msgId={}", message.getMsgId());
            // 记录到失败队列
            failedMessageQueue.add(message);
        } else {
            log.warn("发送失败，正在重试: retryCount={}, msgId={}", retryCount, message.getMsgId());
            Thread.sleep(1000); // 延迟1秒后重试
        }
    }
}
```

### 5.2 消费失败处理

```java
@Override
public void onMessage(ConsumerRecord<String, String> record) {
    try {
        processMessage(record);
    } catch (Exception e) {
        log.error("处理消息失败: offset={}", record.offset(), e);

        // 可以选择：
        // 1. 记录到失败队列
        failedRecordQueue.add(record);

        // 2. 记录到数据库
        // failedMessageService.save(record);

        // 3. 发送告警
        // alertService.sendAlert("消息处理失败", record);
    }
}
```

## 6. 资源清理

### 6.1 优雅关闭生产者

```java
// 刷新缓冲区
producer.flush();

// 关闭生产者
producer.close();

// 或者带超时关闭
producer.close(10000); // 10秒超时
```

### 6.2 优雅关闭消费者

```java
// 停止消费
consumer.stop();

// 唤醒消费者（退出阻塞的poll）
consumer.wakeup();

// 关闭消费者
consumer.close();

// 或者带超时关闭
consumer.close(10000); // 10秒超时
```

### 6.3 优雅关闭消费者服务

```java
// 停止消费者服务（会自动停止所有Topic的消费）
kafkaConsumerService.stop();
```
