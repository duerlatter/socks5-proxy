package com.github.duerlatter.proxy.server.config.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;

/**
 * 代理客户端与其后面真实服务器映射关系模型类。
 * <p>
 * 用于描述代理客户端所代理的后端真实服务器信息，包括代理端口、本地网络信息和备注。
 * 例如：某代理客户端负责将请求转发到内网某个具体服务器及端口。
 * </p>
 *
 * @author fsren
 * @date 2025-06-26
 */
@Getter
@Setter
@ToString
public class ClientProxyMapping implements Serializable {
    @Serial
    private static final long serialVersionUID = -1748928837112587981L;

    /**
     * 代理服务器监听端口号，客户端通过此端口访问代理服务。
     */
    private Integer inetPort;

    /**
     * 需要代理的网络信息，格式为 IP:端口，例如 "192.168.1.99:80"。
     * 表示代理客户端能够访问的后端真实服务器地址，必须包含端口号。
     */
    private String lan;

    /**
     * 该映射的备注名称，可用于描述用途或备注信息。
     */
    private String name;
}
