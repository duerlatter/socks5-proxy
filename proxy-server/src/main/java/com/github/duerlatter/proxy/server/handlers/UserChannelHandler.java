package com.github.duerlatter.proxy.server.handlers;

import com.github.duerlatter.proxy.common.UUIDShortGenerator;
import com.github.duerlatter.proxy.protocol.Constants;
import com.github.duerlatter.proxy.protocol.ProxyMessage;
import com.github.duerlatter.proxy.protocol.Socks5Constants;
import com.github.duerlatter.proxy.server.ProxyChannelManager;
import com.github.duerlatter.proxy.server.config.ProxyConfig;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

import static com.github.duerlatter.proxy.protocol.Constants.CLIENT_KEY;

/**
 * 用户通道处理器，负责处理 SOCKS5 协议的握手、认证、连接请求及数据转发。
 * 实现了 SOCKS5 的内部状态机，分三阶段处理客户端请求：
 * - HANDSHAKE: 协商认证方式（只支持用户名密码认证）
 * - AUTH: 验证用户名密码，认证成功后准备代理连接
 * - READY: 认证完成后，处理 CONNECT 请求并转发数据
 * 认证成功后，建立代理连接映射，转发数据至后端代理通道。
 * 支持用户名密码认证，连接请求解析目标地址，通知代理服务端建立后端连接。
 * @author fsren
 * @date 2025-06-26
 */
@Slf4j
public class UserChannelHandler extends SimpleChannelInboundHandler<ByteBuf> {

    /** Socks5连接状态属性键，存储当前通道的处理阶段 */
    public static final AttributeKey<SocksState> SOCKS_STATE = AttributeKey.valueOf("socks_state");

    /** 认证完成标志属性键 */
    public static final AttributeKey<Boolean> SOCKS_CONNECTED = AttributeKey.valueOf("socks_connected");


    /** 内部状态机表示 SOCKS5 协议处理阶段 */
    public enum SocksState {
        HANDSHAKE,  // 握手阶段：协商认证方式
        AUTH,       // 用户名密码认证阶段
        READY       // 认证成功，准备代理连接及数据转发
    }

    /**
     * 连接激活时，初始化当前通道的状态为 HANDSHAKE，并标记未认证。
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        Channel userChannel = ctx.channel();
        userChannel.attr(Constants.AUTHENTICATED).set(false);
        userChannel.attr(SOCKS_STATE).set(SocksState.HANDSHAKE);

        userChannel.config().setOption(ChannelOption.TCP_NODELAY, true);
        userChannel.config().setOption(ChannelOption.SO_RCVBUF, 1024 * 1024);
        userChannel.config().setOption(ChannelOption.SO_SNDBUF, 1024 * 1024);
        super.channelActive(ctx);
    }

    /**
     * 根据当前状态分发处理逻辑：
     * - HANDSHAKE：处理认证方式协商
     * - AUTH：用户名密码认证
     * - READY：首次处理 CONNECT 命令，之后转发数据
     *
     * @param ctx 当前通道上下文
     * @param msg 收到的字节数据
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
        Channel channel = ctx.channel();
        SocksState state = channel.attr(SOCKS_STATE).get();
        log.info("Received data in state: {}", state);
        switch (state) {
            case HANDSHAKE -> handleHandshake(ctx, msg);
            case AUTH -> handleUserPassAuth(ctx, msg);
            case READY -> {
                Boolean connected = channel.attr(SOCKS_CONNECTED).get();
                if (connected == null || !connected) {
                    // 第一次收到数据应为 CONNECT 请求
                    handleSocks5Connect(ctx, msg);
                } else {
                    // 之后为要转发的应用层数据
                    forwardData(ctx, msg);
                }
            }
        }
    }

    /**
     * 处理 SOCKS5 握手，客户端发送支持的认证方式，服务端选择用户名密码认证或拒绝连接。
     * 只接受用户名密码认证，若无支持方式则回复失败并关闭连接。
     */
    private void handleHandshake(ChannelHandlerContext ctx, ByteBuf msg) {
        msg.markReaderIndex();

        if (msg.readableBytes() < 2) {
            // 数据不完整，等待后续
            msg.resetReaderIndex();
            return;
        }

        byte ver = msg.readByte();
        byte nMethods = msg.readByte();

        if (ver != Socks5Constants.VERSION_SOCKS5) {
            log.warn("Unsupported SOCKS version: {}", ver);
            ctx.close();
            return;
        }

        if (msg.readableBytes() < nMethods) {
            msg.resetReaderIndex();
            return;
        }

        boolean hasUserPass = false;
        for (int i = 0; i < nMethods; i++) {
            byte method = msg.readByte();
            if (method == Socks5Constants.METHOD_USERNAME_PASSWORD) {
                hasUserPass = true;
            }
        }

        if (!hasUserPass) {
            // 客户端不支持用户名密码认证，拒绝连接
            ctx.writeAndFlush(ctx.alloc().buffer(2)
                            .writeByte(Socks5Constants.VERSION_SOCKS5)
                            .writeByte(Socks5Constants.METHOD_NO_ACCEPTABLE))
                    .addListener(f -> ctx.close());
        } else {
            // 选择用户名密码认证方式，切换状态到 AUTH
            ctx.writeAndFlush(ctx.alloc().buffer(2)
                    .writeByte(Socks5Constants.VERSION_SOCKS5)
                    .writeByte(Socks5Constants.METHOD_USERNAME_PASSWORD));
            ctx.channel().attr(SOCKS_STATE).set(SocksState.AUTH);
        }
    }

