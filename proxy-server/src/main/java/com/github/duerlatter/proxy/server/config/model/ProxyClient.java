package com.github.duerlatter.proxy.server.config.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 代理客户端模型类。
 *
 * <p>用于描述代理客户端的基本信息和其对应的后端真实服务器映射关系。</p>
 *
 * <p>字段说明：
 * <ul>
 *   <li>name: 客户端备注名称，用于识别客户端的描述性名称。</li>
 *   <li>clientKey: 代理客户端唯一标识，用于标识和认证客户端。</li>
 *   <li>status: 在线状态，1表示在线，0表示离线。</li>
 *   <li>proxyMappings: 该客户端所代理的后端真实服务器列表映射关系。</li>
 * </ul>
 * </p>
 *
 * @author fsren
 * @date 2025-06-26
 */
@Getter
@Setter
@ToString
public class ProxyClient implements Serializable {
    @Serial
    private static final long serialVersionUID = 8123931514338969934L;

    /**
     * 客户端备注名称，便于区分和管理。
     */
    private String name;

    /**
     * 代理客户端的唯一标识 key，用于身份认证和识别。
     */
    private String clientKey;

    /**
     * 在线状态，1表示在线，0表示离线。
     */
    private int status;

    /**
     * 代理客户端对应的后端真实服务器映射列表。
     */
    private List<ClientProxyMapping> proxyMappings;
}
