package com.cw.im.common.protocol;

import io.netty.buffer.ByteBuf;

/**
 * 数据包编解码器接口
 *
 * <p>定义消息的编码和解码规范，支持多种序列化方式</p>
 *
 * <h3>数据包格式</h3>
 * <pre>
 * +--------+--------+--------+--------+------------------+
 * |           Length (4 bytes)              |               |
 * +--------+--------+--------+--------+------------------+
 * |                    JSON Body (N bytes)                   |
 * +---------------------------------------------------------+
 * </pre>
 *
 * @author cw
 * @since 1.0.0
 */
public interface PacketCodec {

    /**
     * 编码消息
     *
     * <p>将IMMessage对象编码为ByteBuf，格式：[4字节长度] + [JSON体]</p>
     *
     * @param message 消息对象
     * @return 编码后的ByteBuf
     * @throws Exception 编码异常
     */
    ByteBuf encode(IMMessage message) throws Exception;

    /**
     * 解码消息
     *
     * <p>从ByteBuf中解析IMMessage对象，格式：[4字节长度] + [JSON体]</p>
     *
     * @param byteBuf 字节缓冲区
     * @return 解码后的消息对象
     * @throws Exception 解码异常
     */
    IMMessage decode(ByteBuf byteBuf) throws Exception;

    /**
     * 获取协议版本
     *
     * @return 版本号
     */
    default String getVersion() {
        return ProtocolVersion.getCurrentVersion();
    }
}
