# NB-IM 中间件开发计划

## 📋 项目概述

**NB-IM** 是一个高性能的即时通讯中间件，采用 **JDK17 + Netty + Redis + Kafka** 技术栈，专注于长连接管理、消息路由和可靠投递，为业务系统提供可扩展的 IM 基础设施。

### 核心职责

✅ **负责**
- 长连接管理（Netty）
- 消息路由转发
- 在线状态维护
- 消息可靠投递（ACK + 重试）
- 消息顺序保证
- 离线消息转交 MQ

❌ **不负责**（交给业务层）
- 消息持久化
- 已读/未读状态
- 会话列表管理
- 内容审核
- 推送策略

### 性能目标

- **单机连接数**: 10万+
- **单机 TPS**: 5万+
- **消息延迟**: < 50ms（P99）
- **可用性**: 99.9%+

---

## 🏗️ 技术栈与版本选型

### 核心依赖

| 组件 | 版本 | 说明 |
|------|------|------|
| JDK | 17 LTS | Long-term Support |
| Netty | 4.1.x | 高性能 NIO 框架 |
| Kafka Client | 3.6.x | 消息队列客户端 |
| Redis Client | Lettuce 6.2.x | Redis 连接客户端 |
| Jackson | 2.15.x | JSON 序列化 |
| Lombok | 1.18.x | 代码简化 |
| Slf4j + Logback | 2.0.x / 1.4.x | 日志框架 |
| JUnit 5 | 5.10.x | 单元测试 |

### 可选依赖

- **Micrometer**: 监控指标采集
- **Prometheus Client**: 监控对接
- **Spring Boot** (可选): 便于快速启动和配置

---

## 📦 项目模块划分

```
nb-im/
├── nb-im-core/                    # 核心模块
│   ├── nb-im-common/              # 公共组件
│   │   ├── model/                # 消息模型
│   │   ├── protocol/             # 协议定义
│   │   ├── constants/            # 常量定义
│   │   └── utils/                # 工具类
│   ├── nb-im-redis/              # Redis 客户端封装
│   ├── nb-im-kafka/              # Kafka 客户端封装
│   └── nb-im-netty/              # Netty 核心组件
├── nb-im-gateway/                # 网关服务（启动模块）
├── nb-im-router/                 # 路由服务（可选）
└── nb-im-biz-demo/               # 业务服务示例
```

---

## 🎯 开发阶段规划

### 阶段一：基础架构搭建（Week 1-2）

**目标**: 完成项目骨架、依赖配置和基础编码规范

#### 1.1 Maven 项目构建

- [ ] 创建多模块 Maven 结构
- [ ] 配置父 POM 依赖管理
- [ ] 配置 JDK 17 编译参数
- [ ] 添加核心依赖（Netty、Kafka、Redis、Jackson）
- [ ] 配置代码风格检查（Checkstyle/Spotless）

#### 1.2 公共模块开发

- [ ] **消息模型定义**
  ```java
  - IMMessage          // 统一消息体
  - MessageHeader      // 消息头
  - MessageBody        // 消息体
  - MessageType        // 消息类型枚举
  ```

- [ ] **协议定义**
  ```java
  - Command            // 命令类型（私聊/群聊/公屏/ACK/心跳）
  - ProtocolVersion    // 协议版本
  - PacketCodec        // 编解码器接口
  ```

- [ ] **常量定义**
  ```java
  - RedisKeys          // Redis Key 模板
  - KafkaTopics        // Topic 名称
  - Constants         // 通用常量
  ```

#### 1.3 配置管理

- [ ] 配置文件结构设计（application.yml）
- [ ] 配置类定义（@ConfigurationProperties）
- [ ] 配置验证注解

**里程碑**: 可以编译通过，有完整的消息模型和协议定义

---

### 阶段二：Redis 在线路由（Week 3）

**目标**: 实现在线状态的 Redis 存储、查询和生命周期管理

#### 2.1 Redis 客户端封装

- [ ] Lettuce 连接池配置
- [ ] RedisTemplate 封装
- [ ] 序列化配置（JSON）
- [ ] 连接失败重试机制

#### 2.2 在线状态管理

