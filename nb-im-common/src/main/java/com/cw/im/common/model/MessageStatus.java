package com.cw.im.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 消息状态模型
 *
 * <p>用于跟踪消息的生命周期状态</p>
 *
 * <h3>消息状态流转</h3>
 * <pre>
 * SENDING（发送中）
 *     ↓
 * SENT（已发送到Kafka）
 *     ↓
 * DELIVERED（已投递到客户端）
 *     ↓
 * READ（已读）
 *
 * 或
 *
 * SENDING → FAILED（发送失败）
 * </pre>
 *
 * <h3>状态说明</h3>
 * <ul>
 *     <li>SENDING: 消息正在发送中</li>
 *     <li>SENT: 消息已成功发送到Kafka</li>
 *     <li>DELIVERED: 消息已投递到目标客户端</li>
 *     <li>READ: 目标用户已读消息</li>
 *     <li>FAILED: 消息发送失败</li>
 * </ul>
 *
 * @author cw
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageStatus implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息ID
     */
    private String msgId;

    /**
     * 发送者用户ID
     */
    private Long fromUserId;

    /**
     * 接收者用户ID
     */
    private Long toUserId;

    /**
     * 当前状态
     */
    private Status status;

    /**
     * 创建时间戳
     */
    private Long createTime;

    /**
     * 状态更新时间戳
     */
    private Long updateTime;

    /**
     * 失败原因（可选）
     */
    private String failureReason;

    /**
     * 重试次数
     */
    private Integer retryCount;

    /**
     * 网关ID（可选）
     */
    private String gatewayId;

    /**
     * 额外信息（可选）
     */
    private String extras;

    /**
     * 消息状态枚举
     */
    public enum Status {
        /**
         * 发送中
         */
        SENDING,

        /**
         * 已发送
         */
        SENT,

        /**
         * 已投递
         */
        DELIVERED,

        /**
         * 已读
         */
        READ,

        /**
         * 失败
         */
        FAILED
    }

    /**
     * 创建初始状态
     *
     * @param msgId      消息ID
     * @param fromUserId 发送者ID
     * @param toUserId   接收者ID
     * @return 消息状态对象
     */
    public static MessageStatus createInitial(String msgId, Long fromUserId, Long toUserId) {
        long now = System.currentTimeMillis();
        return MessageStatus.builder()
                .msgId(msgId)
                .fromUserId(fromUserId)
                .toUserId(toUserId)
                .status(Status.SENDING)
                .createTime(now)
                .updateTime(now)
                .retryCount(0)
                .build();
    }

    /**
     * 更新状态
     *
     * @param newStatus 新状态
     */
    public void updateStatus(Status newStatus) {
        this.status = newStatus;
        this.updateTime = System.currentTimeMillis();
    }

    /**
     * 更新状态并设置失败原因
     *
     * @param newStatus     新状态
     * @param failureReason 失败原因
     */
    public void updateStatus(Status newStatus, String failureReason) {
        this.status = newStatus;
        this.failureReason = failureReason;
        this.updateTime = System.currentTimeMillis();
    }

    /**
     * 增加重试次数
     */
    public void incrementRetryCount() {
        if (this.retryCount == null) {
            this.retryCount = 0;
        }
        this.retryCount++;
        this.updateTime = System.currentTimeMillis();
    }

    /**
     * 检查是否为最终状态
     *
     * @return true-已到达最终状态, false-仍在处理中
     */
    public boolean isFinalStatus() {
        return status == Status.DELIVERED
                || status == Status.READ
                || status == Status.FAILED;
    }

    /**
     * 检查是否为成功状态
     *
     * @return true-成功, false-失败或处理中
     */
    public boolean isSuccessStatus() {
        return status == Status.DELIVERED || status == Status.READ;
    }

    /**
     * 检查是否为失败状态
     *
     * @return true-失败, false-成功或处理中
     */
    public boolean isFailedStatus() {
        return status == Status.FAILED;
    }

    /**
     * 检查是否可以重试
     *
     * @param maxRetryCount 最大重试次数
     * @return true-可以重试, false-不可重试
     */
    public boolean canRetry(int maxRetryCount) {
        return status == Status.FAILED
                && retryCount != null
                && retryCount < maxRetryCount;
    }

    /**
     * 获取状态持续时间（毫秒）
     *
     * @return 持续时间
     */
    public long getDuration() {
        if (updateTime == null || createTime == null) {
            return 0;
        }
        return updateTime - createTime;
    }
}
