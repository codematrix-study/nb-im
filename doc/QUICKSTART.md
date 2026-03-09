# NB-IM 快速入门指南

本指南将帮助您在 5 分钟内启动并运行 NB-IM 即时通讯中间件。

## 前置条件

确保您的系统已安装以下软件：

- JDK 17 或更高版本
- Maven 3.6 或更高版本
- Docker 和 Docker Compose

验证安装：

```bash
java -version
mvn -version
docker --version
docker-compose --version
```

## 第一步：获取代码

克隆项目仓库：

```bash
git clone https://github.com/your-org/nb-im.git
cd nb-im
```

## 第二步：启动依赖服务

使用 Docker Compose 启动 Redis 和 Kafka：

```bash
docker-compose up -d redis kafka zookeeper
```

验证服务状态：

```bash
docker-compose ps
```

预期输出：

```
NAME                STATUS
nb-im-redis         Up
nb-im-kafka         Up
nb-im-zookeeper     Up
```

## 第三步：编译项目

编译项目并跳过测试（快速启动）：

```bash
mvn clean package -DskipTests
```

或者运行完整构建：

```bash
mvn clean package
```

## 第四步：启动服务器

使用 Maven 启动：

```bash
cd nb-im-server
mvn spring-boot:run
```

或直接运行 JAR：

```bash
java -jar nb-im-server/target/nb-im-server-1.0.0.jar
```

服务器启动后，您会看到以下日志：

```
========================================
Netty IM 服务器启动成功!
监听端口: 8080
网关ID: gateway-xxxxxxxx
========================================
```

## 第五步：验证服务

### 健康检查

```bash
curl http://localhost:8081/health
```

预期响应：

```json
{
  "statusCode": 200,
  "message": "OK",
  "data": {
    "status": "UP",
    "components": {
      "redis": {"status": "UP"},
      "kafka": {"status": "UP"},
      "netty": {"status": "UP"}
    }
  }
}
```

### 查看系统概览

```bash
curl http://localhost:8081/overview
```

### 查看所有指标

```bash
curl http://localhost:8081/metrics
```

## 第六步：连接测试

### 使用 WebSocket 客户端

创建一个简单的 HTML 文件 `test.html`：

```html
<!DOCTYPE html>
<html>
<head>
    <title>NB-IM Test</title>
</head>
<body>
    <script>
        const ws = new WebSocket('ws://localhost:8080');

        ws.onopen = () => {
            console.log('Connected to NB-IM');

            // 发送认证消息
            ws.send(JSON.stringify({
                header: {
                    msgId: crypto.randomUUID(),
                    cmd: 'AUTH',
                    from: 1001,
                    timestamp: Date.now()
                },
                body: {
                    token: 'test-token',
                    deviceId: 'test-device'
                }
            }));
        };

        ws.onmessage = (event) => {
            console.log('Received:', event.data);
        };

        // 发送测试消息
        function sendTestMessage() {
            ws.send(JSON.stringify({
                header: {
                    msgId: crypto.randomUUID(),
                    cmd: 'PRIVATE_CHAT',
                    from: 1001,
                    to: 1002,
                    timestamp: Date.now()
                },
                body: {
                    content: 'Hello from NB-IM!'
                }
            }));
        }
    </script>
    <button onclick="sendTestMessage()">Send Message</button>
</body>
</html>
```

在浏览器中打开此文件，然后：

1. 打开浏览器开发者工具（F12）
2. 查看 Console 标签页
3. 点击 "Send Message" 按钮
4. 观察消息发送和接收

### 使用 wscat

安装 wscat：

```bash
npm install -g wscat
```

连接并发送消息：

```bash
# 连接
wscat -c ws://localhost:8080

# 发送认证消息
> {"header":{"msgId":"test-001","cmd":"AUTH","from":1001,"timestamp":1646812345678},"body":{"token":"test-token","deviceId":"test-device"}}

# 发送私聊消息
> {"header":{"msgId":"test-002","cmd":"PRIVATE_CHAT","from":1001,"to":1002,"timestamp":1646812345678},"body":{"content":"Hello NB-IM!"}}
```

## 常见问题

### 问题 1: 端口已被占用

如果 8080 端口已被占用，修改配置：

编辑 `nb-im-server/src/main/resources/application.yml`：

```yaml
server:
  port: 8081  # 改为其他端口
```

或设置环境变量：

```bash
export SERVER_PORT=8081
java -jar nb-im-server-1.0.0.jar
```

### 问题 2: Redis 连接失败

检查 Redis 是否运行：

```bash
docker-compose logs redis
```

手动测试 Redis 连接：

```bash
docker exec -it nb-im-redis redis-cli ping
```

预期输出：`PONG`

### 问题 3: Kafka 连接失败

检查 Kafka 是否运行：

```bash
docker-compose logs kafka
```

查看 Kafka 日志：

```bash
docker-compose logs -f kafka
```

### 问题 4: 内存不足

如果遇到内存不足错误，增加 JVM 堆内存：

```bash
java -Xms1g -Xmx2g -jar nb-im-server-1.0.0.jar
```

或在 `application.yml` 中配置：

```yaml
java:
  opts: -Xms1g -Xmx2g
```

## 下一步

### 1. 查看监控仪表板

打开 `nb-im-server/src/main/resources/static/monitoring-dashboard.html` 查看实时监控数据。

### 2. 阅读完整文档

- [README.md](../README.md) - 完整项目说明
- [PROJECT_STRUCTURE.md](PROJECT_STRUCTURE.md) - 项目结构
- [DEVELOPMENT_PLAN.md](DEVELOPMENT_PLAN.md) - 开发计划

### 3. 运行测试

```bash
# 单元测试
mvn test

# 集成测试
mvn test -Dtest=*IntegrationTest

# 压力测试
mvn test -Dtest=ThroughputStressTest -Drun.stress=true
```

### 4. 部署到生产环境

参考 [README.md](../README.md#部署) 中的部署指南。

### 5. 开发新功能

参考 [CONTRIBUTING.md](CONTRIBUTING.md) 了解如何贡献代码。

## 获取帮助

- 📖 查看 [完整文档](docs/)
- 🐛 提交 [Issue](https://github.com/your-org/nb-im/issues)
- 💬 加入 [讨论区](https://github.com/your-org/nb-im/discussions)
- 📧 发送邮件到 support@nb-im.example.com

---

祝您使用愉快！🎉