    /**
     * 处理用户名密码认证请求，验证用户名（作为 clientKey）和密码是否正确。
     * 认证成功后切换状态到 READY 并调用后续代理建立逻辑。
     */
    private void handleUserPassAuth(ChannelHandlerContext ctx, ByteBuf msg) {
        msg.markReaderIndex();

        if (msg.readableBytes() < 2) {
            msg.resetReaderIndex();
            return;
        }

        byte version = msg.readByte();
        if (version != Socks5Constants.AUTH_SUBNEGOTIATION_VERSION) {
            log.warn("Unsupported auth version: {}", version);
            ctx.close();
            return;
        }

        int uLen = msg.readByte() & 0xFF;
        if (msg.readableBytes() < uLen + 1) {
            msg.resetReaderIndex();
            return;
        }

        byte[] uname = new byte[uLen];
        msg.readBytes(uname);
        String authKey = new String(uname);

        int pLen = msg.readByte() & 0xFF;
        if (msg.readableBytes() < pLen) {
            msg.resetReaderIndex();
            return;
        }

        byte[] pass = new byte[pLen];
        msg.readBytes(pass);
        String password = new String(pass);

        log.info("Received auth request: authKey={}, password={}", authKey, password);

        Channel userChannel = ctx.channel();
        Channel cmdChannel = ProxyChannelManager.getCmdChannel(authKey);

        // 验证密码是否正确且存在对应控制通道
        if (cmdChannel != null && ProxyConfig.getInstance().getConfigSocksPassword().equals(password)) {
            // 认证成功，回复客户端并切换状态
            ctx.writeAndFlush(ctx.alloc().buffer(2)
                    .writeByte(Socks5Constants.AUTH_SUBNEGOTIATION_VERSION)
                    .writeByte(Socks5Constants.AUTH_STATUS_SUCCESS));
            userChannel.attr(Constants.AUTHENTICATED).set(true);
            userChannel.attr(SOCKS_STATE).set(SocksState.READY);
            userChannel.attr(CLIENT_KEY).set(authKey);
            // 认证成功后，处理代理连接映射等后续逻辑
            handlePostAuthSetup(ctx);
        } else {
            // 认证失败，回复失败并关闭连接
            ctx.writeAndFlush(ctx.alloc().buffer(2)
                            .writeByte(Socks5Constants.AUTH_SUBNEGOTIATION_VERSION)
                            .writeByte(Socks5Constants.AUTH_STATUS_FAILURE))
                    .addListener(f -> ctx.close());
        }
    }

