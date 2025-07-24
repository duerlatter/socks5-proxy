package com.github.duerlatter.proxy.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

import java.nio.charset.StandardCharsets;

/**
 * 代理消息解码器，用于将接收到的字节流解码为 ProxyMessage 对象。
 *
 * <p>协议格式说明：
 * <pre>
 * ---------------------------------------------------------------------------------
 * | Length(4) | Type(1) | SerialNumber(8) | UriLen(1) | Uri(N) | Data(M) |
 * ---------------------------------------------------------------------------------
 * 其中：
 * - Length：表示消息体总长度（不包含自身的4字节Length字段）
 * - Type：消息类型，1字节
 * - SerialNumber：消息序列号，8字节长整型
 * - UriLen：URI字段长度，1字节
 * - Uri：URI内容，长度为 UriLen，UTF-8编码
 * - Data：消息载荷，剩余部分字节数
 * </pre>
 * </p>
 *
 * <p>解码器继承自 Netty 的 LengthFieldBasedFrameDecoder，自动处理TCP粘包和拆包问题，
 * 只需实现协议字段顺序的读取和转换为 ProxyMessage。</p>
 *
 * @author fs
 * @date 2025-06-26
 */
public class ProxyMessageDecoder extends LengthFieldBasedFrameDecoder {

    /**
     * 协议头中Length字段字节数。
     */
    private static final byte HEADER_SIZE = 4;

    /**
     * Type字段大小，单位字节。
     */
    private static final int TYPE_SIZE = 1;

    /**
     * SerialNumber字段大小，单位字节。
     */
    private static final int SERIAL_NUMBER_SIZE = 8;

    /**
     * Uri长度字段大小，单位字节。
     */
    private static final int URI_LENGTH_SIZE = 1;

    /**
     * 构造函数，初始化解码器参数。
     *
     * @param maxFrameLength 最大帧长度，即允许的最大消息体长度
     * @param lengthFieldOffset Length字段偏移量，通常为0
     * @param lengthFieldLength Length字段长度，通常为4
     * @param lengthAdjustment 长度调整，通常为0
     * @param initialBytesToStrip 解码时跳过的字节数，通常跳过Length字段（4字节）
     */
    public ProxyMessageDecoder(int maxFrameLength, int lengthFieldOffset, int lengthFieldLength, int lengthAdjustment,
                               int initialBytesToStrip) {
        super(maxFrameLength, lengthFieldOffset, lengthFieldLength, lengthAdjustment, initialBytesToStrip);
    }

    /**
     * 支持快速失败的构造函数。
     *
     * @param maxFrameLength 最大帧长度
     * @param lengthFieldOffset Length字段偏移
     * @param lengthFieldLength Length字段长度
     * @param lengthAdjustment 长度调整
     * @param initialBytesToStrip 跳过字节数
     * @param failFast 是否快速失败
     */
    public ProxyMessageDecoder(int maxFrameLength, int lengthFieldOffset, int lengthFieldLength, int lengthAdjustment,
                               int initialBytesToStrip, boolean failFast) {
        super(maxFrameLength, lengthFieldOffset, lengthFieldLength, lengthAdjustment, initialBytesToStrip, failFast);
    }

    /**
     * 解码方法，将字节流解析为 ProxyMessage 实例。
     *
     * @param ctx ChannelHandler上下文
     * @param in2 输入的ByteBuf数据缓冲区
     * @return 解析后的ProxyMessage对象，或null（若数据不完整）
     * @throws Exception 解码异常
     */
    @Override
    protected ProxyMessage decode(ChannelHandlerContext ctx, ByteBuf in2) throws Exception {
        // 使用父类实现的粘包拆包处理，返回完整帧ByteBuf
        ByteBuf in = (ByteBuf) super.decode(ctx, in2);
        if (in == null) {
            return null; // 数据未达到一帧完整长度，等待更多数据
        }

        if (in.readableBytes() < HEADER_SIZE) {
            return null; // 防御性检查，数据长度不足4字节，非法
        }

        // 读取消息体总长度（这里一般已被父类处理截断，不必重复读取）
        int frameLength = in.readInt();

        // 再次检查数据长度是否满足frameLength，防止异常
        if (in.readableBytes() < frameLength) {
            return null;
        }

        ProxyMessage proxyMessage = new ProxyMessage();

        // 读取消息类型
        byte type = in.readByte();
        proxyMessage.setType(type);

        // 读取消息序列号（8字节）
        long sn = in.readLong();
        proxyMessage.setSerialNumber(sn);

        // 读取URI长度
        byte uriLength = in.readByte();

        // 读取URI字节并转换成字符串（UTF-8编码）
        byte[] uriBytes = new byte[uriLength];
        in.readBytes(uriBytes);
        proxyMessage.setUri(new String(uriBytes, StandardCharsets.UTF_8));

        // 计算剩余数据体长度
        int dataLength = frameLength - TYPE_SIZE - SERIAL_NUMBER_SIZE - URI_LENGTH_SIZE - uriLength;

        // 读取数据体内容
        byte[] data = new byte[dataLength];
        in.readBytes(data);
        proxyMessage.setData(data);

        // 释放ByteBuf资源，防止内存泄露
        in.release();

        return proxyMessage;
    }
}
