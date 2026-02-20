package com.cw.im.common.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * IM 消息协议
 *
 * @author cw
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IMMessage {

    /**
     * 消息ID（全局唯一）
     */
    private String msgId;

    /**
     * 命令类型
     */
    private CommandType cmd;

    /**
     * 发送者用户ID
     */
    private Long from;

    /**
     * 接收者用户ID（私聊）或群组ID（群聊）
     */
    private Long to;

    /**
     * 时间戳
     */
    private Long timestamp;

    /**
     * 消息体（JSON 字符串）
     */
    private String payload;
}
