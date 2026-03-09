package com.cw.im.common.codec;

import com.cw.im.common.protocol.IMMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.extern.slf4j.Slf4j;

/**
 * IM 消息编码器
 * 将 IMMessage 对象编码为字节流
 *
 * 格式: [4字节长度] + [JSON字符串]
 *
 * @author cw
 */
@Slf4j
public class IMMessageEncoder extends MessageToByteEncoder<IMMessage> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    protected void encode(ChannelHandlerContext ctx, IMMessage msg, ByteBuf out) throws Exception {
        try {
            // 1. 将消息对象序列化为 JSON 字符串
            String json = OBJECT_MAPPER.writeValueAsString(msg);

            // 2. 将 JSON 字符串转换为字节数组
            byte[] jsonBytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);

            // 3. 先写入消息长度（4字节）
            out.writeInt(jsonBytes.length);

            // 4. 再写入消息内容
            out.writeBytes(jsonBytes);

            log.debug("消息编码成功: cmd={}, length={}", msg.getCmd(), jsonBytes.length);

        } catch (Exception e) {
            log.error("消息编码失败: msg={}", msg, e);
            throw e;
        }
    }
}