- [ ] **数据结构设计**
  ```
  # 用户在线网关
  im:online:user:{userId} → gatewayId

  # 用户多端连接
  im:user:channel:{userId} → [channelId1, channelId2, ...]

  # 用户心跳时间
  im:heartbeat:user:{userId} → timestamp
  ```

- [ ] **核心接口实现**
  ```java
  - OnlineUserManager      // 在线用户管理
    + registerUser()       // 用户上线
    + unregisterUser()    // 用户下线
    + getOnlineGateway()  // 查询用户所在网关
    + heartbeat()         // 更新心跳
    + isOnline()          // 判断是否在线
  ```

#### 2.3 心跳检测

- [ ] 心跳时间戳更新
- [ ] 过期用户清理（定时任务）
- [ ] 心跳超时判定策略

**里程碑**: Redis 可以完整记录在线状态，支持心跳和过期清理

---

### 阶段三：Netty 网关核心（Week 4-6）

**目标**: 实现 Netty 长连接、协议编解码和消息处理流程

#### 3.1 Netty Server 启动

- [ ] **启动类设计**
  ```java
  - NettyIMServer         // 主启动类
  - ServerBootstrap 配置
  - EventLoopGroup 配置（Boss + Workers）
  - Channel 初始化
  ```

- [ ] **配置参数**
  ```yaml
  netty:
    port: 8080
    boss-threads: 1
    worker-threads: 0  # 默认 CPU * 2
    so-backlog: 128
    so-rcvbuf: 32KB
    so-sndbuf: 32KB
  ```

#### 3.2 Handler 链实现

按照 README 设计，实现完整 Handler Pipeline：

```
IdleStateHandler（心跳）
    ↓
LengthFieldPrepender/FrameDecoder（拆包）
    ↓
MessageCodec（编解码）
    ↓
AuthHandler（认证）
    ↓
RouteHandler（路由）
    ↓
KafkaProducerHandler（发送 MQ）
```

**具体实现**：

- [ ] **心跳 Handler**
  ```java
  - IdleStateHandler        // Netty 自带（60秒读空闲）
  - HeartbeatHandler       // 自定义心跳处理
    + readerIdle() → 发送心跳检测
    + channelInactive() → 清理资源
  ```

- [ ] **拆包 Handler**
  ```java
  - LengthFieldPrepender    // 编码：添加长度字段
  - LengthFieldBasedFrameDecoder  // 解码：按长度拆包
  ```

- [ ] **编解码 Handler**
  ```java
  - MessageEncoder         // JSON 序列化
  - MessageDecoder         // JSON 反序列化
  - 消息格式：[4字节长度] + [JSON体]
  ```

- [ ] **认证 Handler**
  ```java
  - AuthHandler            // 连接认证
    + 验证 Token
    + 提取 userId
    + 存储到 Channel.attr()
  ```

- [ ] **路由 Handler**
  ```java
  - RouteHandler           // 消息路由
    + 查询目标用户在线状态（Redis）
    + 判断推送/转发策略
    + 调用 Kafka 发送
  ```

#### 3.3 Channel 管理

- [ ] **Channel 管理器**
  ```java
  - ChannelManager         // Channel 生命周期管理
    + addChannel()        // 添加 Channel
    + removeChannel()     // 移除 Channel
    + getChannel()        // 获取用户 Channel
    + getChannels()       // 获取用户所有 Channel（多端）
    + broadcast()         // 广播消息
  ```

- [ ] **多端登录支持**
  - 一个用户多个 Channel（PC、手机、Web）
  - 按设备类型区分

- [ ] **连接数统计**
  - 当前连接数
  - 峰值连接数

**里程碑**: Netty 可以接受连接、收发消息、心跳检测正常

---

### 阶段四：Kafka 消息总线（Week 7-8）

**目标**: 实现 Kafka 消息发送、消费和顺序保证

#### 4.1 Kafka Topic 设计

创建以下 Topics：

```
im-msg-send       # 客户端发送的消息
  - Partitions: 32  # 根据并发量调整
  - Replication: 3

im-msg-push       # 业务回推的消息
  - Partitions: 32
  - Replication: 3

im-ack            # ACK 确认消息
  - Partitions: 16
  - Replication: 3
```

#### 4.2 Kafka Producer 封装

