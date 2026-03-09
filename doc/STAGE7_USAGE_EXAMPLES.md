# Stage 7 Usage Examples

## Real-World Monitoring Scenarios

### Scenario 1: Monitoring High Traffic Load

```java
// In your message handler
public void handleMessage(IMMessage message) {
    // Set MDC context for tracing
    MDCUtils.generateAndSetTraceId();
    MDCUtils.setUserId(message.getHeader().getFrom());

    // Record performance
    PerformanceMonitor monitor = getPerformanceMonitor();
    long requestId = monitor.recordRequestStart(
        PerformanceMetrics.MetricType.MESSAGE_SEND
    );

    long startTime = System.currentTimeMillis();

    try {
        // Process message
        routeMessage(message);

        // Record success with latency
        long latency = System.currentTimeMillis() - startTime;
        monitor.recordRequestSuccess(
            PerformanceMetrics.MetricType.MESSAGE_SEND,
            requestId
        );

        // Audit log
        AuditLogger.logMessageSend(
            message.getHeader().getFrom(),
            message
        );

    } catch (Exception e) {
        // Record failure
        monitor.recordRequestFailure(
            PerformanceMetrics.MetricType.MESSAGE_SEND,
            requestId
        );

        // Log exception
        AuditLogger.logException(
            AuditEventType.OTHER_ERROR,
            e
        );

    } finally {
        MDCUtils.clear();
    }
}

// Query metrics
curl http://localhost:8081/metrics/performance

// Response:
{
  "MESSAGE_SEND": {
    "throughput": 1250.5,
    "avgLatency": 15.2,
    "p99Latency": 45,
    "errorRate": 0.1
  }
}
```

### Scenario 2: Detecting Redis Issues

```bash
# Check health status
curl http://localhost:8081/health

# Response:
{
  "status": "DEGRADED",
  "isHealthy": false,
  "checks": [
    {
      "name": "redis",
      "status": "DEGRADED",
      "description": "Redis响应慢",
      "responseTime": 1500
    }
  ]
}

# View alert
curl http://localhost:8081/alerts

# Response:
[
  {
    "alertId": "a1b2c3d4",
    "ruleName": "redis_latency_high",
    "level": "WARNING",
    "title": "redis_latency_high告警",
    "description": "Redis响应延迟过高",
    "metricName": "redis_ping_latency_ms",
    "currentValue": 1500.0,
    "threshold": 1000.0,
    "timestamp": "2026-03-09T10:30:00"
  }
]
```

### Scenario 3: Tracking Message Flow with MDC

```java
// Logback configuration
<pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} [traceId=%X{traceId},userId=%X{userId},msgId=%X{msgId}] - %msg%n</pattern>

// In your code
public void processMessage(ChannelHandlerContext ctx, IMMessage message) {
    // Set MDC context
    String traceId = MDCUtils.generateAndSetTraceId();
    MDCUtils.setFromChannel(ctx.channel());
    MDCUtils.setFromMessage(message);

    try {
        // All logs in this thread will include MDC context
        log.info("Processing message from user {}", message.getHeader().getFrom());

        // Send to Kafka
        kafkaProducer.send(message);
        log.debug("Message sent to Kafka: msgId={}", message.getHeader().getMsgId());

        // Update Redis
        redisManager.updateUserStatus(message.getHeader().getFrom());
        log.debug("User status updated in Redis");

    } finally {
        MDCUtils.clear();
    }
}

// Log output:
// 10:30:15.123 [nioEventLoop-3-1] INFO  c.c.i.server.handler.RouteHandler [traceId=a1b2c3d4e5f6,userId=1001,msgId=msg-123] - Processing message from user 1001
// 10:30:15.125 [nioEventLoop-3-1] DEBUG c.c.i.server.handler.RouteHandler [traceId=a1b2c3d4e5f6,userId=1001,msgId=msg-123] - Message sent to Kafka: msgId=msg-123
// 10:30:15.126 [nioEventLoop-3-1] DEBUG c.c.i.server.handler.RouteHandler [traceId=a1b2c3d4e5f6,userId=1001,msgId=msg-123] - User status updated in Redis
```

### Scenario 4: Exporting Metrics for Prometheus

```java
// Export metrics
MetricsExporter exporter = monitoringManager.getMetricsExporter();

// Export to Prometheus format
String prometheusMetrics = exporter.exportToPrometheus();

// Save to file for Prometheus to scrape
exporter.saveToFile("prometheus", "/var/lib/prometheus/node-exporter/nb-metrics.prom");

// Or expose via HTTP endpoint in AdminApiServer
// Add to AdminApiServer.java:
case "/metrics/prometheus":
    result = exporter.exportToPrometheus();
    break;
```

Prometheus format output:
```
# NB-IM Metrics Export
# Generated at: 2026-03-09T10:30:00

nbim_jvm_jvm_memory_heap_used 536870912
nbim_jvm_jvm_memory_heap_max 2147483648
nbim_jvm_jvm_memory_heap_usage_percent 25.0
nbim_jvm_jvm_threads_count 50
nbim_redis_redis_ping_latency_ms 5
nbim_netty_netty_connections_current 1250
nbim_netty_netty_users_online 1100
```

