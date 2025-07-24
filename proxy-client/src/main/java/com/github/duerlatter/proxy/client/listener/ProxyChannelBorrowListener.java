package com.github.duerlatter.proxy.client.listener;

import io.netty.channel.Channel;

/**
 * ProxyChannelBorrowListener 是代理通道借用的回调接口，
 * 用于通知通道借用操作的结果。
 * 当成功借用到代理通道时调用 success 方法，
 * 当借用失败时调用 error 方法。
 *
 * 该接口通常用于异步获取或复用代理通道的场景。
 *
 * @author fsren
 * @date 2025-07-04
 */
public interface ProxyChannelBorrowListener {

    /**
     * 通道借用成功时回调，返回可用的代理通道
     *
     * @param channel 成功借用的代理通道
     */
    void success(Channel channel);

    /**
     * 通道借用失败时回调，传递异常原因
     *
     * @param cause 失败的异常信息
     */
    void error(Throwable cause);

}
