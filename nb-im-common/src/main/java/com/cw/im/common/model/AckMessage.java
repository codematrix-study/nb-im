package com.cw.im.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * ACK确认消息模型
 *
 * <p>用于确认消息的接收和处理状态</p>
 *
 * <h3>ACK类型</h3>
 * <ul>
 *     <li>SUCCESS: 消息成功接收和处理</li>
 *     <li>FAILED: 消息处理失败</li>
 *     <li>TIMEOUT: 消息处理超时</li>
 * </ul>
 *
 * <h3>ACK流程</h3>
 * <pre>
 * 1. 客户端发送消息
 * 2. Gateway接收并发送到Kafka
 * 3. Gateway返回接收ACK
 * 4. 业务系统处理消息
 * 5. 业务系统推送给目标用户
 * 6. 目标用户客户端返回ACK
 * 7. Gateway转发ACK到Kafka
 * 8. 业务系统更新消息状态
 * </pre>
 *
 * @author cw
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AckMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 原始消息ID
     */
    private String msgId;

    /**
     * 发送者用户ID
     */
    private Long from;

    /**
     * 接收者用户ID
     */
    private Long to;

    /**
     * 时间戳
     */
    private Long timestamp;

    /**
     * ACK状态
     */
    private AckStatus status;

    /**
     * 失败原因（可选）
     */
    private String reason;

    /**
     * 网关ID（可选）
     */
    private String gatewayId;

    /**
     * 额外信息（可选）
     */
    private String extras;

    /**
     * ACK状态枚举
     */
    public enum AckStatus {
        /**
         * 成功
         */
        SUCCESS,

        /**
         * 失败
         */
        FAILED,

        /**
         * 超时
         */
        TIMEOUT
    }

    /**
     * 创建成功ACK
     *
     * @param msgId 原始消息ID
     * @param from  发送者
     * @param to    接收者
     * @return ACK消息
     */
    public static AckMessage success(String msgId, Long from, Long to) {
        return AckMessage.builder()
                .msgId(msgId)
                .from(from)
                .to(to)
                .timestamp(System.currentTimeMillis())
                .status(AckStatus.SUCCESS)
                .build();
    }

    /**
     * 创建失败ACK
     *
     * @param msgId  原始消息ID
     * @param from   发送者
     * @param to     接收者
     * @param reason 失败原因
     * @return ACK消息
     */
    public static AckMessage failed(String msgId, Long from, Long to, String reason) {
        return AckMessage.builder()
                .msgId(msgId)
                .from(from)
                .to(to)
                .timestamp(System.currentTimeMillis())
                .status(AckStatus.FAILED)
                .reason(reason)
                .build();
    }

    /**
     * 创建超时ACK
     *
     * @param msgId 原始消息ID
     * @param from  发送者
     * @param to    接收者
     * @return ACK消息
     */
    public static AckMessage timeout(String msgId, Long from, Long to) {
        return AckMessage.builder()
                .msgId(msgId)
                .from(from)
                .to(to)
                .timestamp(System.currentTimeMillis())
                .status(AckStatus.TIMEOUT)
                .reason("Message processing timeout")
                .build();
    }

    /**
     * 检查ACK是否成功
     *
     * @return true-成功, false-失败或超时
     */
    public boolean isSuccess() {
        return status == AckStatus.SUCCESS;
    }

    /**
     * 检查ACK是否失败
     *
     * @return true-失败, false-成功或超时
     */
    public boolean isFailed() {
        return status == AckStatus.FAILED;
    }

    /**
     * 检查ACK是否超时
     *
     * @return true-超时, false-成功或失败
     */
    public boolean isTimeout() {
        return status == AckStatus.TIMEOUT;
    }
}
