package com.github.duerlatter.proxy.server;

import com.github.duerlatter.proxy.protocol.Constants;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 代理服务连接管理（代理客户端连接+用户请求连接）
 *
 * @author fsren
 * @date 2025-06-26
 */
@Slf4j
public class ProxyChannelManager {
    /**
     * 用户连接与代理客户端连接关系映射
     */
    private static final AttributeKey<Map<String, Channel>> USER_CHANNELS = AttributeKey.newInstance("user_channels");
    /**
     * 代理客户端连接标识
     */
    private static final AttributeKey<String> CHANNEL_CLIENT_KEY = AttributeKey.newInstance("channel_client_key");
    /**
     * 代理客户端连接与客户端标识的映射关系
     */
    private static final Map<String, Channel> cmdChannels = new ConcurrentHashMap<>();
    /**
     * channel 锁池
     */
    private static final Map<Channel, Object> channelLocks = new ConcurrentHashMap<>();


    /**
     * 增加代理服务器端口与代理控制客户端连接的映射关系
     *
     * @param channel 代理客户端连接
     */
    public static void addCmdChannel(String clientKey, Channel channel) {

        channel.attr(CHANNEL_CLIENT_KEY).set(clientKey);
        channel.attr(USER_CHANNELS).set(new ConcurrentHashMap<>());
        cmdChannels.put(clientKey, channel);
    }

    /**
     * 代理客户端连接断开后清除关系
     *
     * @param channel 代理客户端连接
     */
    public static void removeCmdChannel(Channel channel) {
        log.warn("channel closed, clear user channels, {}", channel);


        String clientKey = channel.attr(CHANNEL_CLIENT_KEY).get();
        if (clientKey != null) {
            Channel removed = cmdChannels.get(clientKey);
            if (removed != null && removed == channel) {
                cmdChannels.remove(clientKey);
            }
            if (channel.isActive()) {
                log.info("disconnect proxy channel {}", channel);
                channel.close();
            }
        }

        Map<String, Channel> userChannels = getUserChannels(channel);
        if (userChannels != null) {
            userChannels.values().forEach(userChannel -> {
                if (userChannel.isActive()) {
                    userChannel.close();
                    log.info("disconnect user channel {}", userChannel);
                }
            });
        }

    }

    /**
     * 获取代理客户端标识
     *
     * @param channel 代理客户端连接
     */
    public static String getCmdChannelClientKey(Channel channel) {
        return channel.attr(CHANNEL_CLIENT_KEY).get();
    }

    /**
     * 获取获取客户端连接
     *
     * @param clientKey 代理客户端标识
     * @return 代理客户端连接
     */
    public static Channel getCmdChannel(String clientKey) {
        return cmdChannels.get(clientKey);
    }

    /**
     * 增加用户连接与代理客户端连接关系
     *
     * @param cmdChannel  代理客户端连接
     * @param userId      用户编号
     * @param userChannel 用户连接
     */
    public static void addUserChannelToCmdChannel(Channel cmdChannel, String userId, Channel userChannel) {
        userChannel.attr(Constants.USER_ID).set(userId);
        cmdChannel.attr(USER_CHANNELS).get().putIfAbsent(userId, userChannel);
    }

    /**
     * 删除用户连接与代理客户端连接关系
     *
     * @param cmdChannel 代理客户端连接
     * @param userId     用户编号
     * @return 用户连接
     */
    public static Channel removeUserChannelFromCmdChannel(Channel cmdChannel, String userId) {
        if (cmdChannel.attr(USER_CHANNELS).get() == null) {
            return null;
        }
        Object lock = channelLocks.computeIfAbsent(cmdChannel, k -> new Object());
        synchronized (lock) {
            return cmdChannel.attr(USER_CHANNELS).get().remove(userId);
        }
    }

    /**
     * 根据代理客户端连接与用户编号获取用户连接
     *
     * @param cmdChannel 代理客户端连接
     * @param userId     用户编号
     * @return 用户连接
     */
    public static Channel getUserChannel(Channel cmdChannel, String userId) {
        return cmdChannel.attr(USER_CHANNELS).get().get(userId);
    }

    /**
     * 获取用户编号
     *
     * @param userChannel 用户连接
     * @return 用户编号
     */
    public static String getUserChannelUserId(Channel userChannel) {
        return userChannel.attr(Constants.USER_ID).get();
    }

    /**
     * 获取代理控制客户端连接绑定的所有用户连接
     *
     * @param cmdChannel 代理客户端连接
     * @return 用户连接映射
     */
    public static Map<String, Channel> getUserChannels(Channel cmdChannel) {
        return cmdChannel.attr(USER_CHANNELS).get();
    }

}
