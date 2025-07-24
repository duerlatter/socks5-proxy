package com.github.duerlatter.proxy.common.container;

/**
 * 容器接口，定义了容器的基本行为。
 *
 * @author fsren
 * @date 2025-06-25
 */
public interface Container {

    /**
     * 启动容器。
     */
    void start();

    /**
     * 停止容器。
     */
    void stop();
}
