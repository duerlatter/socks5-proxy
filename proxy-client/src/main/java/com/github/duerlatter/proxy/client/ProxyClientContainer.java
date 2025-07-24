package com.github.duerlatter.proxy.client;

import com.github.duerlatter.proxy.client.handlers.ClientChannelHandler;
import com.github.duerlatter.proxy.client.handlers.RealServerChannelHandler;
import com.github.duerlatter.proxy.client.listener.ChannelStatusListener;
import com.github.duerlatter.proxy.common.Config;
import com.github.duerlatter.proxy.common.container.Container;
import com.github.duerlatter.proxy.common.container.ContainerHelper;
import com.github.duerlatter.proxy.protocol.IdleCheckHandler;
import com.github.duerlatter.proxy.protocol.ProxyMessage;
import com.github.duerlatter.proxy.protocol.ProxyMessageDecoder;
import com.github.duerlatter.proxy.protocol.ProxyMessageEncoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

/**
 * ProxyClientContainer 是代理客户端的核心容器类，实现了Container接口，
 * 管理客户端的连接生命周期、网络事件处理及重连逻辑。
 *
 * 该类主要负责：
 * - 创建并配置 Netty Bootstrap 实例（代理服务器连接和真实服务器连接）
 * - 初始化通道 Pipeline，添加协议编解码器、心跳检测和业务处理器
 * - 管理代理服务器连接的建立和断线重连
 * - 作为 ChannelStatusListener 监听通道状态变化，处理断线事件
 *
 * @author fsren
 * @date 2025-07-04
 */
@Slf4j
public class ProxyClientContainer implements Container, ChannelStatusListener {

    /**
     * 最大消息帧长度，防止内存溢出
     */
    private static final int MAX_FRAME_LENGTH = 1024 * 1024;

    /**
     * 长度字段偏移量
     */
    private static final int LENGTH_FIELD_OFFSET = 0;

    /**
     * 长度字段长度
     */
    private static final int LENGTH_FIELD_LENGTH = 4;

    /**
     * 读取消息时跳过的初始字节数
     */
    private static final int INITIAL_BYTES_TO_STRIP = 0;

    /**
     * 长度字段调整值
     */
    private static final int LENGTH_ADJUSTMENT = 0;

    /**
     * 事件循环组，负责处理 IO 事件
     */
    private final EventLoopGroup workerGroup;

    /**
     * 用于连接代理服务器的 Bootstrap 实例
     */
    private final Bootstrap bootstrap;

    /**
     * 用于连接真实服务器的 Bootstrap 实例
     */
    private final Bootstrap realServerBootstrap;

    /**
     * 配置对象，读取客户端配置项
     */
    private final Config config = Config.getInstance();

    /**
     * 重连等待时间，指数退避策略起始值，单位毫秒
     */
    private long sleepTimeMill = 1000;

    /**
     * 构造函数，初始化Netty相关组件和通道管道配置
     */
    public ProxyClientContainer() {
        // 创建多线程事件循环组，使用自定义的 NioIoHandler
        workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());

        // 初始化真实服务器连接 Bootstrap
        realServerBootstrap = new Bootstrap();
        realServerBootstrap.group(workerGroup);
        realServerBootstrap.channel(NioSocketChannel.class);
        realServerBootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel ch) {
                // 真实服务器通道添加数据处理器
                ch.pipeline().addLast(new RealServerChannelHandler());
            }
        });

        // 初始化代理服务器连接 Bootstrap
        bootstrap = new Bootstrap();
        bootstrap.group(workerGroup);
        bootstrap.channel(NioSocketChannel.class);
        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel ch) {
                // 代理服务器通道流水线配置
                ch.pipeline().addLast(
                        new ProxyMessageDecoder(MAX_FRAME_LENGTH, LENGTH_FIELD_OFFSET, LENGTH_FIELD_LENGTH, LENGTH_ADJUSTMENT, INITIAL_BYTES_TO_STRIP)
                );
                ch.pipeline().addLast(new ProxyMessageEncoder());
                // 添加心跳检测，防止连接超时断开
                ch.pipeline().addLast(new IdleCheckHandler(IdleCheckHandler.READ_IDLE_TIME, IdleCheckHandler.WRITE_IDLE_TIME - 10, 0));
                // 添加客户端业务处理器，传入真实服务器Bootstrap及自身作为状态监听
                ch.pipeline().addLast(new ClientChannelHandler(realServerBootstrap, bootstrap, ProxyClientContainer.this));
            }
        });
    }

    /**
     * 启动客户端，尝试连接代理服务器
     */
    @Override
    public void start() {
        connectProxyServer();
    }

    /**
     * 连接代理服务器并添加连接成功或失败的监听逻辑
     */
    private void connectProxyServer() {
        bootstrap.connect(config.getStringValue("server.host"), config.getIntValue("server.port"))
                .addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        String clientKey = ClientChannelManager.getClientKey();
                        // 设置控制命令通道
                        ClientChannelManager.setCmdChannel(future.channel());
                        // 发送认证消息（客户端唯一标识）
                        ProxyMessage proxyMessage = new ProxyMessage();
                        proxyMessage.setType(ProxyMessage.C_TYPE_AUTH);
                        proxyMessage.setUri(clientKey);
                        future.channel().writeAndFlush(proxyMessage);

                        // 重置重连等待时间
                        sleepTimeMill = 1000;
                        log.info("connect proxy server success, {}, clientKey : {}", future.channel(), clientKey);
                    } else {
                        log.warn("connect proxy server failed", future.cause());
                        // 连接失败，执行重连等待，然后重新连接
                        reconnectWait();
                        connectProxyServer();
                    }
                });
    }

    /**
     * 停止客户端，优雅关闭事件循环组释放资源
     */
    @Override
    public void stop() {
        workerGroup.shutdownGracefully();
    }

    /**
     * 通道失活事件处理，触发重连
     *
     * @param ctx ChannelHandlerContext
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        reconnectWait();
        connectProxyServer();
    }

    /**
     * 重连等待方法，使用指数退避策略等待一定时间
     * 最大等待时间 60 秒，最小 1 秒
     */
    private void reconnectWait() {
        try {
            if (sleepTimeMill > 60000) {
                sleepTimeMill = 1000;
            }

            synchronized (this) {
                sleepTimeMill = sleepTimeMill * 2;
                wait(sleepTimeMill);
            }
        } catch (InterruptedException ignored) {
            // 忽略中断异常
        }
    }

    /**
     * 程序入口，启动 ProxyClientContainer 容器
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        ContainerHelper.start(Arrays.asList(new Container[]{new ProxyClientContainer()}));
    }
}
