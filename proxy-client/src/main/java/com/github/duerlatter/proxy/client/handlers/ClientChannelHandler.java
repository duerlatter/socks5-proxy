package com.github.duerlatter.proxy.client.handlers;

import com.github.duerlatter.proxy.client.ClientChannelManager;
import com.github.duerlatter.proxy.client.listener.ChannelStatusListener;
import com.github.duerlatter.proxy.client.listener.ProxyChannelBorrowListener;
import com.github.duerlatter.proxy.protocol.Constants;
import com.github.duerlatter.proxy.protocol.ProxyMessage;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import lombok.extern.slf4j.Slf4j;

/**
 * ClientChannelHandler 负责处理客户端与代理服务端之间的通信逻辑，
 * 包括连接建立、数据转发和连接关闭等事件的处理。
 * 继承自 Netty 的 SimpleChannelInboundHandler，专门处理 ProxyMessage 类型消息。
 *
 * @author fsren
 * @date 2025-07-04
 */
@Slf4j
public class ClientChannelHandler extends SimpleChannelInboundHandler<ProxyMessage> {

    /**
     * 用于连接真实目标服务器的 Bootstrap 实例
     */
    private final Bootstrap bootstrap;

    /**
     * 用于连接代理服务器的 Bootstrap 实例
     */
    private final Bootstrap proxyBootstrap;

    /**
     * 连接状态监听器，用于监听通道的激活和非激活事件
     */
    private final ChannelStatusListener channelStatusListener;

    /**
     * 构造方法，注入两个 Bootstrap 实例和连接状态监听器
     * @param bootstrap 连接真实服务器的 Bootstrap
     * @param proxyBootstrap 连接代理服务器的 Bootstrap
     * @param channelStatusListener 通道状态监听器
     */
    public ClientChannelHandler(Bootstrap bootstrap, Bootstrap proxyBootstrap, ChannelStatusListener channelStatusListener) {
        this.bootstrap = bootstrap;
        this.proxyBootstrap = proxyBootstrap;
        this.channelStatusListener = channelStatusListener;
    }

    /**
     * 处理接收到的 ProxyMessage 消息，根据消息类型调用对应处理方法
     * @param ctx 通道上下文
     * @param proxyMessage 接收到的代理消息
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ProxyMessage proxyMessage) {
        log.debug("received proxy message, type is {}", proxyMessage.getType());
        switch (proxyMessage.getType()) {
            case ProxyMessage.TYPE_CONNECT:
                // 处理连接请求消息
                handleConnectMessage(ctx, proxyMessage);
                break;
            case ProxyMessage.TYPE_DISCONNECT:
                // 处理断开连接消息
                handleDisconnectMessage(ctx, proxyMessage);
                break;
            case ProxyMessage.P_TYPE_TRANSFER:
                // 处理数据转发消息
                handleTransferMessage(ctx, proxyMessage);
                break;
            default:
                // 忽略未知类型消息
                break;
        }
    }

    /**
     * 处理数据转发消息，将收到的数据写入真实服务器通道
     * @param ctx 通道上下文
     * @param proxyMessage 代理消息，携带需要转发的数据
     */
    private void handleTransferMessage(ChannelHandlerContext ctx, ProxyMessage proxyMessage) {
        // 通过当前通道属性获取真实服务器通道
        Channel realServerChannel = ctx.channel().attr(Constants.NEXT_CHANNEL).get();
        if (realServerChannel != null) {
            // 分配 ByteBuf 并写入接收到的数据
            ByteBuf buf = ctx.alloc().buffer(proxyMessage.getData().length);
            buf.writeBytes(proxyMessage.getData());
            log.debug("write data to real server, {}", realServerChannel);
            // 写入并刷新数据到真实服务器通道
            realServerChannel.writeAndFlush(buf);
        }
    }

