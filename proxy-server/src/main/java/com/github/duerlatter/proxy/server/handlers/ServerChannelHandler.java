package com.github.duerlatter.proxy.server.handlers;

import com.github.duerlatter.proxy.protocol.Constants;
import com.github.duerlatter.proxy.protocol.ProxyMessage;
import com.github.duerlatter.proxy.server.ProxyChannelManager;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import lombok.extern.slf4j.Slf4j;

/**
 * 代理服务器端的 Channel 处理器。
 * 负责接收并处理来自代理客户端的 ProxyMessage 消息，
 * 包括心跳、认证、连接建立、断开连接及数据传输等。
 *
 * @author fsren
 * @date 2025-06-26
 */
@Slf4j
public class ServerChannelHandler extends SimpleChannelInboundHandler<ProxyMessage> {

    /**
     * 核心消息接收方法，自动解析 ProxyMessage 并分发处理。
     *
     * @param ctx          ChannelHandlerContext 上下文
     * @param proxyMessage 已解码的 ProxyMessage 对象
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ProxyMessage proxyMessage) {
        log.debug("ProxyMessage received {}", proxyMessage.getType());
        switch (proxyMessage.getType()) {
            case ProxyMessage.TYPE_HEARTBEAT:
                handleHeartbeatMessage(ctx, proxyMessage);
                break;
            case ProxyMessage.C_TYPE_AUTH:
                handleAuthMessage(ctx, proxyMessage);
                break;
            case ProxyMessage.TYPE_CONNECT:
                handleConnectMessage(ctx, proxyMessage);
                break;
            case ProxyMessage.TYPE_DISCONNECT:
                handleDisconnectMessage(ctx, proxyMessage);
                break;
            case ProxyMessage.P_TYPE_TRANSFER:
                handleTransferMessage(ctx, proxyMessage);
                break;
            default:
                // 未知消息类型，不做处理
                break;
        }
    }

    /**
     * 处理数据传输消息，将数据转发到用户端连接通道。
     *
     * @param ctx          当前代理客户端的Channel上下文
     * @param proxyMessage 包含传输数据的代理消息
     */
    private void handleTransferMessage(ChannelHandlerContext ctx, ProxyMessage proxyMessage) {
        // 获取用户端通道（即后端真实服务器连接）
        Channel userChannel = ctx.channel().attr(Constants.NEXT_CHANNEL).get();
        if (userChannel != null) {
            // 创建缓冲区写入传输数据
            ByteBuf buf = ctx.alloc().buffer(proxyMessage.getData().length);
            buf.writeBytes(proxyMessage.getData());
            // 写出并刷新至用户通道
            userChannel.writeAndFlush(buf);
        }
    }

    /**
     * 处理断开连接消息，关闭对应的用户通道连接并释放关联资源。
     *
     * @param ctx          当前通道上下文
     * @param proxyMessage 断开连接的代理消息
     */
    private void handleDisconnectMessage(ChannelHandlerContext ctx, ProxyMessage proxyMessage) {
        String clientKey = ctx.channel().attr(Constants.CLIENT_KEY).get();

        // 如果是控制连接还没完成绑定，直接通过控制通道关闭用户连接
        if (clientKey == null) {
            String userId = proxyMessage.getUri();
            Channel userChannel = ProxyChannelManager.removeUserChannelFromCmdChannel(ctx.channel(), userId);
            if (userChannel != null) {
                // 发送空数据并关闭连接，解决HTTP1.0传输问题
                userChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
            }
            return;
        }

        // 通过 clientKey 获取控制通道，关闭对应的用户连接
        Channel cmdChannel = ProxyChannelManager.getCmdChannel(clientKey);
        if (cmdChannel == null) {
            log.warn("ConnectMessage handleDisconnectMessage :error cmd channel key {}", clientKey);
            return;
        }

        // 移除并关闭对应用户连接
        Channel userChannel = ProxyChannelManager.removeUserChannelFromCmdChannel(cmdChannel, ctx.channel().attr(Constants.USER_ID).get());
        if (userChannel != null) {
            // 发送空数据并关闭用户连接，确保数据发送完成
            userChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
            // 清理当前通道属性，断开绑定关系
            ctx.channel().attr(Constants.NEXT_CHANNEL).set(null);
            ctx.channel().attr(Constants.CLIENT_KEY).set(null);
            ctx.channel().attr(Constants.USER_ID).set(null);
            ctx.channel().close();
        }
    }

