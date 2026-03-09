# 阶段五使用指南

## 快速开始

### 1. 启动服务器

服务器已自动集成所有新组件，直接启动即可：

```bash
# 设置环境变量
export REDIS_HOST=192.168.215.2
export REDIS_PORT=6379
export KAFKA_SERVERS=192.168.215.2:9092
export SERVER_PORT=8080

# 启动服务器
cd nb-im-server
mvn exec:java -Dexec.mainClass="com.cw.im.server.NettyIMServer"
```

### 2. 查看日志

启动后会看到以下关键日志：

```
========================================
Netty IM 服务器正在启动...
========================================
Redis 管理器初始化成功: 192.168.215.2:6379
在线状态服务启动成功
Channel 管理器初始化成功, gatewayId=gateway-localhost-xxx
消息去重器初始化成功
RouteHandler 初始化完成: gatewayId=gateway-localhost-xxx
========================================
推送消息消费者已注册: topic=im-msg-push
ACK消息消费者已注册: topic=im-ack
Kafka 消费者服务已启动
========================================
Netty IM 服务器启动成功!
========================================
```

## 消息类型使用示例

### 私聊消息

**发送**：
```java
IMMessage message = IMMessage.builder()
    .header(MessageHeader.builder()
        .msgId(UUID.randomUUID().toString())
        .cmd(CommandType.PRIVATE_CHAT)
        .from(1001L)  // 发送者ID
        .to(1002L)    // 接收者ID
        .timestamp(System.currentTimeMillis())
        .version("1.0")
        .build())
    .body(MessageBody.builder()
        .content("你好，这是一条私聊消息")
        .contentType("text")
        .build())
    .build();

channel.writeAndFlush(message);
```

**流程**：
1. Gateway检查消息去重
2. 查询用户1002是否在线
3. 在线：直接推送，离线：发送到Kafka
4. 返回ACK给发送者

### 群聊消息

**发送**：
```java
IMMessage message = IMMessage.builder()
    .header(MessageHeader.builder()
        .msgId(UUID.randomUUID().toString())
        .cmd(CommandType.GROUP_CHAT)
        .from(1001L)  // 发送者ID
        .to(2001L)    // 群组ID
        .timestamp(System.currentTimeMillis())
        .version("1.0")
        .build())
    .body(MessageBody.builder()
        .content("大家好，这是一条群聊消息")
        .contentType("text")
        .build())
    .build();

channel.writeAndFlush(message);
```

**流程**：
1. Gateway检查消息去重
2. 发送到Kafka（使用groupId作为分区key）
3. 业务系统消费并扩散消息
4. 推送给群组在线成员

### 公屏消息

**发送**：
```java
IMMessage message = IMMessage.builder()
    .header(MessageHeader.builder()
        .msgId(UUID.randomUUID().toString())
        .cmd(CommandType.PUBLIC_CHAT)
        .from(1001L)  // 发送者ID
        .to(0L)       // 公屏消息to字段无意义
        .timestamp(System.currentTimeMillis())
        .version("1.0")
        .build())
    .body(MessageBody.builder()
        .content("这是一条公屏消息")
        .contentType("text")
        .build())
    .build();

channel.writeAndFlush(message);
```

**流程**：
1. Gateway检查消息去重
2. 发送到Kafka广播Topic
3. 所有Gateway消费并广播
4. 推送给所有在线用户

### ACK消息

**发送**（客户端收到消息后）：
```java
IMMessage ack = IMMessage.builder()
    .header(MessageHeader.builder()
        .msgId(UUID.randomUUID().toString())
        .cmd(CommandType.ACK)
        .from(1002L)  // 接收者ID
        .to(0L)       // 系统消息
        .timestamp(System.currentTimeMillis())
        .version("1.0")
        .build())
    .body(MessageBody.builder()
        .content("ACK: " + originalMsgId)
        .contentType("ack")
        .extras(Map.of(
            "msgId", originalMsgId,  // 原始消息ID
            "success", true,         // 是否成功
            "reason", ""             // 失败原因
        ))
        .build())
    .build();

channel.writeAndFlush(ack);
```

**流程**：
1. 客户端返回ACK
2. Gateway接收并转发到Kafka
3. 业务系统消费ACK
4. 更新消息状态

## 统计信息查看

### 在服务器关闭时查看统计

服务器关闭时会自动输出所有统计信息：

```
========================================
连接统计信息:
ConnectionHandler{...}
========================================
路由消息统计信息:
RouteHandler{total=1000, privateChat=600(60.00%), ...}
========================================
PrivateChatHandler 统计信息:
PrivateChatHandler{totalSent=600, directPush=400(66.67%), ...}
========================================
Kafka 消费统计信息:
KafkaConsumerService{...}
========================================
```

### 手动获取统计信息

```java
// 获取RouteHandler统计
RouteHandler routeHandler = ...;
String stats = routeHandler.getStats();
System.out.println(stats);

// 获取所有Handler统计
String allStats = routeHandler.getAllHandlerStats();
System.out.println(allStats);

// 获取消息去重统计
MessageDeduplicator deduplicator = ...;
String dedupStats = deduplicator.getStats();
System.out.println(dedupStats);
```

## Kafka Topic说明

### 使用的Topic

1. **im-msg-send**：客户端发送的消息
   - 私聊、群聊、公屏消息都发送到此Topic
   - 业务系统消费此Topic处理消息