- [ ] **Producer 配置**
  ```java
  - KafkaTemplate<String, IMMessage>
  - 自定义 Partitioner（会话ID哈希）
  - 幂等性配置
  - 批量发送配置
  ```

- [ ] **分区策略**
  ```java
  - 私聊: partitionKey = conversationId（min(from, to) + max(from, to)）
  - 群聊: partitionKey = groupId
  - 公屏: partitionKey = broadcastId（固定或轮询）
  ```

#### 4.3 Kafka Consumer 封装

- [ ] **Consumer 配置**
  ```java
  - 手动提交 Offset
  - 消费者组配置
  - 消费线程池配置
  ```

- [ ] **消息消费监听**
  ```java
  - @KafkaListener(topics = "im-msg-push")
  - PushMessageConsumer       // 处理业务下推消息
    + 查询用户在线（Redis）
    + 推送到 Netty Channel
  ```

#### 4.4 顺序保证

- [ ] **同一会话进入同一 Partition**
  ```java
  partitionKey.hashCode() % partitionCount
  ```

- [ ] **单 Partition 单线程消费**
  - 保证消息处理顺序

**里程碑**: Kafka 可以正确发送、接收消息，顺序保证生效

---

### 阶段五：消息流程实现（Week 9-10）

**目标**: 实现完整的私聊、群聊、公屏消息流程

#### 5.1 私聊消息流程

**发送流程**:
```
客户端 → Netty → Kafka(im-msg-send) → 业务服务
```

- [ ] 接收客户端消息
- [ ] 验证消息格式
- [ ] 发送到 Kafka（按会话ID分区）
- [ ] 返回发送 ACK

**投递流程**:
```
业务服务 → Kafka(im-msg-push) → Gateway → Redis查询在线 → 推送客户端
```

- [ ] 消费 Push Topic
- [ ] 查询目标用户在线状态
- [ ] 在线：推送消息
- [ ] 离线：忽略（业务层处理持久化）

#### 5.2 群聊消息流程

- [ ] 接收群聊消息
- [ ] 发送到 Kafka（按 groupId 分区）
- [ ] 业务层扩散消息
- [ ] Gateway 消费并推送给在线成员

#### 5.3 公屏消息流程

- [ ] 发送到 Kafka 广播 Topic
- [ ] 所有 Gateway 消费
- [ ] 广播到所有在线 Channel

**里程碑**: 三种消息流程全部打通

---

### 阶段六：可靠性机制（Week 11-12）

**目标**: 实现消息 ACK、重试和去重

#### 6.1 ACK 机制设计

**三段 ACK**:

1. **客户端 ACK**
   ```java
   - 客户端收到消息后回复 ACK
   - Gateway 接收 ACK 并转发到 Kafka
   ```

2. **网关 ACK**
   ```java
   - Gateway 收到消息后回复接收 ACK
   - 确认消息已进入 Kafka
   ```

3. **业务 ACK**
   ```java
   - 业务层处理完成后回复 ACK
   - 确认消息已持久化
   ```

#### 6.2 重试机制

- [ ] **Kafka 消费重试**
  - 自动重试（配置 retry）
  - 失败消息进入 DLT（Dead Letter Topic）

- [ ] **客户端未 ACK 重试**
  - 定时任务扫描未确认消息
  - 重新推送到客户端
  - 最大重试次数限制

#### 6.3 去重机制

- [ ] **Redis 去重**
  ```
  im:msg:processed:{msgId} → TTL 24h
  ```

- [ ] **消息去重 Handler**
  ```java
  - 重复消息直接丢弃
  - 幂等性保证
  ```

**里程碑**: 消息可靠性保证完成

---

### 阶段七：监控与运维（Week 13）

**目标**: 实现监控指标、日志和部署方案

#### 7.1 监控指标

- [ ] **连接指标**
  - 当前连接数
  - 峰值连接数
  - 新建连接速率

- [ ] **消息指标**
  - 发送 TPS
  - 接收 TPS
  - 消息延迟（P50/P99）

- [ ] **系统指标**
  - CPU/内存/网络
  - Kafka 消费延迟
  - Redis 响应时间

#### 7.2 日志规范

- [ ] 结构化日志（JSON 格式）
- [ ] TraceId 全链路追踪
- [ ] 关键操作审计日志

#### 7.3 健康检查

- [ ] **Liveness**
  - Netty 是否运行
  - Kafka/Redis 连接状态

