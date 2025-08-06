[English](README.md) | [中文](README.zh.md)

# Project Introduction

This project is a high-performance proxy tool implemented based on the SOCKS5 protocol, supporting bidirectional
communication between the client and server. Through a custom protocol and flexible configuration, it meets various
network proxy scenarios, including traffic forwarding, access control, and secure authentication.

Key features include:

* Full support for the SOCKS5 protocol, including TCP and UDP forwarding
* Support for username and password authentication to ensure secure proxy connections
* Modular design for easy extension and maintenance
* High-performance network framework for low latency and high concurrency
* Cross-platform startup scripts supporting Windows and Linux/Mac environments

This project is suitable for scientific research, network debugging, and everyday proxy needs, providing users with
stable and reliable network proxy services.

---

## 📁 Project Structure Overview

The project directory structure is as follows:

```
socks5-proxy/
├── bin/                # Executable files directory
├── proxy-client/       # Client module
├── proxy-common/       # Common utilities module
├── proxy-protocol/     # Protocol definitions module
├── proxy-server/       # Server module
├── .gitignore          # Git ignore file
└── pom.xml             # Maven build configuration file
```

---

## 🧩 Main Modules Description

* **proxy-client**: Implements the SOCKS5 client functionality to connect to a SOCKS5 proxy server.
* **proxy-server**: Implements the SOCKS5 proxy server functionality to handle client requests and forward data.
* **proxy-common**: Contains shared utilities and constants used by both client and server.
* **proxy-protocol**: Defines the request and response formats related to the SOCKS5 protocol.

---


👉 [Java Client](https://github.com/duerlatter/socks5-proxy/tree/main/proxy-client)

👉 [C# Client](https://github.com/duerlatter/ProxyClient-cs)

## 🛠️ Build and Run

This project uses Maven as the build tool.

### Build the Project

```bash
mvn clean package
```

After building, startup scripts `startup.sh` (Linux/Mac) and `startup.bat` (Windows) along with related executable jars
will be generated under the `bin/` directory.

---

### Start the SOCKS5 Proxy Server

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

The server will listen on the default port (e.g., 1080). You can modify the port configuration in the source code if
needed.

### Start the SOCKS5 Proxy Client

Navigate to the `proxy-client` module directory and run the following command to start the client:

```bash
java -cp target/proxy-client-1.0-SNAPSHOT.jar com.duerlatter.proxy.client.ProxyClientContainer
```

The client will connect to the specified SOCKS5 proxy server and forward traffic.

---

## Configuration File Explanation — `config.properties`

### The `config.properties` file in the
`proxy-server` module is used to set parameters related to the SOCKS5 proxy service.

| Property                | Description                         | Example   | Notes                                         |
|-------------------------|-------------------------------------|-----------|-----------------------------------------------|
| `server.bind`           | Server bind IP address              | `0.0.0.0` | Usually `0.0.0.0` to listen on all interfaces |
| `server.port`           | Server listening port               | `4900`    | Main port for the proxy service               |
| `config.socks.bind`     | SOCKS5 proxy service bind IP        | `0.0.0.0` | Listen on all addresses                       |
| `config.socks.port`     | SOCKS5 proxy service listening port | `1080`    | Default port for SOCKS5 protocol              |
| `config.socks.password` | SOCKS5 proxy connection password    | `11111`   | Password used for client authentication       |

---

### Notes

* `server.bind` and `server.port` configure the core proxy service's network binding.
* `config.socks.bind` and `config.socks.port` configure the SOCKS5 proxy listening address and port.
* `config.socks.password` is used to authenticate clients to ensure proxy security.
* Adjust the bind addresses and ports according to your network environment.
* Use strong passwords to prevent unauthorized access.

---

## The `config.properties` file in the
`proxy-client` module is used to set parameters related to connecting to the SOCKS5 proxy server.

| Property      | Description                 | Example     | Notes                                        |
|---------------|-----------------------------|-------------|----------------------------------------------|
| `server.host` | Address of the proxy server | `127.0.0.1` | The IP or domain the client connects to      |
| `server.port` | Proxy server listening port | `4900`      | Must match the port configured on the server |

---

### Notes

* `server.host` specifies the SOCKS5 proxy server address to connect to, usually a LAN or public IP.
* `server.port` specifies the port that the proxy server listens on and must match the server’s port.
* Modify these values according to your deployment environment to ensure successful client connection.

---

## 🧪 Testing and Verification

No automated tests are provided, but you can manually verify functionality as follows:

1. Start the server.
2. Start the client and connect it to the server.
3. Configure your browser or tools (e.g., `curl`) to use the SOCKS5 proxy.
4. Verify that you can access target websites through the proxy.

---

## 📚 Example Command

You can test the SOCKS5 proxy connection in a terminal using the `curl` command in the following format:

```bash
curl -x socks5h://{username}:{password}@{proxy-server-ip}:{proxy-server-port} https://example.com
```

Replace the parameters as follows:

| Parameter             | Description                                                                           |
|-----------------------|---------------------------------------------------------------------------------------|
| `{username}`          | Unique identifier generated by the client for authentication                          |
| `{password}`          | The password configured in the server’s `config.properties` (`config.socks.password`) |
| `{proxy-server-ip}`   | IP address or domain name of the proxy server                                         |
| `{proxy-server-port}` | Listening port of the proxy server (e.g., 1080)                                       |

---

### Example

Given:

* Username: `user123`
* Password: `11111`
* Proxy server IP: `127.0.0.1`
* Proxy port: `1080`

The command would be:

```bash
curl -x socks5h://user123:11111@127.0.0.1:1080 https://example.com
```

---

This allows you to access `https://example.com` through the proxy server, making it easy to test and verify the proxy
service.

---
