package com.github.duerlatter.proxy.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * 代理消息编码器。
 * 负责将 ProxyMessage 对象编码为二进制协议格式，供网络传输。
 *
 * <p>编码的协议格式严格按照以下顺序：
 * <pre>
 * -----------------------------------------------------
 * | Length(4) | Type(1) | SerialNumber(8) | UriLen(1) |
 * | Uri(N)    | Data(M)                               |
 * -----------------------------------------------------
 *
 * 说明：
 * - Length：整条消息的长度，不包含自身的4字节长度字段
 * - Type：消息类型，1字节
 * - SerialNumber：消息序列号，8字节长整型
 * - UriLen：URI长度，1字节，最大255
 * - Uri：URI内容，长度为UriLen，UTF-8编码
 * - Data：消息载荷，长度为M字节，可能为空
 * </pre>
 * </p>
 *
 * 该编码器继承自Netty的MessageToByteEncoder，自动处理编码流程。
 *
 * @author fsren
 * @date 2025-06-26
 */
public class ProxyMessageEncoder extends MessageToByteEncoder<ProxyMessage> {

    /**
     * 消息类型字段大小，单位为字节。
     */
    private static final int TYPE_SIZE = 1;

    /**
     * 消息序列号字段大小，单位为字节。
     */
    private static final int SERIAL_NUMBER_SIZE = 8;

    /**
     * URI长度字段大小，单位为字节。
     */
    private static final int URI_LENGTH_SIZE = 1;

    /**
     * 编码方法，将 ProxyMessage 编码为字节流格式。
     *
     * @param ctx ChannelHandler上下文
     * @param msg 待编码的 ProxyMessage 实例
     * @param out 编码后的字节写入该 ByteBuf
     */
    @Override
    protected void encode(ChannelHandlerContext ctx, ProxyMessage msg, ByteBuf out) {
        int bodyLength = TYPE_SIZE + SERIAL_NUMBER_SIZE + URI_LENGTH_SIZE;
        byte[] uriBytes = null;

        // 计算URI字节长度
        if (msg.getUri() != null) {
            uriBytes = msg.getUri().getBytes();  // 默认UTF-8编码
            bodyLength += uriBytes.length;
        }

        // 计算数据体字节长度
        if (msg.getData() != null) {
            bodyLength += msg.getData().length;
        }

        // 写入消息总长度（不包含本身4字节长度字段）
        out.writeInt(bodyLength);

        // 写入消息类型
        out.writeByte(msg.getType());

        // 写入序列号
        out.writeLong(msg.getSerialNumber());

        // 写入URI长度及内容，如果无URI则写0
        if (uriBytes != null) {
            out.writeByte((byte) uriBytes.length);
            out.writeBytes(uriBytes);
        } else {
            out.writeByte((byte) 0x00);
        }

        // 写入数据体内容（如果存在）
        if (msg.getData() != null) {
            out.writeBytes(msg.getData());
        }
    }
}
