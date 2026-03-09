# NB-IM 项目结构说明

本文档详细说明 NB-IM 项目的目录结构和各模块职责。

## 目录树

```
nb-im/
├── nb-im-common/              # 公共模块
│   ├── src/main/java/
│   │   └── com/cw/im/common/
│   │       ├── model/         # 数据模型
│   │       │   ├── IMMessage.java
│   │       │   ├── MessageHeader.java
│   │       │   ├── MessageBody.java
│   │       │   ├── MessageType.java
│   │       │   └── AckMessage.java
│   │       ├── protocol/      # 协议定义
│   │       │   ├── Command.java
│   │       │   └── ProtocolVersion.java
│   │       ├── constants/     # 常量定义
│   │       │   ├── IMConstants.java
│   │       │   ├── RedisKeys.java
│   │       │   └── KafkaTopics.java
│   │       ├── codec/         # 编解码器
│   │       │   ├── IMMessageEncoder.java
│   │       │   └── IMMessageDecoder.java
│   │       └── utils/         # 工具类
│   │           └── JsonUtil.java
│   └── src/test/java/         # 单元测试
│
├── nb-im-core/                # 核心模块
│   ├── src/main/java/
│   │   └── com/cw/im/
│   │       ├── redis/         # Redis 客户端
│   │       │   ├── RedisManager.java
│   │       │   ├── OnlineUserManager.java
│   │       │   ├── HeartbeatMonitor.java
│   │       │   └── OnlineStatusService.java
│   │       ├── kafka/         # Kafka 客户端
│   │       │   ├── KafkaProducerManager.java
│   │       │   ├── KafkaConsumerManager.java
│   │       │   ├── KafkaConsumerService.java
│   │       │   └── MessagePartitioner.java
│   │       ├── monitoring/    # 监控组件
│   │       │   ├── metrics/   # 指标收集
│   │       │   ├── health/    # 健康检查
│   │       │   ├── performance/# 性能监控
│   │       │   ├── logging/   # 审计日志
│   │       │   ├── alerting/  # 告警引擎
│   │       │   └── MonitoringManager.java
│   │       └── reliability/   # 可靠性组件
│   │           ├── MessagePersistenceStore.java
│   │           ├── MessageStatusTracker.java
│   │           ├── MessageRetryManager.java
│   │           ├── DeadLetterQueue.java
│   │           ├── AckTimeoutMonitor.java
│   │           └── ReliabilityMetrics.java
│   └── src/test/java/         # 单元测试
│
├── nb-im-server/              # 网关服务
│   ├── src/main/java/
│   │   └── com/cw/im/server/
│   │       ├── NettyIMServer.java          # 主启动类
│   │       ├── handler/                   # Netty Handler
│   │       │   ├── AuthHandler.java        # 认证
│   │       │   ├── HeartbeatHandler.java   # 心跳
│   │       │   ├── ConnectionHandler.java  # 连接管理
│   │       │   ├── RouteHandler.java       # 消息路由
│   │       │   ├── PrivateChatHandler.java # 私聊
│   │       │   ├── GroupChatHandler.java   # 群聊
│   │       │   ├── PublicChatHandler.java  # 公屏
│   │       │   └── AckHandler.java         # ACK处理
│   │       ├── channel/                   # Channel管理
│   │       │   ├── ChannelManager.java
│   │       │   └── ChannelAttributes.java
│   │       ├── consumer/                  # Kafka消费者
│   │       │   ├── PushMessageConsumer.java
│   │       │   ├── GroupMessagePushConsumer.java
│   │       │   ├── PublicBroadcastConsumer.java
│   │       │   └── AckConsumer.java
│   │       └── api/                       # 管理API
│   │           ├── MonitoringService.java
│   │           ├── AdminApiServer.java
│   │           └── HttpResponse.java
│   ├── src/main/resources/
│   │   ├── application.yml                # 配置文件
│   │   ├── logback.xml                    # 日志配置
│   │   └── static/
│   │       └── monitoring-dashboard.html  # 监控仪表板
│   └── src/test/java/                     # 集成测试
│
├── nb-im-biz-demo/            # 业务服务示例（可选）
│
├── k8s/                       # Kubernetes部署配置
│   ├── deployment.yaml
│   ├── service.yaml
│   └── configmap.yaml
│
├── docs/                      # 文档
│   ├── architecture.md
│   ├── api.md
│   └── deployment.md
│
├── scripts/                   # 脚本
│   ├── start.sh
│   ├── stop.sh
│   └── deploy.sh
│
├── pom.xml                    # Maven父POM
├── docker-compose.yml         # Docker Compose配置
├── Dockerfile                 # Docker镜像构建
├── README.md                  # 项目说明
├── CHANGELOG.md               # 版本历史
├── CONTRIBUTING.md            # 贡献指南
├── LICENSE                    # 许可证
└── .gitignore                 # Git忽略文件
```

