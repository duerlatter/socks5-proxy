package com.github.duerlatter.proxy.client;

import com.github.duerlatter.proxy.client.listener.ProxyChannelBorrowListener;
import com.github.duerlatter.proxy.common.Config;
import com.github.duerlatter.proxy.protocol.Constants;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.util.AttributeKey;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * ClientChannelManager 是代理客户端中管理各种 Channel（通道）的核心管理类。
 * 负责管理真实服务器连接通道、代理通道池，以及控制命令通道的状态和生命周期。
 * 提供代理通道借用、归还、通道绑定和用户标识管理等功能。
 *
 * 通过线程安全的集合和属性保证高并发下通道管理的安全与高效。
 *
 * @author fsren
 * @date 2025-07-04
 */
@Slf4j
public class ClientChannelManager {

    /**
     * 用户通道可写属性键，用于标识用户通道的写状态
     */
    private static final AttributeKey<Boolean> USER_CHANNEL_WRITEABLE = AttributeKey.newInstance("user_channel_writeable");

    /**
     * 客户端通道可写属性键，用于标识客户端通道的写状态
     */
    private static final AttributeKey<Boolean> CLIENT_CHANNEL_WRITEABLE = AttributeKey.newInstance("client_channel_writeable");

    /**
     * 代理通道池最大容量限制，防止资源无限增长
     */
    private static final int MAX_POOL_SIZE = 100;

    /**
     * 维护用户ID到真实服务器通道的映射，便于通过用户ID快速获取通道
     */
    private static final Map<String, Channel> realServerChannels = new ConcurrentHashMap<>();

    /**
     * 代理通道连接池，用于复用连接减少重复连接开销
     */
    private static final ConcurrentLinkedQueue<Channel> proxyChannelPool = new ConcurrentLinkedQueue<>();

    /**
     * 代表控制命令通道，通常为与代理服务器通信的主通道
     */
    @Getter
    private static volatile Channel cmdChannel;

    /**
     * 配置实例，读取客户端配置
     */
    private static final Config config = Config.getInstance();

    /**
     * 唯一客户端标识 UUID，用于身份认证和消息绑定
     */
    private static final String UUID = java.util.UUID.randomUUID().toString();

    /**
     * 借用一个代理通道，如果连接池中有可用通道则直接返回，
     * 否则发起新的连接请求异步建立连接。
     *
     * @param bootstrap 用于连接代理服务器的 Bootstrap 对象
     * @param borrowListener 借用结果回调监听器
     */
    public static void borrowProxyChanel(Bootstrap bootstrap, final ProxyChannelBorrowListener borrowListener) {
        Channel channel = proxyChannelPool.poll();
        if (channel != null) {
            // 池中有空闲通道，直接返回
            borrowListener.success(channel);
            return;
        }

        // 池中无空闲通道，异步连接代理服务器
        bootstrap.connect(config.getStringValue("server.host"), config.getIntValue("server.port"))
                .addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        borrowListener.success(future.channel());
                    } else {
                        log.warn("connect proxy server failed", future.cause());
                        borrowListener.error(future.cause());
                    }
                });
    }

    /**
     * 归还代理通道，若连接池未满则放回池中复用，
     * 否则关闭该通道释放资源。
     *
     * @param proxyChanel 待归还的代理通道
     */
    public static void returnProxyChanel(Channel proxyChanel) {
        if (proxyChannelPool.size() > MAX_POOL_SIZE) {
            // 池已满，关闭连接
            proxyChanel.close();
        } else {
            // 重置通道配置并放回池中
            proxyChanel.config().setOption(ChannelOption.AUTO_READ, true);
            proxyChanel.attr(Constants.NEXT_CHANNEL).remove();
            proxyChannelPool.offer(proxyChanel);
            log.debug("return ProxyChanel to the pool, channel is {}, pool size is {} ", proxyChanel, proxyChannelPool.size());
        }
    }

    /**
     * 从连接池中移除指定代理通道，通常用于连接关闭或异常时清理
     *
     * @param proxyChanel 待移除的代理通道
     */
    public static void removeProxyChanel(Channel proxyChanel) {
        proxyChannelPool.remove(proxyChanel);
    }

    /**
     * 设置控制命令通道
     *
     * @param cmdChannel 控制命令通道实例
     */
    public static void setCmdChannel(Channel cmdChannel) {
        ClientChannelManager.cmdChannel = cmdChannel;
    }

    /**
     * 绑定用户ID到真实服务器通道的属性中
     *
     * @param realServerChannel 真实服务器通道
     * @param userId 用户标识
     */
    public static void setRealServerChannelUserId(Channel realServerChannel, String userId) {
        realServerChannel.attr(Constants.USER_ID).set(userId);
    }

    /**
     * 获取真实服务器通道绑定的用户ID
     *
     * @param realServerChannel 真实服务器通道
     * @return 绑定的用户ID
     */
    public static String getRealServerChannelUserId(Channel realServerChannel) {
        return realServerChannel.attr(Constants.USER_ID).get();
    }

    /**
     * 根据用户ID获取对应的真实服务器通道
     *
     * @param userId 用户标识
     * @return 真实服务器通道，若不存在则返回 null
     */
    public static Channel getRealServerChannel(String userId) {
        return realServerChannels.get(userId);
    }

    /**
     * 添加用户ID与真实服务器通道的映射
     *
     * @param userId 用户标识
     * @param realServerChannel 真实服务器通道
     */
    public static void addRealServerChannel(String userId, Channel realServerChannel) {
        realServerChannels.put(userId, realServerChannel);
    }

    /**
     * 移除指定用户ID对应的真实服务器通道映射
     *
     * @param userId 用户标识
     * @return 被移除的真实服务器通道，若不存在则返回 null
     */
    public static Channel removeRealServerChannel(String userId) {
        return realServerChannels.remove(userId);
    }

    /**
     * 判断真实服务器通道是否可读，
     * 依据客户端通道和用户通道的写状态属性决定，
     * 用于流控管理。
     *
     * @param realServerChannel 真实服务器通道
     * @return true 表示可读，false 表示不可读
     */
    public static boolean isRealServerReadable(Channel realServerChannel) {
        Boolean clientWritable = realServerChannel.attr(CLIENT_CHANNEL_WRITEABLE).get();
        Boolean userWritable = realServerChannel.attr(USER_CHANNEL_WRITEABLE).get();
        return Boolean.TRUE.equals(clientWritable) && Boolean.TRUE.equals(userWritable);
    }

    /**
     * 清理所有真实服务器通道，通常在控制通道关闭时调用，
     * 会关闭所有真实服务器通道以释放资源。
     */
    public static void clearRealServerChannels() {
        log.warn("channel closed, clear real server channels");

        for (Map.Entry<String, Channel> stringChannelEntry : realServerChannels.entrySet()) {
            Channel realServerChannel = stringChannelEntry.getValue();
            if (realServerChannel.isActive()) {
                // 发送空数据包并关闭通道
                realServerChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
            }
        }

        realServerChannels.clear();
    }

    /**
     * 获取客户端唯一标识，用于身份识别和鉴权
     *
     * @return 客户端唯一UUID字符串
     */
    public static String getClientKey() {
        return UUID;
    }
}
