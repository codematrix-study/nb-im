package com.cw.im.common.codec;

import com.cw.im.common.protocol.IMMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * IM 消息解码器
 * 将字节流解码为 IMMessage 对象
 *
 * 格式: [4字节长度] + [JSON字符串]
 *
 * @author cw
 */
@Slf4j
public class IMMessageDecoder extends ByteToMessageDecoder {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        try {
            // 1. 检查是否有足够的数据读取长度字段（4字节）
            if (in.readableBytes() < 4) {
                return;
            }

            // 2. 标记当前读位置
            in.markReaderIndex();

            // 3. 读取消息长度
            int dataLength = in.readInt();

            // 4. 检查数据是否完整
            if (in.readableBytes() < dataLength) {
                // 数据不完整，重置读位置，等待更多数据
                in.resetReaderIndex();
                return;
            }

            // 5. 读取消息内容
            byte[] jsonBytes = new byte[dataLength];
            in.readBytes(jsonBytes);

            // 6. 反序列化为 IMMessage 对象
            String json = new String(jsonBytes, StandardCharsets.UTF_8);
            IMMessage message = OBJECT_MAPPER.readValue(json, IMMessage.class);

            // 7. 添加到输出列表
            out.add(message);

            log.debug("消息解码成功: cmd={}, length={}", message.getCmd(), dataLength);

        } catch (Exception e) {
            log.error("消息解码失败", e);
            // 解码失败时，跳过当前数据包
            in.clear();
        }
    }
}
