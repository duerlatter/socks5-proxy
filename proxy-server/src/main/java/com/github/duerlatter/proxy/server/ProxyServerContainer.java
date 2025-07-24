package com.github.duerlatter.proxy.server;

import com.github.duerlatter.proxy.common.container.Container;
import com.github.duerlatter.proxy.common.container.ContainerHelper;
import com.github.duerlatter.proxy.protocol.IdleCheckHandler;
import com.github.duerlatter.proxy.protocol.ProxyMessageDecoder;
import com.github.duerlatter.proxy.protocol.ProxyMessageEncoder;
import com.github.duerlatter.proxy.server.config.ProxyConfig;
import com.github.duerlatter.proxy.server.handlers.ServerChannelHandler;
import com.github.duerlatter.proxy.server.handlers.UserChannelHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.extern.slf4j.Slf4j;

import java.net.BindException;
import java.util.List;

/**
 * 代理服务器容器类，负责启动和管理代理服务端和用户端口。
 * 通过 Netty 实现 TCP 服务端功能，处理代理客户端与用户客户端的连接。
 * 主要职责：
 * 1. 启动代理服务器监听端口，接收代理客户端连接（负责代理数据转发）
 * 2. 启动 SOCKS5 用户端口，监听用户客户端连接（负责 Socks5 握手、认证、转发）
 * 3. 管理 Netty 的线程池和管道初始化
 * 实现了自定义的 Container 接口，支持统一启动与停止。
 *
 * @author fsren
 * @date 2025-06-26
 */
@Slf4j
public class ProxyServerContainer implements Container {
    /**
     * 最大帧长度，限制单个消息最大为 2MB，防止内存溢出或攻击。
     */
    private static final int MAX_FRAME_LENGTH = 2 * 1024 * 1024;

    /**
     * 长度字段偏移，表示消息中长度字段从头开始第0个字节。
     */
    private static final int LENGTH_FIELD_OFFSET = 0;

    /**
     * 长度字段长度，使用4字节表示消息长度。
     */
    private static final int LENGTH_FIELD_LENGTH = 4;

    /**
     * 解码时跳过的初始字节数，通常为0表示不跳过。
     */
    private static final int INITIAL_BYTES_TO_STRIP = 0;

    /**
     * 长度调整，长度计算时的偏移修正，0表示长度字段表示的长度即为消息体长度。
     */
    private static final int LENGTH_ADJUSTMENT = 0;

    /**
     * Netty Boss 线程组，负责接受客户端连接请求
     */
    private final EventLoopGroup serverBossGroup;

    /**
     * Netty Worker 线程组，负责处理已建立连接的数据读写
     */
    private final EventLoopGroup serverWorkerGroup;

    /**
     * 构造方法，初始化 Boss 和 Worker 线程组
     * 使用 Netty NIO 事件处理器工厂创建多线程事件循环组
     */
    public ProxyServerContainer() {

        int cpus = Runtime.getRuntime().availableProcessors();
        int bossWorkerCount = cpus <= 4 ? 1 : 2;
        int childWorkerCount = cpus * 2;

        serverBossGroup = new MultiThreadIoEventLoopGroup(bossWorkerCount,NioIoHandler.newFactory());
        serverWorkerGroup = new MultiThreadIoEventLoopGroup(childWorkerCount,NioIoHandler.newFactory());
    }

    /**
     * 启动代理服务器：
     * 1. 配置 Netty ServerBootstrap，设置线程组和服务端通道类型
     * 2. 初始化通道管道，添加解码器、编码器、心跳检测和业务处理器
     * 3. 绑定配置的代理服务器端口，开始监听代理客户端连接
     * 4. 启动 SOCKS5 用户端口服务，监听用户连接
     */
    @Override
    public void start() {
        ServerBootstrap bootstrap = new ServerBootstrap();

        bootstrap.group(serverBossGroup, serverWorkerGroup)
                .channel(NioServerSocketChannel.class) // 使用 NIO 服务器套接字通道
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) {
                        // 添加协议解码器，基于长度字段，最大长度2M
                        ch.pipeline().addLast(new ProxyMessageDecoder(
                                MAX_FRAME_LENGTH,
                                LENGTH_FIELD_OFFSET,
                                LENGTH_FIELD_LENGTH,
                                LENGTH_ADJUSTMENT,
                                INITIAL_BYTES_TO_STRIP));

                        // 添加协议编码器，负责将消息编码成字节流
                        ch.pipeline().addLast(new ProxyMessageEncoder());

                        // 添加空闲检测处理器，设置读写空闲超时时间，防止连接假死
                        ch.pipeline().addLast(new IdleCheckHandler(
                                IdleCheckHandler.READ_IDLE_TIME,
                                IdleCheckHandler.WRITE_IDLE_TIME,
                                0));

                        // 添加业务处理器，负责处理代理客户端发来的消息
                        ch.pipeline().addLast(new ServerChannelHandler());
                    }
                })
                .option(ChannelOption.SO_BACKLOG, 1024)
                .option(ChannelOption.SO_REUSEADDR, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_RCVBUF, 1024 * 1024)
                .childOption(ChannelOption.SO_SNDBUF, 1024 * 1024);

        try {
            // 绑定代理服务器监听的 IP 和端口
            bootstrap.bind(ProxyConfig.getInstance().getServerBind(), ProxyConfig.getInstance().getServerPort()).get();
            log.info("proxy server start on port {}", ProxyConfig.getInstance().getServerPort());
        } catch (Exception ex) {
            // 绑定失败抛出异常
            throw new RuntimeException(ex);
        }

        // 启动监听用户端口，处理用户连接
        startSocksPort();
    }

    /**
     * 启动 SOCKS5 用户端口，监听来自用户的连接请求
     * 只添加 UserChannelHandler 处理器，负责处理 SOCKS5 握手和转发
     */
    private void startSocksPort() {
        ServerBootstrap bootstrap = new ServerBootstrap();

        bootstrap.group(serverBossGroup, serverWorkerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {

                    @Override
                    public void initChannel(SocketChannel ch) {
                        // 用户连接只需要添加 UserChannelHandler 进行协议解析和数据转发
                        ch.pipeline().addLast(new UserChannelHandler());
                    }
                });

        int socksPort = ProxyConfig.getInstance().getConfigSocksPort();
        try {
            bootstrap.bind(socksPort).get();
            log.info("bind socks port {}", socksPort);
        } catch (Exception ex) {
            // 端口被占用等异常，非绑定异常则抛出
            if (!(ex.getCause() instanceof BindException)) {
                throw new RuntimeException(ex);
            }
        }
    }

    /**
     * 停止服务器，优雅关闭 Netty 线程池
     */
    @Override
    public void stop() {
        serverBossGroup.shutdownGracefully();
        serverWorkerGroup.shutdownGracefully();
    }

    /**
     * 程序入口，启动 ProxyServerContainer 容器
     * 支持传入多个 Container 统一启动管理
     */
    public static void main(String[] args) {
        ContainerHelper.start(List.of(new ProxyServerContainer()));
    }
}