2. **im-msg-push**：业务系统推送的消息
   - PushMessageConsumer消费此Topic
   - 推送给在线用户

3. **im-ack**：ACK确认消息
   - AckConsumer消费此Topic
   - 更新消息状态

### Topic配置建议

```bash
# 创建Topic
kafka-topics.sh --create \
  --topic im-msg-send \
  --partitions 32 \
  --replication-factor 3 \
  --bootstrap-server localhost:9092

kafka-topics.sh --create \
  --topic im-msg-push \
  --partitions 32 \
  --replication-factor 3 \
  --bootstrap-server localhost:9092

kafka-topics.sh --create \
  --topic im-ack \
  --partitions 16 \
  --replication-factor 3 \
  --bootstrap-server localhost:9092
```

## 消息去重说明

### 去重原理

使用Redis的SETNX命令实现原子性检查：

```java
// 检查消息是否已处理
if (messageDeduplicator.isProcessed(msgId)) {
    // 消息已处理，跳过
    return;
}

// 首次处理，自动标记为已处理
// ...
```

### Redis Key格式

```
im:msg:processed:{msgId} → "1" (TTL: 24小时)
```

### 去重位置

- **RouteHandler**：全局去重（所有消息类型）
- **PrivateChatHandler**：私聊消息去重
- **GroupChatHandler**：群聊消息去重
- **PublicChatHandler**：公屏消息去重
- **AckHandler**：ACK消息去重

### 去重统计

```java
MessageDeduplicator deduplicator = ...;

// 获取统计
String stats = deduplicator.getStats();
// 输出: MessageDeduplicator{totalCheck=1000, duplicate=50, processed=950, duplicateRate=5.00%}

// 获取重复率
double duplicateRate = deduplicator.getDuplicateRate();

// 重置统计
deduplicator.resetStats();
```

## 可选功能启用

### 启用群聊推送Consumer

如果需要在Gateway查询群组成员并推送，取消注释：

```java
// 在NettyIMServer.start()方法中
kafkaConsumerService.addListener(KafkaTopics.GROUP_MSG_PUSH,
    new GroupMessagePushConsumer(channelManager, onlineStatusService));
log.info("群聊推送消息消费者已注册: topic={}", KafkaTopics.GROUP_MSG_PUSH);
```

### 启用公屏广播Consumer

如果需要公屏功能，取消注释：

```java
// 在NettyIMServer.start()方法中
kafkaConsumerService.addListener(KafkaTopics.PUBLIC_BROADCAST,
    new PublicBroadcastConsumer(channelManager));
log.info("公屏广播消费者已注册: topic={}", KafkaTopics.PUBLIC_BROADCAST);
```

**注意**：公屏会广播到所有在线用户，建议添加频率限制和权限验证。

## 常见问题

### 1. 消息重复处理

**现象**：日志显示"消息重复，跳过处理"

**原因**：消息ID重复，可能是：
- 客户端重发消息
- 网络重传
- 消息ID生成重复

**解决**：
- 确保消息ID全局唯一（使用UUID）
- 检查客户端重发逻辑
- 调整去重TTL（默认24小时）

### 2. 消息发送失败

**现象**：日志显示"发送到Kafka失败"

**原因**：
- Kafka连接失败
- Topic不存在
- 网络问题

**解决**：
- 检查Kafka连接配置
- 确认Topic已创建
- 查看Kafka日志

### 3. 用户收不到消息

**现象**：消息发送成功但用户收不到

**原因**：
- 用户离线
- Channel不可用
- 消息被去重

**解决**：
- 检查用户在线状态
- 查看Channel状态
- 检查消息去重日志

### 4. ACK未返回

**现象**：消息已发送但未收到ACK

**原因**：
- Gateway未收到消息
- Kafka消费失败
- ACK未转发

**解决**：
- 检查Gateway日志
- 检查Kafka消费日志
- 确认ACK Consumer正常运行

## 性能调优

### 1. 消息去重优化

```java
// 调整去重TTL（根据业务需求）
Duration customTtl = Duration.ofHours(12); // 12小时
MessageDeduplicator deduplicator = new MessageDeduplicator(redisManager, customTtl);
```

### 2. 批量处理优化

```java
// Kafka批量发送配置
Properties props = new Properties();
props.put("batch.size", 16384);      // 16KB
props.put("linger.ms", 10);          // 10ms
props.put("compression.type", "lz4"); // 压缩
```

### 3. 连接池优化

```java
// Redis连接池优化
RedisURI redisURI = RedisURI.builder()
    .withHost("localhost")
    .withPort(6379)
    .withTimeout(Duration.of(5, ChronoUnit.SECONDS))
    .build();
```

## 监控建议

### 1. 关键指标监控

- 消息发送TPS
- 消息推送成功率
- 消息重复率
- ACK成功率
- 在线用户数

### 2. 告警规则

- 消息重复率 > 5%
- 推送失败率 > 1%
- ACK超时率 > 1%
- Kafka消费延迟 > 1000条

### 3. 日志监控

- ERROR级别日志
- 消息处理失败日志
- Kafka连接异常日志
- Redis连接异常日志

## 下一步

完成阶段五后，可以继续：

1. **阶段六**：实现可靠性机制（重试、持久化、死信队列）
2. **阶段七**：实现监控与运维（Prometheus、健康检查）
3. **阶段八**：测试与压测（单元测试、集成测试、压力测试）

---

**文档版本**: v1.0
**创建日期**: 2026-03-09
**维护者**: NB-IM Team