    /**
     * 处理断开连接消息，关闭对应的真实服务器连接和释放代理通道
     * @param ctx 通道上下文
     * @param proxyMessage 断开连接消息
     */
    private void handleDisconnectMessage(ChannelHandlerContext ctx, ProxyMessage proxyMessage) {
        // 取出绑定的真实服务器通道
        Channel realServerChannel = ctx.channel().attr(Constants.NEXT_CHANNEL).get();
        log.debug("handleDisconnectMessage, {}", realServerChannel);
        if (realServerChannel != null) {
            // 解除绑定
            ctx.channel().attr(Constants.NEXT_CHANNEL).set(null);
            // 将代理通道归还给管理器
            ClientChannelManager.returnProxyChanel(ctx.channel());
            // 向真实服务器通道写空数据并关闭连接
            realServerChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }

    /**
     * 处理连接请求消息，建立真实服务器连接及代理通道绑定
     * @param ctx 通道上下文
     * @param proxyMessage 连接请求消息，携带 URI 信息（userId:ip:port）
     */
    private void handleConnectMessage(ChannelHandlerContext ctx, ProxyMessage proxyMessage) {
        String uri = proxyMessage.getUri();
        String[] info = uri.split(":");
        // URI 必须包含三部分信息：userId、IP 和端口
        if (info.length != 3) {
            log.error("Invalid connect message URI: {}", uri);
            return;
        }
        final Channel cmdChannel = ctx.channel();
        final String userId = info[0];
        String ip = info[1];
        int port = Integer.parseInt(info[2]);
        String clientKey = ClientChannelManager.getClientKey();

        // 异步连接目标真实服务器
        bootstrap.connect(ip, port).addListener((ChannelFutureListener) future -> {

            if (future.isSuccess()) {
                // 连接成功
                final Channel realServerChannel = future.channel();
                log.debug("connect real server success, {}", realServerChannel);

                // 先关闭自动读取，等待绑定完成后再启用
                realServerChannel.config().setOption(ChannelOption.AUTO_READ, false);

                // 从代理通道池中借用一个通道
                ClientChannelManager.borrowProxyChanel(proxyBootstrap, new ProxyChannelBorrowListener() {

                    @Override
                    public void success(Channel channel) {
                        // 建立真实服务器通道和代理通道的双向绑定
                        channel.attr(Constants.NEXT_CHANNEL).set(realServerChannel);
                        realServerChannel.attr(Constants.NEXT_CHANNEL).set(channel);

                        // 发送连接请求消息到代理服务器，标识 userId 和 clientKey
                        ProxyMessage proxyMessage1 = new ProxyMessage();
                        proxyMessage1.setType(ProxyMessage.TYPE_CONNECT);
                        proxyMessage1.setUri(userId + "@" + clientKey);
                        channel.writeAndFlush(proxyMessage1);

                        // 启用真实服务器通道自动读取
                        realServerChannel.config().setOption(ChannelOption.AUTO_READ, true);

                        // 注册真实服务器通道及其用户 ID
                        ClientChannelManager.addRealServerChannel(userId, realServerChannel);
                        ClientChannelManager.setRealServerChannelUserId(realServerChannel, userId);
                    }

                    @Override
                    public void error(Throwable cause) {
                        // 代理通道借用失败，通知控制通道断开连接
                        ProxyMessage proxyMessage1 = new ProxyMessage();
                        proxyMessage1.setType(ProxyMessage.TYPE_DISCONNECT);
                        proxyMessage1.setUri(userId);
                        cmdChannel.writeAndFlush(proxyMessage1);
                    }
                });

            } else {
                // 连接失败，通知控制通道断开连接
                ProxyMessage proxyMessage1 = new ProxyMessage();
                proxyMessage1.setType(ProxyMessage.TYPE_DISCONNECT);
                proxyMessage1.setUri(userId);
                cmdChannel.writeAndFlush(proxyMessage1);
            }
        });
    }

    /**
     * 当通道的写可用状态改变时触发，动态调整真实服务器通道的自动读取状态，防止写缓冲区拥堵
     * @param ctx 通道上下文
     * @throws Exception 异常抛出
     */
    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        Channel realServerChannel = ctx.channel().attr(Constants.NEXT_CHANNEL).get();
        if (realServerChannel != null) {
            // 根据当前通道是否可写，设置真实服务器通道是否自动读取数据
            realServerChannel.config().setOption(ChannelOption.AUTO_READ, ctx.channel().isWritable());
        }

        super.channelWritabilityChanged(ctx);
    }

    /**
     * 通道非活动时触发，释放资源并关闭对应的关联通道
     * @param ctx 通道上下文
     * @throws Exception 异常抛出
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {

        // 判断是否为控制通道
        if (ClientChannelManager.getCmdChannel() == ctx.channel()) {
            // 清理控制通道及所有真实服务器通道
            ClientChannelManager.setCmdChannel(null);
            ClientChannelManager.clearRealServerChannels();
            // 通知外部监听器通道已关闭
            channelStatusListener.channelInactive(ctx);
        } else {
            // 数据传输通道断开，关闭绑定的真实服务器通道
            Channel realServerChannel = ctx.channel().attr(Constants.NEXT_CHANNEL).get();
            if (realServerChannel != null && realServerChannel.isActive()) {
                realServerChannel.close();
            }
        }

        // 从代理通道管理器移除当前通道
        ClientChannelManager.removeProxyChanel(ctx.channel());
        super.channelInactive(ctx);
    }

    /**
     * 捕获异常，打印日志
     * @param ctx 通道上下文
     * @param cause 异常对象
     * @throws Exception 异常抛出
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("exception caught", cause);
        super.exceptionCaught(ctx, cause);
    }

}
