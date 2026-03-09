# 多阶段构建 Dockerfile for NB-IM

# 阶段1: 构建应用
FROM maven:3.8.6-openjdk-17 AS builder

# 设置工作目录
WORKDIR /app

# 复制 pom.xml 并下载依赖（利用 Docker 缓存）
COPY ../pom.xml .
RUN mvn dependency:go-offline -B

# 复制源代码
COPY src ./src

# 构建应用
RUN mvn clean package -DskipTests -B

# 阶段2: 运行应用
FROM openjdk:17-jdk-slim

# 维护者信息
LABEL maintainer="NB-IM Team <info@nb-im.example.com>"
LABEL version="1.0.0"
LABEL description="NB-IM Instant Messaging Middleware"

# 设置时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 创建应用用户（安全考虑）
RUN groupadd -r nbim && useradd -r -g nbim nbim

# 设置工作目录
WORKDIR /app

# 从构建阶段复制 JAR 文件
COPY --from=builder /app/nb-im-server/target/nb-im-server-*.jar app.jar

# 创建日志目录
RUN mkdir -p /app/logs && chown -R nbim:nbim /app

# 切换到应用用户
USER nbim

# 暴露端口
EXPOSE 8080 8081

# JVM 参数
ENV JAVA_OPTS="-Xms2g -Xmx2g \
               -XX:+UseG1GC \
               -XX:MaxGCPauseMillis=100 \
               -XX:+HeapDumpOnOutOfMemoryError \
               -XX:HeapDumpPath=/app/logs/heapdump.hprof \
               -Dio.netty.leakDetectionLevel=simple"

# 启动命令
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8081/health || exit 1
