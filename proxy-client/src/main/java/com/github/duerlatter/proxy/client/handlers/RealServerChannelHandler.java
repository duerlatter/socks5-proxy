package com.github.duerlatter.proxy.client.handlers;

import com.github.duerlatter.proxy.client.ClientChannelManager;
import com.github.duerlatter.proxy.protocol.Constants;
import com.github.duerlatter.proxy.protocol.ProxyMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * RealServerChannelHandler 负责处理与真实服务器之间的数据通信。
 * 继承自 SimpleChannelInboundHandler，处理从真实服务器接收的字节流(ByteBuf)，
 * 并将数据封装为 ProxyMessage 发送给代理服务器通道。
 * 同时处理通道活跃状态变化及异常等事件。
 *
 * @author fsren
 * @date 2025-07-04
 */
@Slf4j
public class RealServerChannelHandler extends SimpleChannelInboundHandler<ByteBuf> {

    /**
     * 从真实服务器通道读取到数据时触发，
     * 将读取到的数据封装成 ProxyMessage 后写入绑定的代理通道。
     * 如果代理通道不存在，则关闭当前通道。
     *
     * @param ctx 通道上下文
     * @param buf 读取到的字节缓冲区
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf buf) {
        Channel realServerChannel = ctx.channel();
        // 获取与真实服务器通道绑定的代理通道
        Channel channel = realServerChannel.attr(Constants.NEXT_CHANNEL).get();

        if (channel == null) {
            // 代理客户端连接已断开，关闭当前通道释放资源
            ctx.channel().close();
        } else {
            // 读取字节数据
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);

            // 获取该真实服务器通道对应的用户ID
            String userId = ClientChannelManager.getRealServerChannelUserId(realServerChannel);

            // 创建代理消息进行封装转发
            ProxyMessage proxyMessage = new ProxyMessage();
            proxyMessage.setType(ProxyMessage.P_TYPE_TRANSFER);
            proxyMessage.setUri(userId);
            proxyMessage.setData(bytes);

            // 将封装后的代理消息写入代理通道并刷新
            channel.writeAndFlush(proxyMessage);

            log.debug("write data to proxy server, {}, {}", realServerChannel, channel);
        }
    }

    /**
     * 通道激活时触发，可以在此方法中添加激活时的逻辑（当前为空实现）
     *
     * @param ctx 通道上下文
     * @throws Exception 异常抛出
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
    }

    /**
     * 通道非活跃时触发，处理清理绑定关系和通知代理服务器断开连接。
     *
     * @param ctx 通道上下文
     * @throws Exception 异常抛出
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Channel realServerChannel = ctx.channel();
        // 获取用户ID，清理该真实服务器通道在管理器中的映射
        String userId = ClientChannelManager.getRealServerChannelUserId(realServerChannel);
        ClientChannelManager.removeRealServerChannel(userId);

        // 获取绑定的代理通道
        Channel channel = realServerChannel.attr(Constants.NEXT_CHANNEL).get();
        if (channel != null) {
            log.debug("channelInactive, {}", realServerChannel);

            // 向代理通道发送断开连接消息
            ProxyMessage proxyMessage = new ProxyMessage();
            proxyMessage.setType(ProxyMessage.TYPE_DISCONNECT);
            proxyMessage.setUri(userId);
            channel.writeAndFlush(proxyMessage);
        }

        super.channelInactive(ctx);
    }

    /**
     * 当通道的写可用状态发生变化时触发，
     * 根据真实服务器通道是否可写，调整代理通道的自动读取配置，
     * 避免写缓冲区过载。
     *
     * @param ctx 通道上下文
     * @throws Exception 异常抛出
     */
    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        Channel realServerChannel = ctx.channel();
        Channel proxyChannel = realServerChannel.attr(Constants.NEXT_CHANNEL).get();

        if (proxyChannel != null) {
            // 根据真实服务器通道的写可用状态，设置代理通道是否自动读取
            proxyChannel.config().setOption(ChannelOption.AUTO_READ, realServerChannel.isWritable());
        }

        super.channelWritabilityChanged(ctx);
    }

    /**
     * 捕获并处理通道异常，打印错误日志
     *
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
