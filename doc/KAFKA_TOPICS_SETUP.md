# Kafka Topics 创建脚本

## 创建 Topics

使用以下命令在 Kafka 集群中创建所需的 Topics：

```bash
# 1. 客户端发送消息Topic（32分区，3副本）
kafka-topics.sh --create \
  --bootstrap-server 192.168.215.2:9092 \
  --topic im-msg-send \
  --partitions 32 \
  --replication-factor 3 \
  --config retention.ms=604800000

# 2. 业务推送消息Topic（32分区，3副本）
kafka-topics.sh --create \
  --bootstrap-server 192.168.215.2:9092 \
  --topic im-msg-push \
  --partitions 32 \
  --replication-factor 3 \
  --config retention.ms=86400000

# 3. ACK确认消息Topic（16分区，3副本）
kafka-topics.sh --create \
  --bootstrap-server 192.168.215.2:9092 \
  --topic im-ack \
  --partitions 16 \
  --replication-factor 3 \
  --config retention.ms=259200000

# 4. 离线消息Topic（16分区，3副本）
kafka-topics.sh --create \
  --bootstrap-server 192.168.215.2:9092 \
  --topic im-msg-offline \
  --partitions 16 \
  --replication-factor 3 \
  --config retention.ms=604800000

# 5. 系统通知Topic（8分区，3副本）
kafka-topics.sh --create \
  --bootstrap-server 192.168.215.2:9092 \
  --topic im-system-notice \
  --partitions 8 \
  --replication-factor 3 \
  --config retention.ms=2592000000

# 6. 网关状态Topic（8分区，3副本）
kafka-topics.sh --create \
  --bootstrap-server 192.168.215.2:9092 \
  --topic im-gateway-status \
  --partitions 8 \
  --replication-factor 3 \
  --config retention.ms=604800000
```

## 验证 Topics

查看已创建的 Topics：

```bash
kafka-topics.sh --list --bootstrap-server 192.168.215.2:9092
```

查看 Topic 详情：

```bash
kafka-topics.sh --describe \
  --bootstrap-server 192.168.215.2:9092 \
  --topic im-msg-send
```

## 删除 Topics（如果需要）

```bash
kafka-topics.sh --delete \
  --bootstrap-server 192.168.215.2:9092 \
  --topic im-msg-send
```

## 注意事项

1. **副本数**: 需要根据 Kafka 集群的实际节点数调整，副本数不能超过节点数
2. **分区数**: 可以根据实际并发量调整，建议为 2 的幂次方
3. **保留时间**:
   - im-msg-send: 7天 (604800000ms)
   - im-msg-push: 1天 (86400000ms)
   - im-ack: 3天 (259200000ms)
   - im-msg-offline: 7天 (604800000ms)
   - im-system-notice: 30天 (2592000000ms)
   - im-gateway-status: 7天 (604800000ms)
