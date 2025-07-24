package com.github.duerlatter.proxy.protocol;

/**
 * SOCKS5 协议常量定义类，用于解释握手、认证、连接请求与应答流程中的各类标志位。
 * 参考 RFC 1928 与 RFC 1929 标准。
 *
 * <p>该类定义了 SOCKS5 协议中所有重要的协议字段常量，供客户端与服务端通信时的编码和解析使用。</p>
 *
 * <ul>
 *   <li>版本号</li>
 *   <li>认证方法</li>
 *   <li>用户名密码认证结果</li>
 *   <li>命令类型</li>
 *   <li>地址类型</li>
 *   <li>连接应答结果码</li>
 * </ul>
 *
 * @author fsren
 * @date 2025-06-27
 */
public class Socks5Constants {

    /* ===== 版本号 ===== */

    /**
     * SOCKS5 协议版本号，固定值 0x05。
     */
    public static final byte VERSION_SOCKS5 = 0x05;

    /**
     * 用户名密码认证子协商版本号，固定值 0x01。
     * 参见 RFC 1929。
     */
    public static final byte AUTH_SUBNEGOTIATION_VERSION = 0x01;


    /* ===== 握手阶段认证方法（METHOD） ===== */

    /**
     * 无认证方式（No Authentication Required）。
     * 表示客户端无需提供认证信息即可连接。
     */
    public static final byte METHOD_NO_AUTH = 0x00;

    /**
     * GSSAPI 认证方法（通用安全服务 API）。
     * 未实现，一般不使用。
     */
    public static final byte METHOD_GSSAPI = 0x01;

    /**
     * 用户名密码认证方式（Username/Password Authentication）。
     * 客户端需提供用户名和密码进行认证。
     */
    public static final byte METHOD_USERNAME_PASSWORD = 0x02;

    /**
     * 无可接受的认证方法（No acceptable methods）。
     * 表示服务端不支持客户端提供的任何认证方式。
     */
    public static final byte METHOD_NO_ACCEPTABLE = (byte) 0xFF;


    /* ===== 用户名密码认证结果状态 ===== */

    /**
     * 用户名密码认证成功（SUCCESS）。
     */
    public static final byte AUTH_STATUS_SUCCESS = 0x00;

    /**
     * 用户名密码认证失败（FAILURE）。
     */
    public static final byte AUTH_STATUS_FAILURE = 0x01;


    /* ===== 命令类型（CMD） ===== */

    /**
     * 建立 TCP 连接请求（CONNECT）。
     * 这是 SOCKS5 中最常用的命令，用于建立代理连接。
     */
    public static final byte CMD_CONNECT = 0x01;

    /**
     * 绑定请求（BIND）。
     * 用于被动监听连接，未实现。
     */
    public static final byte CMD_BIND = 0x02;

    /**
     * UDP 关联请求（UDP ASSOCIATE）。
     * 用于 UDP 代理，未实现。
     */
    public static final byte CMD_UDP_ASSOCIATE = 0x03;


    /* ===== 地址类型（ATYP） ===== */

    /**
     * IPv4 地址类型，地址长度 4 字节。
     */
    public static final byte ATYP_IPV4 = 0x01;

    /**
     * 域名地址类型，地址长度为 1 字节长度 + 域名内容。
     */
    public static final byte ATYP_DOMAIN = 0x03;

    /**
     * IPv6 地址类型，地址长度 16 字节。
     */
    public static final byte ATYP_IPV6 = 0x04;


    /* ===== 连接应答结果码（REP） ===== */

    /**
     * 请求成功（Succeeded）。
     */
    public static final byte REP_SUCCEEDED = 0x00;

    /**
     * 通用 SOCKS 服务器失败。
     */
    public static final byte REP_GENERAL_FAILURE = 0x01;

    /**
     * 连接不允许（规则集禁止）。
     */
    public static final byte REP_CONNECTION_NOT_ALLOWED = 0x02;

    /**
     * 网络不可达。
     */
    public static final byte REP_NETWORK_UNREACHABLE = 0x03;

    /**
     * 主机不可达。
     */
    public static final byte REP_HOST_UNREACHABLE = 0x04;

    /**
     * 连接被拒绝。
     */
    public static final byte REP_CONNECTION_REFUSED = 0x05;

    /**
     * TTL（生存时间）过期。
     */
    public static final byte REP_TTL_EXPIRED = 0x06;

    /**
     * 不支持的命令。
     */
    public static final byte REP_COMMAND_NOT_SUPPORTED = 0x07;

    /**
     * 不支持的地址类型。
     */
    public static final byte REP_ADDRESS_TYPE_NOT_SUPPORTED = 0x08;

    // 0x09~0xFF 保留为未来使用
}