    /**
     * 认证成功后处理，建立用户通道和控制通道的映射关系，
     * 生成唯一 userId，用于后续连接请求和数据转发关联。
     */
    private void handlePostAuthSetup(ChannelHandlerContext ctx) {
        Channel userChannel = ctx.channel();
        String userId = newUserId();
        String authKey = userChannel.attr(CLIENT_KEY).get();
        Channel cmdChannel = ProxyChannelManager.getCmdChannel(authKey);

        if (cmdChannel == null) {
            log.warn("cmdChannel not found for clientKey: {}", authKey);
            userChannel.close();
            return;
        }

        // 绑定 userId 与用户通道、控制通道的映射
        ProxyChannelManager.addUserChannelToCmdChannel(cmdChannel, userId, userChannel);
    }

    /**
     * 处理 SOCKS5 CONNECT 命令，解析目标地址和端口，
     * 并向代理服务端发送建立连接的请求。
     *
     * @param ctx 当前用户通道上下文
     * @param msg CONNECT 命令数据包
     */
    private void handleSocks5Connect(ChannelHandlerContext ctx, ByteBuf msg) {
        msg.markReaderIndex();

        if (msg.readableBytes() < 7) {
            // 数据不足，等待更多数据
            msg.resetReaderIndex();
            return;
        }

        byte ver = msg.readByte();  // 版本号
        byte cmd = msg.readByte();  // 命令码
        msg.readByte();  // 保留字段
        byte addressType = msg.readByte(); // 地址类型

        log.info("Processing SOCKS5 CONNECT request, addressType={} , cmd = {}", addressType,cmd);
        // 仅支持版本5和CONNECT命令
        if (ver != Socks5Constants.VERSION_SOCKS5 || cmd != Socks5Constants.CMD_CONNECT) {
            log.warn("Unsupported SOCKS5 CONNECT request, ver={}, cmd={}", ver, cmd);
            sendSocks5ConnectReply(ctx, Socks5Constants.REP_COMMAND_NOT_SUPPORTED);
            return;
        }

        String host;
        int port;

        try {
            // 解析目标地址，根据地址类型读取
            switch (addressType) {
                case Socks5Constants.ATYP_IPV4 -> {
                    if (msg.readableBytes() < 4 + 2) {
                        msg.resetReaderIndex();
                        return;
                    }
                    byte[] addrBytes = new byte[4];
                    msg.readBytes(addrBytes);
                    host = InetAddress.getByAddress(addrBytes).getHostAddress();
                }

                case Socks5Constants.ATYP_DOMAIN -> {
                    if (msg.readableBytes() < 1) {
                        msg.resetReaderIndex();
                        return;
                    }
                    int domainLen = msg.readByte() & 0xFF;
                    if (msg.readableBytes() < domainLen + 2) {
                        msg.resetReaderIndex();
                        return;
                    }
                    byte[] domainBytes = new byte[domainLen];
                    msg.readBytes(domainBytes);
                    host = new String(domainBytes, StandardCharsets.UTF_8);
                }

                case Socks5Constants.ATYP_IPV6 -> {
                    if (msg.readableBytes() < 16 + 2) {
                        msg.resetReaderIndex();
                        return;
                    }
                    byte[] addrBytes = new byte[16];
                    msg.readBytes(addrBytes);
                    host = InetAddress.getByAddress(addrBytes).getHostAddress();
                }

                default -> {
                    log.warn("Unknown address type: {}", addressType);
                    sendSocks5ConnectReply(ctx, Socks5Constants.REP_ADDRESS_TYPE_NOT_SUPPORTED);
                    return;
                }
            }

            port = msg.readUnsignedShort();

        } catch (Exception e) {
            log.warn("Failed to parse address/port", e);
            sendSocks5ConnectReply(ctx, Socks5Constants.REP_GENERAL_FAILURE);
            return;
        }

        Channel userChannel = ctx.channel();
        String userId = ProxyChannelManager.getUserChannelUserId(userChannel);
        String clientKey = userChannel.attr(CLIENT_KEY).get();
        userChannel.attr(SOCKS_CONNECTED).set(true);
        Channel cmdChannel = ProxyChannelManager.getCmdChannel(clientKey);

        if (cmdChannel == null) {
            log.warn("No cmd channel for clientKey: {}", clientKey);
            sendSocks5ConnectReply(ctx, Socks5Constants.REP_GENERAL_FAILURE);
            return;
        }

        // 暂停读取数据，等待代理连接建立完毕
        userChannel.config().setOption(ChannelOption.AUTO_READ, false);

        // 构造代理连接请求消息，通知代理端去连接目标服务器
        ProxyMessage connectMsg = new ProxyMessage();
        connectMsg.setType(ProxyMessage.TYPE_CONNECT);
        connectMsg.setUri(userId + ":" + host + ":" + port);
        cmdChannel.writeAndFlush(connectMsg);

        // 回复 SOCKS5 客户端连接成功响应
        sendSocks5ConnectReply(ctx, Socks5Constants.REP_SUCCEEDED);
    }

