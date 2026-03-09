package com.cw.im.common.protocol;

import com.cw.im.common.model.MessageBody;
import com.cw.im.common.model.MessageHeader;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * IM统一消息体
 *
 * <p>包含完整的消息结构：消息头(header)和消息体(body)</p>
 *
 * <h3>消息格式</h3>
 * <pre>
 * {
 *   "header": {
 *     "msgId": "uuid",
 *     "cmd": "PRIVATE_CHAT",
 *     "from": 1001,
 *     "to": 1002,
 *     "timestamp": 1234567890,
 *     "version": "1.0",
 *     "extras": {}
 *   },
 *   "body": {
 *     "content": "hello",
 *     "contentType": "text",
 *     "extras": {}
 *   }
 * }
 * </pre>
 *
 * <h3>协议传输格式</h3>
 * <p>网络传输格式: [4字节长度] + [JSON体]</p>
 *
 * @author cw
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IMMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息头
     * <p>包含消息的元数据信息，用于路由、排序和验证</p>
     */
    private MessageHeader header;

    /**
     * 消息体
     * <p>包含消息的实际内容数据</p>
     */
    private MessageBody body;

    /**
     * 快捷方法：获取消息ID
     */
    public String getMsgId() {
        return header != null ? header.getMsgId() : null;
    }

    /**
     * 快捷方法：获取命令类型
     */
    public CommandType getCmd() {
        return header != null ? header.getCmd() : null;
    }

    /**
     * 快捷方法：获取发送者ID
     */
    public Long getFrom() {
        return header != null ? header.getFrom() : null;
    }

    /**
     * 快捷方法：获取接收者ID
     */
    public Long getTo() {
        return header != null ? header.getTo() : null;
    }

    /**
     * 快捷方法：获取时间戳
     */
    public Long getTimestamp() {
        return header != null ? header.getTimestamp() : null;
    }

    /**
     * 快捷方法：获取消息内容
     */
    public String getContent() {
        return body != null ? body.getContent() : null;
    }

    /**
     * 验证消息是否有效
     *
     * @return true-消息有效, false-消息无效
     */
    public boolean isValid() {
        return header != null
                && header.getMsgId() != null
                && header.getCmd() != null
                && header.getFrom() != null
                && header.getTo() != null
                && header.getTimestamp() != null;
    }
}