    /**
     * 处理后端真实服务器建立连接消息，将该连接与用户连接绑定。
     *
     * @param ctx          当前代理通道上下文
     * @param proxyMessage 建立连接请求消息，URI格式为"userId@clientKey"
     */
    private void handleConnectMessage(ChannelHandlerContext ctx, ProxyMessage proxyMessage) {
        String uri = proxyMessage.getUri();
        if (uri == null) {
            ctx.channel().close();
            log.warn("ConnectMessage:null uri");
            return;
        }

        // 解析 userId 和 clientKey
        String[] tokens = uri.split("@");
        if (tokens.length != 2) {
            ctx.channel().close();
            log.warn("ConnectMessage handleConnectMessage:error uri");
            return;
        }

        // 通过 clientKey 获取对应的控制通道
        Channel cmdChannel = ProxyChannelManager.getCmdChannel(tokens[1]);
        if (cmdChannel == null) {
            log.warn("ConnectMessage handleConnectMessage:error cmd channel key {}", tokens[1]);
            ctx.channel().close();
            return;
        }

        // 获取用户连接通道
        Channel userChannel = ProxyChannelManager.getUserChannel(cmdChannel, tokens[0]);
        if (userChannel != null) {
            // 绑定 userId、clientKey 与两端通道的互相引用
            ctx.channel().attr(Constants.USER_ID).set(tokens[0]);
            ctx.channel().attr(Constants.CLIENT_KEY).set(tokens[1]);
            ctx.channel().attr(Constants.NEXT_CHANNEL).set(userChannel);
            userChannel.attr(Constants.NEXT_CHANNEL).set(ctx.channel());

            // 设置用户连接通道为可读状态，开始正常通信
            userChannel.config().setOption(ChannelOption.AUTO_READ, true);
        }
    }

    /**
     * 心跳消息处理，回复同类型心跳消息确认连接活跃。
     *
     * @param ctx          当前通道上下文
     * @param proxyMessage 接收到的心跳消息
     */
    private void handleHeartbeatMessage(ChannelHandlerContext ctx, ProxyMessage proxyMessage) {
        ProxyMessage heartbeatMessage = new ProxyMessage();
        // 回复时携带原消息流水号，方便双方对应
        heartbeatMessage.setSerialNumber(proxyMessage.getSerialNumber());
        heartbeatMessage.setType(ProxyMessage.TYPE_HEARTBEAT);
        log.debug("ConnectMessage handleHeartbeatMessage response heartbeat message {}", ctx.channel());
        ctx.channel().writeAndFlush(heartbeatMessage);
    }

    /**
     * 处理客户端认证消息，保存 clientKey 与控制通道映射关系。
     *
     * @param ctx          当前通道上下文
     * @param proxyMessage 包含 clientKey 的认证消息
     */
    private void handleAuthMessage(ChannelHandlerContext ctx, ProxyMessage proxyMessage) {
        String clientKey = proxyMessage.getUri();
        // 防御性检查，检查key格式  ZC-BCE92F671DD2
        if (clientKey == null || !clientKey.startsWith(Constants.CLIENT_KEY_PREFIX)) {
            log.warn("ConnectMessage handleAuthMessage error clientKey {}", clientKey);
            ctx.channel().close();
            return;
        }
        Channel channel = ProxyChannelManager.getCmdChannel(clientKey);
        if (channel != null) {
            log.warn("ConnectMessage handleAuthMessage exist channel for key {}, {}", clientKey, channel);
            // 已存在相同 clientKey 的通道，关闭当前连接
            ctx.channel().close();
            return;
        }

        // 绑定 clientKey 与当前通道，表示认证成功
        log.info("ConnectMessage handleAuthMessage set clientKey => channel, {}, {}", clientKey, ctx.channel());
        ProxyChannelManager.addCmdChannel(clientKey, ctx.channel());
    }

    /**
     * 监听通道的可写状态变化，动态调整关联用户通道的读取开关，
     * 以应对写缓冲区满时的背压机制。
     *
     * @param ctx 通道上下文
     * @throws Exception 异常抛出
     */
    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        Channel userChannel = ctx.channel().attr(Constants.NEXT_CHANNEL).get();
        if (userChannel != null) {
            // 当当前通道可写时，允许用户通道继续读取数据，否则暂停读取以防止内存溢出
            userChannel.config().setOption(ChannelOption.AUTO_READ, ctx.channel().isWritable());
        }

        super.channelWritabilityChanged(ctx);
    }

    /**
     * 处理通道断开事件，清理绑定关系，关闭关联通道，释放资源。
     *
     * @param ctx 通道上下文
     * @throws Exception 异常抛出
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Channel userChannel = ctx.channel().attr(Constants.NEXT_CHANNEL).get();
        if (userChannel != null && userChannel.isActive()) {
            String clientKey = ctx.channel().attr(Constants.CLIENT_KEY).get();
            String userId = ctx.channel().attr(Constants.USER_ID).get();

            // 获取控制通道，移除用户通道绑定关系
            Channel cmdChannel = ProxyChannelManager.getCmdChannel(clientKey);
            if (cmdChannel != null) {
                ProxyChannelManager.removeUserChannelFromCmdChannel(cmdChannel, userId);
            } else {
                log.warn("null cmdChannel, clientKey is {}", clientKey);
            }

            // 发送空数据触发关闭用户通道连接，解决 HTTP1.0 连接提前关闭问题
            userChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);

            // 关闭用户通道（防御性关闭）
            if (userChannel.isActive()) {
                userChannel.close();
            }
        } else {
            // 若当前通道无绑定用户连接，则移除控制通道映射关系
            ProxyChannelManager.removeCmdChannel(ctx.channel());
        }

        super.channelInactive(ctx);
    }

    /**
     * 异常捕获处理，打印错误日志并关闭异常通道。
     *
     * @param ctx   通道上下文
     * @param cause 异常原因
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("exception caught {}", cause.getMessage());
        ctx.close();
    }
}
