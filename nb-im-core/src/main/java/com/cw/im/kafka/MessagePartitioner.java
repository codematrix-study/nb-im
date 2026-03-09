package com.cw.im.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.Partitioner;
import org.apache.kafka.common.Cluster;

import java.util.Map;

/**
 * 自定义 Kafka 消息分区器
 *
 * <p>实现消息分区策略，保证消息顺序</p>
 *
 * <h3>分区策略</h3>
 * <ul>
 *     <li>私聊: 使用 conversationId（min(from, to) + "-" + max(from, to)）作为分区key</li>
 *     <li>群聊: 使用 groupId 作为分区key</li>
 *     <li>公屏: 使用固定值 "broadcast" 作为分区key</li>
 *     <li>ACK: 使用 msgId 作为分区key</li>
 * </ul>
 *
 * <h3>分区Key规则</h3>
 * <ul>
 *     <li>同一 partitionKey 的消息会进入同一分区</li>
 *     <li>同一分区内的消息保证顺序</li>
 *     <li>不同分区的消息可以并行处理</li>
 * </ul>
 *
 * @author cw
 * @since 1.0.0
 */
@Slf4j
public class MessagePartitioner implements Partitioner {

    /**
     * 计算消息应该发送到哪个分区
     *
     * @param topic      Topic名称
     * @param key        消息Key（分区key）
     * @param keyBytes   Key的字节数组
     * @param value      消息Value
     * @param valueBytes Value的字节数组
     * @param cluster    Kafka集群元数据
     * @return 分区号
     */
    @Override
    public int partition(String topic, Object key, byte[] keyBytes,
                        Object value, byte[] valueBytes, Cluster cluster) {
        // 获取分区总数
        int partitionCount = cluster.partitionCountForTopic(topic);

        // 如果没有指定key，使用轮询策略（Kafka默认）
        if (key == null) {
            log.debug("消息key为空，使用轮询策略: topic={}", topic);
            // 返回-1表示使用Kafka默认的轮询策略
            return -1;
        }

        // 使用hashCode保证同一partitionKey进入同一分区
        String partitionKey = (String) key;
        int partition = Math.abs(partitionKey.hashCode()) % partitionCount;

        log.debug("消息分区计算: topic={}, partitionKey={}, partition={}, partitionCount={}",
                topic, partitionKey, partition, partitionCount);

        return partition;
    }

    /**
     * 关闭分区器（释放资源）
     */
    @Override
    public void close() {
        log.info("MessagePartitioner 已关闭");
    }

    /**
     * 配置分区器
     *
     * @param configs 配置参数
     */
    @Override
    public void configure(Map<String, ?> configs) {
        log.info("MessagePartitioner 配置: configs={}", configs);
    }

    // ==================== 工具方法 ====================

    /**
     * 生成私聊消息的分区Key
     * <p>规则: min(from, to) + "-" + max(from, to)</p>
     *
     * @param from 发送者ID
     * @param to   接收者ID
     * @return 分区Key（会话ID）
     */
    public static String generatePrivateChatPartitionKey(Long from, Long to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("from 和 to 不能为 null");
        }
        long min = Math.min(from, to);
        long max = Math.max(from, to);
        return min + "-" + max;
    }

    /**
     * 生成群聊消息的分区Key
     * <p>规则: groupId</p>
     *
     * @param groupId 群组ID
     * @return 分区Key
     */
    public static String generateGroupChatPartitionKey(Long groupId) {
        if (groupId == null) {
            throw new IllegalArgumentException("groupId 不能为 null");
        }
        return String.valueOf(groupId);
    }

    /**
     * 生成公屏消息的分区Key
     * <p>规则: 固定值 "broadcast"</p>
     *
     * @return 分区Key
     */
    public static String generateBroadcastPartitionKey() {
        return "broadcast";
    }

    /**
     * 生成ACK消息的分区Key
     * <p>规则: msgId</p>
     *
     * @param msgId 消息ID
     * @return 分区Key
     */
    public static String generateAckPartitionKey(String msgId) {
        if (msgId == null) {
            throw new IllegalArgumentException("msgId 不能为 null");
        }
        return msgId;
    }
}
