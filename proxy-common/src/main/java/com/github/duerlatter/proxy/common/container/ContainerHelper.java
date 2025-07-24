package com.github.duerlatter.proxy.common.container;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 容器帮助类，提供容器相关的工具方法和操作。
 *
 * @author fsren
 * @date 2025-06-25
 */
@Slf4j
public class ContainerHelper {
    /**
     * 容器运行状态标志，指示容器是否正在运行。
     */
    private static volatile boolean running = true;
    /**
     * 缓存的容器列表，存储当前所有已启动的容器实例。
     */
    private static List<Container> cachedContainers;

    /**
     * 启动容器。
     *
     * @param containers 容器列表
     */
    public static void start(List<Container> containers) {

        cachedContainers = containers;

        // 启动所有容器
        startContainers();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            synchronized (ContainerHelper.class) {
                // 停止所有容器.
                stopContainers();
                running = false;
                ContainerHelper.class.notify();
            }
        }));

        synchronized (ContainerHelper.class) {
            while (running) {
                try {
                    ContainerHelper.class.wait();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /**
     * 启动所有容器实例。
     */
    private static void startContainers() {
        for (Container container : cachedContainers) {
            log.info("starting container [{}]", container.getClass().getName());
            container.start();
            log.info("container [{}] started", container.getClass().getName());
        }
    }

    /**
     * 停止所有容器实例。
     */
    private static void stopContainers() {
        for (Container container : cachedContainers) {
            log.info("stopping container [{}]", container.getClass().getName());
            try {
                container.stop();
                log.info("container [{}] stopped", container.getClass().getName());
            } catch (Exception ex) {
                log.warn("container stopped with error", ex);
            }
        }
    }
}
