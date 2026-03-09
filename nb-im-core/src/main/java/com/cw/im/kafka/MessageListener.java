package com.cw.im.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;

/**
 * Kafka 消息消费监听器接口
 *
 * <p>定义消息消费的回调方法，实现自定义的消息处理逻辑</p>
 *
 * <h3>使用场景</h3>
 * <ul>
 *     <li>推送消息消费（PushMessageConsumer）</li>
 *     <li>ACK消息消费</li>
 *     <li>系统通知消费</li>
 *     <li>离线消息消费</li>
 * </ul>
 *
 * <h3>生命周期</h3>
 * <ol>
 *     <li>onMessage: 处理每条消息</li>
 *     <li>onException: 处理异常情况</li>
 *     <li>onComplete: 消费完成回调（可选）</li>
 * </ol>
 *
 * @author cw
 * @since 1.0.0
 */
public interface MessageListener {

    /**
     * 处理消息
     * <p>当Kafka消费者拉取到消息时，会调用此方法</p>
     *
     * @param record Kafka消息记录
     */
    void onMessage(ConsumerRecord<String, String> record);

    /**
     * 处理异常
     * <p>当消息处理过程中发生异常时，会调用此方法</p>
     * <p>可以实现重试、记录日志、发送告警等逻辑</p>
     *
     * @param exception 异常对象
     */
    void onException(Exception exception);

    /**
     * 消费完成回调
     * <p>当一批消息处理完成后，会调用此方法</p>
     * <p>可以用于提交Offset、清理资源等操作</p>
     * <p>默认实现为空，子类可以选择性重写</p>
     */
    default void onComplete() {
        // 默认空实现
    }
}