## 模块说明

### nb-im-common

**职责**: 公共组件和数据模型

**主要内容**:
- 消息模型定义（IMMessage, MessageHeader, MessageBody）
- 协议常量定义（Command, MessageType）
- 编解码器（JSON序列化/反序列化）
- Redis键模板、Kafka主题常量
- 通用工具类

**依赖**: 无其他模块依赖

### nb-im-core

**职责**: 核心业务逻辑

**主要内容**:
- Redis客户端封装和在线状态管理
- Kafka生产者/消费者封装
- 监控组件（指标收集、健康检查、告警）
- 可靠性组件（持久化、重试、死信队列）

**依赖**: nb-im-common

### nb-im-server

**职责**: 网关服务启动和Netty集成

**主要内容**:
- Netty服务器启动类
- Handler Pipeline实现
- Channel管理
- Kafka消费者实现
- 管理REST API
- 监控仪表板

**依赖**: nb-im-common, nb-im-core

### nb-im-biz-demo

**职责**: 业务服务示例（可选）

**主要内容**:
- 业务系统Kafka消费者示例
- 消息持久化示例
- REST API示例

**依赖**: nb-im-common

## 包命名规范

```
com.cw.im.{module}.{layer}

module: common, core, server
layer: model, service, handler, consumer, api
```

示例:
- `com.cw.im.common.model` - 公共模型
- `com.cw.im.core.redis` - Redis核心组件
- `com.cw.im.server.handler` - 服务器Handler

## 配置文件说明

### application.yml

主配置文件，包含：
- 服务器配置（端口、线程数）
- Redis连接配置
- Kafka配置
- 监控配置
- 日志配置

### logback.xml

日志配置，包含：
- 日志级别
- 日志格式
- 日志文件路径
- 滚动策略

### docker-compose.yml

Docker Compose配置，定义：
- Redis服务
- Kafka服务
- Zookeeper服务
- NB-IM服务

## 部署产物

### 编译后结构

```
nb-im-server/target/
├── nb-im-server-1.0.0.jar           # 可执行JAR
├── lib/                              # 依赖JAR
└── classes/                          # 编译后的class文件
```

### Docker镜像

```
nb-im:1.0.0
├── /app/app.jar                      # 应用JAR
├── /app/logs/                        # 日志目录
└── /app/                             # 工作目录
```

## 扩展指南

### 添加新的Handler

1. 在 `nb-im-server/src/main/java/com/cw/im/server/handler/` 创建Handler类
2. 继承 `SimpleChannelInboundHandler<IMMessage>`
3. 实现 `channelRead0()` 方法
4. 在 `RouteHandler` 中注册新的Handler
5. 添加对应的单元测试

### 添加新的监控指标

1. 在 `nb-im-core/src/main/java/com/cw/im/monitoring/metrics/` 创建Collector
2. 继承 `AbstractMetricsCollector`
3. 实现 `collect()` 方法
4. 在 `MonitoringManager` 中注册
5. 添加API端点（可选）

### 添加新的消息类型

1. 在 `nb-im-common` 中添加枚举值
2. 创建对应的Handler
3. 更新RouteHandler路由逻辑
4. 更新文档

---

**最后更新**: 2026-03-09