    /**
     * 发送 SOCKS5 连接回复报文，包含连接结果码及绑定地址信息。
     *
     * @param ctx 当前通道上下文
     * @param rep 连接结果码，如 0x00 表示成功
     */
    private void sendSocks5ConnectReply(ChannelHandlerContext ctx, byte rep) {
        ByteBuf resp = ctx.alloc().buffer();
        resp.writeByte(Socks5Constants.VERSION_SOCKS5); // VER
        resp.writeByte(rep);                            // REP
        resp.writeByte(0x00);                           // RSV
        resp.writeByte(Socks5Constants.ATYP_IPV4);     // ATYP (IPv4)
        resp.writeBytes(new byte[]{0, 0, 0, 0});       // BND.ADDR (0.0.0.0)
        resp.writeShort(0);                             // BND.PORT (0)

        ctx.writeAndFlush(resp);
    }

    /**
     * 转发用户数据到代理端通道，封装为 ProxyMessage。
     *
     * @param ctx 当前通道上下文
     * @param msg 用户发送的原始数据
     */
    private void forwardData(ChannelHandlerContext ctx, ByteBuf msg) {
        Channel userChannel = ctx.channel();
        Channel proxyChannel = userChannel.attr(Constants.NEXT_CHANNEL).get();

        if (proxyChannel == null) {
            log.warn("No proxy channel found");
            userChannel.close();
            return;
        }

        byte[] data = new byte[msg.readableBytes()];
        msg.readBytes(data);

        String userId = ProxyChannelManager.getUserChannelUserId(userChannel);

        ProxyMessage proxyMessage = new ProxyMessage();
        proxyMessage.setType(ProxyMessage.P_TYPE_TRANSFER);
        proxyMessage.setUri(userId);
        proxyMessage.setData(data);

        log.info(" forwardData userId={}, data length={}", userId, data.length);

        proxyChannel.writeAndFlush(proxyMessage);
    }

    /**
     * 当用户通道的可写状态改变时，调整代理通道的读取状态，
     * 实现背压控制，防止写缓冲区溢出。
     */
    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        Channel userChannel = ctx.channel();
        String clientKey = ProxyChannelManager.getCmdChannelClientKey(userChannel);
        Channel cmdChannel = ProxyChannelManager.getCmdChannel(clientKey);

        if (cmdChannel == null) {
            log.warn("WritabilityChanged: cmdChannel null");
            userChannel.close();
            return;
        }

        Channel proxyChannel = userChannel.attr(Constants.NEXT_CHANNEL).get();
        if (proxyChannel != null) {
            proxyChannel.config().setOption(ChannelOption.AUTO_READ, userChannel.isWritable());
        }

        super.channelWritabilityChanged(ctx);
    }

    /**
     * 发生异常时，记录日志并关闭当前通道。
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.warn("Exception caught: {}", cause.getMessage());
        ctx.close();
    }

    /**
     * 生成唯一的用户ID，用于标识每个用户连接。
     */
    private static String newUserId() {
        return UUIDShortGenerator.generateShortUuid();
    }
}
