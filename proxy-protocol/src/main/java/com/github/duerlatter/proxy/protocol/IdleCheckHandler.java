package com.github.duerlatter.proxy.protocol;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;

/**
 * 空闲检查处理器，用于监测Netty通道(Channel)的读写空闲状态，
 * 以便及时发送心跳包或关闭空闲连接，防止资源浪费和连接超时。
 *
 * <p>继承自Netty的IdleStateHandler，利用其空闲事件机制，
 * 监控指定时间内通道的读空闲、写空闲及全部空闲事件。</p>
 *
 * <p>实现心跳包发送机制（写空闲时）和连接超时关闭机制（读空闲时）。</p>
 *
 * @author fsren
 * @date 2025-06-26
 */
@Slf4j
public class IdleCheckHandler extends IdleStateHandler {

    /**
     * 读空闲超时时长，单位为秒
     * 若超过此时间未收到数据，则触发读空闲事件
     */
    public static final int READ_IDLE_TIME = 60;

    /**
     * 写空闲超时时长，单位为秒
     * 若超过此时间未发送数据，则触发写空闲事件
     */
    public static final int WRITE_IDLE_TIME = 40;

    /**
     * 构造函数，初始化空闲检查的读、写、全部空闲时间
     *
     * @param readerIdleTimeSeconds 读空闲时间，单位秒
     * @param writerIdleTimeSeconds 写空闲时间，单位秒
     * @param allIdleTimeSeconds    全部空闲时间，单位秒
     */
    public IdleCheckHandler(int readerIdleTimeSeconds, int writerIdleTimeSeconds, int allIdleTimeSeconds) {
        super(readerIdleTimeSeconds, writerIdleTimeSeconds, allIdleTimeSeconds);
    }

    /**
     * 监听空闲事件，触发心跳包发送或关闭连接
     *
     * @param ctx 通道处理上下文
     * @param evt 空闲事件
     * @throws Exception 事件处理异常
     */
    @Override
    protected void channelIdle(ChannelHandlerContext ctx, IdleStateEvent evt) throws Exception {

        // 第一次写空闲事件，发送心跳包，维持连接活跃
        if (IdleStateEvent.FIRST_WRITER_IDLE_STATE_EVENT == evt) {
            log.debug("channel write timeout {}", ctx.channel());
            ProxyMessage proxyMessage = new ProxyMessage();
            proxyMessage.setType(ProxyMessage.TYPE_HEARTBEAT);
            ctx.channel().writeAndFlush(proxyMessage);

            // 第一次读空闲事件，说明长时间未收到数据，关闭连接释放资源
        } else if (IdleStateEvent.FIRST_READER_IDLE_STATE_EVENT == evt) {
            log.warn("channel read timeout {}", ctx.channel());
            ctx.channel().close();
        }

        super.channelIdle(ctx, evt);
    }
}
