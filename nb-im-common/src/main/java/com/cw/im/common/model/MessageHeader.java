package com.cw.im.common.model;

import com.cw.im.common.protocol.CommandType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * IM消息头
 *
 * <p>包含消息的元数据信息，用于消息路由、排序和验证</p>
 *
 * @author cw
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageHeader implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息ID（全局唯一）
     * <p>使用UUID或雪花算法生成，用于消息去重和ACK确认</p>
     */
    private String msgId;

    /**
     * 命令类型/消息类型
     * <p>标识消息的业务类型：私聊、群聊、公屏、ACK、心跳等</p>
     */
    private CommandType cmd;

    /**
     * 发送者用户ID
     */
    private Long from;

    /**
     * 接收者ID
     * <p>私聊时为用户ID，群聊时为群组ID</p>
     */
    private Long to;

    /**
     * 消息时间戳（毫秒）
     * <p>用于消息排序和去重</p>
     */
    private Long timestamp;

    /**
     * 协议版本号
     * <p>用于协议升级和兼容性处理</p>
     */
    private String version;

    /**
     * 扩展字段
     * <p>用于存储额外的元数据信息，如设备类型、客户端版本等</p>
     */
    private java.util.Map<String, Object> extras;
}