- [ ] **Readiness**
  - 是否可以接受新连接
  - 是否可以处理消息

#### 7.4 部署方案

- [ ] Docker 镜像构建
- [ ] Kubernetes 部署配置
- [ ] 滚动更新策略
- [ ] 配置中心集成（Nacos/Apollo）

**里程碑**: 完整的监控运维体系

---

### 阶段八：测试与压测（Week 14）

**目标**: 完成单元测试、集成测试和压力测试

#### 8.1 单元测试

- [ ] 核心组件单元测试（覆盖率 > 80%）
- [ ] Handler 单元测试
- [ ] Redis/Kafka 客户端测试

#### 8.2 集成测试

- [ ] 消息发送流程测试
- [ ] 消息接收流程测试
- [ ] ACK 机制测试
- [ ] 重试机制测试

#### 8.3 压力测试

- [ ] **连接压测**
  - 目标：10万连接
  - 工具：JMeter / 自定义压测客户端

- [ ] **消息压测**
  - 目标：5万 TPS
  - 场景：私聊/群聊混合

- [ ] **稳定性测试**
  - 长时间运行（24h+）
  - 内存泄漏检测
  - 连接池泄漏检测

**里程碑**: 通过性能和稳定性测试

---

## 🔑 关键技术点与实现要点

### 1. 消息协议设计

```json
{
  "header": {
    "msgId": "uuid",
    "cmd": "PRIVATE_CHAT",
    "from": 1001,
    "to": 1002,
    "timestamp": 1234567890,
    "version": "1.0"
  },
  "body": {
    "content": "hello",
    "extras": {}
  }
}
```

**要点**:
- 使用 UUID 保证 msgId 唯一
- timestamp 用于排序和去重
- 扩展字段支持业务自定义

### 2. Kafka 分区策略

```java
public class MessagePartitioner implements Partitioner {
    @Override
    public int partition(String topic, Object key, byte[] keyBytes,
                        Object value, byte[] valueBytes, Cluster cluster) {
        String conversationId = (String) key;
        return Math.abs(conversationId.hashCode()) % partitionCount;
    }
}
```

**要点**:
- 相同会话进入同一 Partition
- 保证消息顺序性

### 3. Redis Key 设计

```java
public class RedisKeys {
    public static final String ONLINE_USER = "im:online:user:%s";
    public static final String USER_CHANNELS = "im:user:channel:%s";
    public static final String HEARTBEAT = "im:heartbeat:user:%s";
    public static final String MSG_PROCESSED = "im:msg:processed:%s";
}
```

**要点**:
- 使用 Hash Tag 保证数据分布
- 设置合理的 TTL 避免内存泄漏

### 4. Netty Pipeline 配置

```java
bootstrap.handler(new ChannelInitializer<SocketChannel>() {
    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline pipeline = ch.pipeline();

        // 心跳（60秒读空闲）
        pipeline.addLast("idle", new IdleStateHandler(60, 0, 0));

        // 拆包
        pipeline.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(
            65536, 0, 4, 0, 4));
        pipeline.addLast("frameEncoder", new LengthFieldPrepender(4));

        // 编解码
        pipeline.addLast("decoder", new MessageDecoder());
        pipeline.addLast("encoder", new MessageEncoder());

        // 业务 Handler
        pipeline.addLast("auth", new AuthHandler());
        pipeline.addLast("heartbeat", new HeartbeatHandler());
        pipeline.addLast("route", new RouteHandler());
    }
});
```

**要点**:
- Handler 顺序至关重要
- 心跳 Handler 放在最前
- 确保线程安全

### 5. 多端登录处理

```java
public void addChannel(Long userId, String deviceId, Channel channel) {
    // 存储 userId -> Channel 映射
    userChannels.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet())
                .add(channel);

    // 存储 Channel -> userId 映射
    channel.attr(ChannelAttributes.USER_ID).set(userId);
    channel.attr(ChannelAttributes.DEVICE_ID).set(deviceId);

    // 更新 Redis
    redisTemplate.opsForSet().add(
        String.format(RedisKeys.USER_CHANNELS, userId),
        channel.id().asLongText()
    );
}
```

**要点**:
- 支持一个用户多设备在线
- 断线时只清理对应 Channel
- 其他设备不受影响

