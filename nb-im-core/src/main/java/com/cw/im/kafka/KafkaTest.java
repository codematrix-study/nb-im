package com.cw.im.kafka;

/**
 * Kafka 测试类 - 演示如何使用
 *
 * @author cw
 */
public class KafkaTest {

    public static void main(String[] args) {
        // ==================== 生产者测试 ====================
        KafkaProducerManager producer = new KafkaProducerManager();

        // 异步发送消息
        producer.sendAsync("im-msg-send", "conversation-123", "Hello Kafka!");

        // 同步发送消息
        // producer.sendSync("im-msg-send", "conversation-123", "Hello Kafka!");

        // ==================== 消费者测试 ====================
        KafkaConsumerManager consumer = new KafkaConsumerManager();

        // 订阅 Topic
        consumer.subscribe("im-msg-push");

        // 开始消费
        consumer.startConsume(record -> {
            System.out.println("收到消息: " + record.value());
        });

        // 注意：startConsume 是阻塞方法，实际使用时应该在单独线程中运行
    }
}
