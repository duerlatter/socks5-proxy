package com.github.duerlatter.proxy.protocol;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

/**
 * 常量类，定义了代理协议相关的关键属性键。
 *
 * 这些属性键用于在Netty的Channel中存储和检索关联的上下文信息，
 * 便于在不同的Handler之间传递状态和连接关系。
 *
 * @author fsren
 * @date 2025-06-26
 */
public class Constants {

    /**
     * 私有构造器，防止实例化
     */
    private Constants() {
    }

    /**
     * NEXT_CHANNEL：
     * 用于存储当前Channel关联的“下一个Channel”，
     * 比如代理客户端通道对应的真实服务器通道或反向，
     * 便于数据转发和连接管理。
     */
    public static final AttributeKey<Channel> NEXT_CHANNEL = AttributeKey.newInstance("nxt_channel");

    /**
     * USER_ID：
     * 存储与Channel关联的用户唯一标识（User ID），
     * 用于标识不同用户的连接，便于管理和鉴权。
     */
    public static final AttributeKey<String> USER_ID = AttributeKey.newInstance("user_id");

    /**
     * CLIENT_KEY：
     * 存储客户端的唯一标识密钥，
     * 用于认证和区分不同客户端实例。
     */
    public static final AttributeKey<String> CLIENT_KEY = AttributeKey.newInstance("client_key");

    /**
     * AUTHENTICATED：
     * 存储用户认证状态，布尔值，
     * 标记该Channel是否已通过认证流程。
     */
    public static final AttributeKey<Boolean> AUTHENTICATED = AttributeKey.valueOf("authenticated");
    /**
     * 客户端连接的唯一标识符前缀
     */
    public static final String CLIENT_KEY_PREFIX = "ZC-";

}