### 6. 消息去重

```java
public boolean isProcessed(String msgId) {
    String key = String.format(RedisKeys.MSG_PROCESSED, msgId);
    Boolean isNew = redisTemplate.opsForValue().setIfAbsent(key, "1", 24, TimeUnit.HOURS);
    return Boolean.FALSE.equals(isNew);
}
```

**要点**:
- 使用 setIfAbsent 原子操作
- 设置 24h TTL 自动过期
- 避免内存泄漏

---

## ⚠️ 风险点与应对方案

### 1. 连接数限制

**风险**: 操作系统限制最大连接数

**应对**:
- 调整 `/etc/sysctl.conf`:
  ```
  fs.file-max = 1000000
  net.ipv4.tcp_max_tw_buckets = 10000
  net.ipv4.tcp_fin_timeout = 30
  ```
- 进程级限制: `ulimit -n 65535`

### 2. 内存泄漏

**风险**: Channel 未正确清理导致内存泄漏

**应对**:
- 使用 `try-finally` 确保资源释放
- 定期监控 JVM 内存
- 使用 LeakDetector 检测泄漏

### 3. 消息堆积

**风险**: Kafka 消费慢导致消息堆积

**应对**:
- 增加 Consumer 实例
- 优化消息处理逻辑
- 监控消费延迟
- 设置合理的批处理大小

### 4. Redis 连接池耗尽

**风险**: 高并发下连接池耗尽

**应对**:
- 合理配置连接池大小
- 监控连接池使用率
- 设置连接超时时间
- 使用连接池监控

---

## 📊 性能优化建议

### 1. Netty 优化

- **EventLoopGroup 线程数**: Worker = CPU * 2
- **TCP 参数**:
  ```yaml
  so-backlog: 128        # 连接队列
  so-rcvbuf: 32KB        # 接收缓冲
  so-sndbuf: 32KB        # 发送缓冲
  tcp-nodelay: true      # 禁用 Nagle
  ```
- **堆外内存**: 使用 `Unpooled` 减少内存拷贝

### 2. Kafka 优化

- **批量发送**: `linger.ms=10`，`batch.size=16KB`
- **压缩**: `compression.type=lz4`
- **幂等性**: `enable.idempotence=true`
- **acks**: `acks=1`（平衡性能和可靠性）

### 3. Redis 优化

- **Pipeline**: 批量操作减少 RTT
- **连接池**: 合理配置最大连接数
- **序列化**: 使用高效的序列化方式

---

## 📦 交付清单

### 代码交付

- [ ] 源代码（含注释和文档）
- [ ] 单元测试代码
- [ ] 集成测试代码
- [ ] 压测脚本

### 文档交付

- [ ] 架构设计文档
- [ ] 接口文档（API）
- [ ] 部署文档
- [ ] 运维手册
- [ ] 性能测试报告

### 配置交付

- [ ] application.yml（生产环境）
- [ ] Dockerfile
- [ ] Kubernetes YAML
- [ ] Prometheus 监控配置
- [ ] Logback 日志配置

---

## 🎯 下一步行动

**立即开始**：

1. **Week 1**: 搭建 Maven 项目，配置依赖
2. **Week 2**: 实现消息模型和协议定义
3. **Week 3**: 完成 Redis 在线路由
4. **Week 4-6**: 实现 Netty 网关核心
5. **Week 7-8**: 集成 Kafka 消息总线
6. **Week 9-10**: 实现消息流程
7. **Week 11-12**: 实现可靠性机制
8. **Week 13**: 完善监控运维
9. **Week 14**: 测试与压测

**项目里程碑**：

- ✅ Week 3: 基础框架完成
- ✅ Week 6: 核心功能完成
- ✅ Week 10: 完整流程打通
- ✅ Week 14: 生产环境就绪

---

## 📚 参考资料

- [Netty 官方文档](https://netty.io/)
- [Kafka 官方文档](https://kafka.apache.org/documentation/)
- [Redis 命令参考](https://redis.io/commands/)
- [JDK 17 文档](https://docs.oracle.com/en/java/javase/17/)

---

**文档版本**: v1.0
**创建日期**: 2026-02-13
**最后更新**: 2026-02-13
**维护者**: NB-IM Team
