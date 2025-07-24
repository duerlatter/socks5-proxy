package com.github.duerlatter.proxy.protocol;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;

/**
 * 代理客户端与代理服务器之间消息交换的协议实体类。
 * <p>
 * 该类定义了多种消息类型，包括心跳、认证、连接、断开连接和数据传输，
 * 并封装了消息相关的必要属性：类型、流水号、命令URI及数据内容。
 * </p>
 * <p>
 * 该类实现Serializable接口，支持序列化以便网络传输。
 * </p>
 *
 * @author fsren
 * @date 2025-06-26
 */
@Getter
@Setter
public class ProxyMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1767891164147281831L;

    /**
     * 心跳消息类型，客户端与服务器保持连接的心跳检测。
     */
    public static final byte TYPE_HEARTBEAT = 0x07;

    /**
     * 认证消息类型，用于客户端发送认证信息(clientKey)，
     * 服务器验证客户端身份。
     */
    public static final byte C_TYPE_AUTH = 0x01;

    // /** 保活确认消息 */
    // public static final byte TYPE_ACK = 0x02;

    /**
     * 代理后端服务器建立连接消息类型，
     * 表示客户端请求代理服务器建立连接。
     */
    public static final byte TYPE_CONNECT = 0x03;

    /**
     * 代理后端服务器断开连接消息类型，
     * 表示连接断开通知。
     */
    public static final byte TYPE_DISCONNECT = 0x04;

    /**
     * 代理数据传输消息类型，
     * 传输代理数据内容。
     */
    public static final byte P_TYPE_TRANSFER = 0x05;

    /**
     * 消息类型，标识当前消息的种类，如心跳、认证、连接等。
     */
    private byte type;

    /**
     * 消息流水号，用于标识消息顺序或唯一标识消息。
     */
    private long serialNumber;

    /**
     * 消息命令请求信息，通常用于承载URI或特定命令参数。
     */
    private String uri;

    /**
     * 消息传输的数据内容，字节数组形式存储具体数据。
     */
    private byte[] data;

    /**
     * 返回消息的字符串表示，包含消息类型、流水号、URI及数据内容（数组）。
     * 用于调试和日志打印。
     *
     * @return 消息的字符串形式
     */
    @Override
    public String toString() {
        return "ProxyMessage [type=" + type + ", serialNumber=" + serialNumber + ", uri=" + uri + ", data=" + Arrays.toString(data) + "]";
    }
}
