package com.github.duerlatter.proxy.client.listener;

import io.netty.channel.ChannelHandlerContext;

/**
 * ChannelStatusListener 是一个接口，用于监听 Netty 通道的状态变化事件。
 * 目前定义了通道非活动（断开）时的回调方法，
 * 以便在通道关闭时执行自定义处理逻辑。
 *
 * @author fsren
 * @date 2025-07-04
 */
public interface ChannelStatusListener {

    /**
     * 当通道变为非活动状态时调用，例如连接断开时触发。
     * 实现类可以在此方法中执行资源清理、重连、通知等操作。
     *
     * @param ctx 当前通道的上下文对象
     */
    void channelInactive(ChannelHandlerContext ctx);
}
