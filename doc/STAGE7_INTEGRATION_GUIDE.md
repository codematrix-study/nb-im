# Stage 7 Integration Guide

## Quick Start: Integrating Monitoring into NettyIMServer

### Step 1: Add MonitoringManager to NettyIMServer

In `NettyIMServer.java`, add the monitoring manager:

```java
// In class fields
private MonitoringManager monitoringManager;

// In initBusinessComponents() method, after reliabilityMetrics initialization:
// 14. 初始化监控管理器
monitoringManager = new MonitoringManager(
        channelManager,
        redisManager,
        kafkaProducer,
        kafkaConsumerService,
        reliabilityMetrics,
        8081  // Admin API port
);
log.info("监控管理器初始化成功");

// In start() method, after ackTimeoutMonitor.start():
// 13. 启动监控管理器
monitoringManager.start();
log.info("监控管理器已启动");

// In shutdown() method, before closing Redis:
// 11. 关闭监控管理器
if (monitoringManager != null) {
    monitoringManager.stop();
    log.info("监控管理器已关闭");
}
```

### Step 2: Add Performance Tracking to RouteHandler

In `RouteHandler.java`, wrap message processing:

```java
@Override
public void channelRead0(ChannelHandlerContext ctx, IMMessage message) {
    // Generate and set trace ID
    String traceId = MDCUtils.generateAndSetTraceId();
    MDCUtils.setFromChannel(ctx.channel());
    MDCUtils.setFromMessage(message);

    // Record request start
    PerformanceMonitor perfMonitor = getPerformanceMonitor();
    long requestId = perfMonitor.recordRequestStart(PerformanceMetrics.MetricType.MESSAGE_SEND);

    try {
        // ... existing message processing logic ...

        // Record success
        perfMonitor.recordRequestSuccess(PerformanceMetrics.MetricType.MESSAGE_SEND, requestId);

        // Log audit
        AuditLogger.logMessageSend(userId, message);

    } catch (Exception e) {
        // Record failure
        perfMonitor.recordRequestFailure(PerformanceMetrics.MetricType.MESSAGE_SEND, requestId);

        // Log exception
        AuditLogger.logException(AuditMessageType.OTHER_ERROR, e);

    } finally {
        // Clear MDC
        MDCUtils.clear();
    }
}
```

### Step 3: Update pom.xml (if needed)

Ensure Netty HTTP codec dependency is available:

```xml
<dependency>
    <groupId>io.netty</groupId>
    <artifactId>netty-codec-http</artifactId>
</dependency>
```

### Step 4: Configure Logback

Create `logback.xml` in resources:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} [traceId=%X{traceId},userId=%X{userId},msgId=%X{msgId}] - %msg%n</pattern>
        </encoder>
    </appender>

    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/nb-im.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/nb-im.%d{yyyy-MM-dd}.log</fileNamePattern>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} [traceId=%X{traceId},userId=%X{userId}] - %msg%n</pattern>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="STDOUT" />
        <appender-ref ref="FILE" />
    </root>

    <!-- Debug logging for monitoring -->
    <logger name="com.cw.im.monitoring" level="DEBUG"/>
</configuration>
```

### Step 5: Run and Monitor

1. Start the server:
```bash
mvn clean package
java -jar nb-im-server/target/nb-im-server-1.0-SNAPSHOT.jar
```

2. Access monitoring endpoints:
```bash
# Health check
curl http://localhost:8081/health

# All metrics
curl http://localhost:8081/metrics

# System overview
curl http://localhost:8081/overview

# Active alerts
curl http://localhost:8081/alerts
```

3. Open dashboard:
```bash
# Open in browser
file:///path/to/nb-im/nb-im-core/src/main/resources/monitoring-dashboard.html
```

## Verification Checklist

- [ ] MonitoringManager starts without errors
- [ ] Admin API server responds on port 8081
- [ ] Health checks return UP for all components
- [ ] Performance metrics are being collected
- [ ] Audit logs appear in console/file
- [ ] Dashboard displays real-time data
- [ ] Alerts trigger when thresholds are exceeded
- [ ] MDC context appears in log output

## Troubleshooting

### Issue: Admin API port already in use
**Solution**: Change port in MonitoringManager constructor:
```java
monitoringManager = new MonitoringManager(..., 8082);
```

### Issue: Missing metrics
**Solution**: Check that collectors are initialized:
```java
// In MonitoringManager constructor
this.metricsCollectors.add(new JVMMetricsCollector());
this.metricsCollectors.add(new NettyMetricsCollector(channelManager));
// etc.
```

### Issue: MDC not appearing in logs
**Solution**: Verify Logback pattern includes %X{}:
```xml
<pattern>%d{HH:mm:ss.SSS} [traceId=%X{traceId}] - %msg%n</pattern>
```

### Issue: Dashboard shows "Loading failed"
**Solution**: Ensure Admin API server is running and CORS is enabled:
```bash
curl http://localhost:8081/health
```

## Performance Impact

The monitoring system has minimal performance impact:

- **Metrics Collection**: < 1ms per collection (thread-safe atomic operations)
- **Health Checks**: Async, non-blocking (< 5ms per check)
- **Alerting**: Async evaluation (60s interval)
- **API Server**: Separate thread, Netty-based (non-blocking I/O)
- **Logging**: Async via Logback

## Production Recommendations

1. **Adjust Intervals**: Increase check intervals for production:
   - Health checks: 60s (default: 30s)
   - Alert checks: 120s (default: 60s)

2. **Metrics Retention**: Configure metrics export to external system (Prometheus)

3. **Alert Routing**: Implement email/Slack alert handlers:
```java
alertingEngine.addHandler(new EmailAlertHandler("admin@example.com"));
alertingEngine.addHandler(new SlackAlertHandler(webhookUrl));
```

4. **Dashboard Security**: Add authentication to Admin API server

5. **Log Rotation**: Configure logback rolling policy for production

## Advanced Usage

### Custom Metrics Collector

```java
public class CustomMetricsCollector extends AbstractMetricsCollector {
    public CustomMetricsCollector() {
        super("custom", "Custom metrics collector");
    }

    @Override
    public Map<String, Object> collectMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        // ... collect custom metrics ...
        return metrics;
    }
}

// Register in MonitoringManager
metricsCollectors.add(new CustomMetricsCollector());
```

### Custom Alert Handler

```java
public class EmailAlertHandler implements AlertHandler {
    private final String recipient;

    public EmailAlertHandler(String recipient) {
        this.recipient = recipient;
    }

    @Override
    public void handle(Alert alert) {
        // Send email
        EmailService.send(recipient, alert.getTitle(), alert.getDescription());
    }

    @Override
    public String getName() {
        return "email";
    }
}

// Register
alertingEngine.addHandler(new EmailAlertHandler("admin@example.com"));
```

---

**Last Updated**: 2026-03-09
**Stage**: 7 - Monitoring & Operations
