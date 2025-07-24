package com.github.duerlatter.proxy.server.config;

import com.github.duerlatter.proxy.common.Config;
import com.github.duerlatter.proxy.server.config.model.ProxyClient;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 代理服务器配置类
 *
 * @author fsren
 * @date 2025-06-26
 */
@Slf4j
@Getter
@Setter
@ToString
public class ProxyConfig implements Serializable {
    @Serial
    private static final long serialVersionUID = 540589510146731614L;
    /**
     * 代理服务器绑定主机host
     */
    private String serverBind;

    /**
     * 代理服务器与代理客户端通信端口
     */
    private Integer serverPort;
    /**
     * socks 服务器绑定主机host
     */
    private String configSocksBind;
    /**
     * socks 服务器端口
     */
    private Integer configSocksPort;
    /**
     * socks 密码
     */
    private String configSocksPassword;

    /**
     * 代理客户端，支持多个客户端
     */
    private List<ProxyClient> clients;

    /**
     * 获取代理服务器配置实例
     */
    @Getter
    private static ProxyConfig instance = new ProxyConfig();


    private ProxyConfig() {

        // 代理服务器主机和端口配置初始化
        this.serverPort = Config.getInstance().getIntValue("server.port", 4900);
        this.serverBind = Config.getInstance().getStringValue("server.bind", "0.0.0.0");

        // socks服务器主机和端口配置初始化
        this.configSocksBind = Config.getInstance().getStringValue("config.socks.bind", "0.0.0.0");
        this.configSocksPort = Config.getInstance().getIntValue("config.socks.port", 1080);
        this.configSocksPassword = Config.getInstance().getStringValue("config.socks.password");


        log.info("config init serverBind {}, serverPort {}, configSocksBind {}, configSocksPort {}",
                serverBind, serverPort, configSocksBind, configSocksPort);
    }

}