### Scenario 5: Custom Alert for Business Logic

```java
// Create custom alert rule
AlertingEngine alertingEngine = monitoringManager.getAlertingEngine();

// Alert if message queue size is too large
alertingEngine.addRule(AlertRule.builder()
    .name("message_queue_large")
    .description("待处理消息队列过大")
    .metricName("message_queue_size")
    .operator(AlertRule.Operator.GREATER_THAN)
    .threshold(10000.0)
    .level(Alert.AlertLevel.CRITICAL)
    .enabled(true)
    .durationSeconds(120) // 2 minutes
    .build());

// Update metric from your business logic
public void enqueueMessage(IMMessage message) {
    messageQueue.offer(message);

    // Update alerting engine
    alertingEngine.updateMetric("message_queue_size", messageQueue.size());
}

public void processQueuedMessages() {
    int size = messageQueue.size();
    // ... process messages ...

    // Update after processing
    alertingEngine.updateMetric("message_queue_size", messageQueue.size());
}
```

### Scenario 6: Monitoring Multiple Gateways

```bash
# Gateway 1 (port 8080, admin port 8081)
curl http://gateway1.example.com:8081/overview

# Gateway 2 (port 8080, admin port 8081)
curl http://gateway2.example.com:8081/overview

# Aggregate metrics using monitoring system
# Query all gateways and aggregate data
```

### Scenario 7: Health Check for Kubernetes

```yaml
# Kubernetes deployment with health checks
apiVersion: apps/v1
kind: Deployment
metadata:
  name: nb-im-gateway
spec:
  replicas: 3
  template:
    spec:
      containers:
      - name: nb-im
        image: nb-im:1.0
        ports:
        - containerPort: 8080
        - containerPort: 8081
        livenessProbe:
          httpGet:
            path: /health
            port: 8081
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /health
            port: 8081
          initialDelaySeconds: 5
          periodSeconds: 5
```

### Scenario 8: Dashboard Monitoring Workflow

1. **Open Dashboard**
   ```
   file:///path/to/nb-im/nb-im-core/src/main/resources/monitoring-dashboard.html
   ```

2. **Monitor Real-Time Metrics**
   - System Status: Shows overall health (UP/DOWN/DEGRADED)
   - Online Connections: Current connection count
   - Online Users: Active user count
   - Active Alerts: Number of unresolved alerts

3. **Performance Monitoring**
   - Throughput: Requests per second for each operation type
   - Avg Latency: Average response time
   - P99 Latency: 99th percentile latency
   - Error Rate: Percentage of failed requests

4. **Health Checks**
   - Redis: Connection status and response time
   - Kafka: Producer/consumer status
   - Netty: Server status and connection info

5. **Alerts**
   - CRITICAL: System errors, high failure rates
   - WARNING: Performance degradation, high latency
   - INFO: Informational alerts

6. **Auto-Refresh**
   - Dashboard refreshes every 30 seconds
   - Manual refresh: Click "🔄 刷新数据" button

### Scenario 9: Troubleshooting with Logs and MDC

```java
// Problem: Message delivery failed for specific user

// 1. Check logs with MDC context
// Log output:
[traceId=abc123,userId=1001,msgId=msg-456] ERROR RouteHandler - Message delivery failed

// 2. Trace the request through the system
// All logs with traceId=abc123 will show:
// - Message received
// - Routing decision
// - Kafka send attempt
// - Failure point

// 3. Check metrics for the time period
curl http://localhost:8081/metrics/performance

// 4. Check health status
curl http://localhost:8081/health

// 5. Check for alerts
curl http://localhost:8081/alerts
```

### Scenario 10: Performance Analysis

```bash
# 1. Get performance metrics
curl http://localhost:8081/metrics/performance | jq '.'

# 2. Analyze throughput
{
  "MESSAGE_SEND": {
    "throughput": 1500.5,    # 1500 messages/second
    "avgLatency": 12.3,      # 12ms average
    "p99Latency": 45,        # 45ms P99
    "errorRate": 0.05        # 0.05% error rate
  }
}

# 3. If latency is high, check:
# - Redis ping latency
curl http://localhost:8081/metrics | jq '.redis.redis_ping_latency_ms'

# - JVM heap usage
curl http://localhost:8081/metrics | jq '.jvm.jvm_memory_heap_usage_percent'

# - Connection count
curl http://localhost:8081/metrics | jq '.netty.netty_connections_current'

# 4. Check for alerts
curl http://localhost:8081/alerts
```

## Monitoring Best Practices

1. **Set Up Alerting Early**: Configure alerts before production deployment
2. **Monitor Metrics Continuously**: Don't wait for issues to check metrics
3. **Use Trace IDs**: Always set MDC context for request tracing
4. **Review Logs Regularly**: Check audit logs for security events
5. **Baseline Performance**: Know your normal metrics to detect anomalies
6. **Test Alert Rules**: Verify alerts trigger before production
7. **Dashboard Visibility**: Make dashboard accessible to operations team
8. **Export Metrics**: Integrate with external monitoring (Prometheus, Grafana)

---

**Last Updated**: 2026-03-09
**Stage**: 7 - Monitoring & Operations
