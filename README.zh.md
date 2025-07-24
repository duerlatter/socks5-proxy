[English](README.md) | [中文](README.zh.md)

# 项目介绍

本项目是一个基于 SOCKS5 协议实现的高性能代理工具，支持客户端与服务端的双向通信。通过自定义协议和灵活的配置，能够满足多种网络代理场景需求，包括流量转发、访问控制和安全认证。

主要功能包括：

* 完整支持 SOCKS5 协议，包含 TCP 和 UDP 转发
* 支持用户名密码认证，保障代理连接安全
* 多模块设计，易于扩展和维护
* 采用高性能网络框架，实现低延迟、高并发
* 提供跨平台启动脚本，支持 Windows 和 Linux/Mac 环境

该项目适用于科研测试、网络调试及日常代理需求，为用户提供稳定可靠的网络代理服务。

---

## 📁 项目结构概览

该项目的目录结构如下：

```
socks5-proxy/
├── bin/                # 可执行文件目录
├── proxy-client/      # 客户端模块
├── proxy-common/      # 公共模块
├── proxy-protocol/    # 协议模块
├── proxy-server/      # 服务端模块
├── .gitignore         # Git 忽略文件
└── pom.xml             # Maven 构建配置文件
```

---

## 🧩 主要模块说明

* **proxy-client**：实现 SOCKS5 客户端功能，用于连接 SOCKS5 代理服务器。
* **proxy-server**：实现 SOCKS5 代理服务端功能，处理客户端请求并转发数据。
* **proxy-common**：包含客户端和服务端共享的工具类和常量定义。
* **proxy-protocol**：定义 SOCKS5 协议相关的请求和响应格式。

---

## 🛠️ 构建与运行

该项目使用 Maven 作为构建工具。

### 构建项目

```bash
mvn clean package
```

构建完成后，`bin/` 目录下会生成启动脚本 `startup.sh`（Linux/Mac）和 `startup.bat`（Windows），以及相关可执行 jar。

---

### 启动 SOCKS5 代理服务端

#### Linux / Mac

```bash
cd bin
./startup.sh
```

#### Windows

```bat
cd bin
startup.bat
```

服务端将监听默认端口（如 1080），可根据需要修改代码中的端口配置。

### 启动 SOCKS5 代理客户端

进入 `proxy-client` 模块目录，运行以下命令启动客户端：

```bash
java -cp target/proxy-client-1.0-SNAPSHOT.jar com.duerlatter.proxy.client.ProxyClientContainer
```

客户端将连接到指定的 SOCKS5 代理服务器，并转发流量。

---

## 配置文件说明 — `config.properties`

### proxy-server 模块的配置文件 `config.properties` 用于设置 SOCKS5 代理服务的相关参数。

| 配置项                     | 含义                   | 示例值       | 备注                       |
|-------------------------|----------------------|-----------|--------------------------|
| `server.bind`           | 服务端监听的绑定 IP 地址       | `0.0.0.0` | 通常设置为 `0.0.0.0` 表示监听所有网卡 |
| `server.port`           | 服务端监听的端口号            | `4900`    | 代理服务的主要端口                |
| `config.socks.bind`     | SOCKS5 代理服务绑定的 IP 地址 | `0.0.0.0` | 监听所有地址                   |
| `config.socks.port`     | SOCKS5 代理服务监听端口      | `1080`    | SOCKS5 协议默认端口            |
| `config.socks.password` | SOCKS5 代理连接密码（认证密码）  | `11111`   | 用于客户端连接时的密码认证            |

---

### 说明

* `server.bind` 和 `server.port` 负责服务端核心代理服务的网络监听设置。
* `config.socks.bind` 与 `config.socks.port` 用于配置 SOCKS5 代理服务的监听地址和端口。
* `config.socks.password` 是 SOCKS5 代理连接的密码，用于验证客户端身份，确保代理服务安全。
* 建议根据实际网络环境调整绑定地址和端口。
* 密码请设置复杂且安全，避免被未授权访问。

---

## proxy-client 模块的配置文件 `config.properties` 用于设置 SOCKS5 代理客户端连接的相关参数。

| 配置项           | 含义          | 示例值         | 备注                  |
|---------------|-------------|-------------|---------------------|
| `server.host` | 代理服务端的服务器地址 | `127.0.0.1` | 客户端将连接的代理服务端 IP 或域名 |
| `server.port` | 代理服务端监听的端口号 | `4900`      | 客户端连接代理服务端所使用的端口    |

---

### 说明

* `server.host` 指定客户端连接的 SOCKS5 代理服务器地址，通常为局域网或公网 IP。
* `server.port` 指定代理服务器监听的端口，需与服务端配置的端口保持一致。
* 请根据实际部署环境修改为正确的服务器地址和端口，确保客户端能成功连接。

---

## 🧪 测试与验证

项目中未提供自动化测试用例，但可以手动验证功能：

1. 启动服务端。
2. 启动客户端，连接到服务端。
3. 使用支持 SOCKS5 代理的工具（如浏览器或 `curl`）配置代理，访问目标网站。
4. 检查是否成功通过代理访问目标网站。

---

---

## 📚 参考命令

你可以在终端使用 `curl` 命令测试 SOCKS5 代理连接，格式如下：

```bash
curl -x socks5h://{用户名}:{密码}@{代理服务端IP}:{代理服务端端口} https://example.com
```

请根据实际情况替换以下参数：

| 参数          | 说明                                                       |
|-------------|----------------------------------------------------------|
| `{用户名}`     | 客户端生成的唯一标识，用于认证                                          |
| `{密码}`      | 代理服务端配置文件 `config.properties` 中的 `config.socks.password` |
| `{代理服务端IP}` | 代理服务器的 IP 地址或域名                                          |
| `{代理服务端端口}` | 代理服务器监听的端口号（如：1080）                                      |

---

### 示例

假设：

* 用户名：`user123`
* 密码：`11111`
* 代理服务器地址：`127.0.0.1`
* 代理端口：`1080`

则命令为：

```bash
curl -x socks5h://user123:11111@127.0.0.1:1080 https://example.com
```

---

这样就能通过代理服务器访问 `https://example.com`，方便测试和验证代理服务是否正常工作。

---

