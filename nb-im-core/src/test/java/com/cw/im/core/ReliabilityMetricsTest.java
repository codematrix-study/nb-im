package com.cw.im.core;

import com.cw.im.redis.RedisManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 可靠性指标测试
 *
 * @author cw
 * @since 1.0.0
 */
@DisplayName("可靠性指标测试")
class ReliabilityMetricsTest {

    private RedisManager redisManager;
    private ReliabilityMetrics metrics;

    @BeforeEach
    void setUp() {
        redisManager = mock(RedisManager.class);
        metrics = new ReliabilityMetrics(redisManager);
    }

    @Test
    @DisplayName("测试记录发送消息")
    void testRecordSent() {
        // 执行测试
        metrics.recordSent();
        metrics.recordSent();

        // 验证结果
        assertEquals(2, metrics.collectMetrics().getTotalSent());
    }

    @Test
    @DisplayName("测试记录发送成功")
    void testRecordSendSuccess() {
        // 执行测试
        metrics.recordSendSuccess();
        metrics.recordSendSuccess();

        // 验证结果
        assertEquals(2, metrics.collectMetrics().getSendSuccess());
    }

    @Test
    @DisplayName("测试记录发送失败")
    void testRecordSendFailed() {
        // 执行测试
        metrics.recordSendFailed();

        // 验证结果
        assertEquals(1, metrics.collectMetrics().getSendFailed());
    }

    @Test
    @DisplayName("测试计算发送成功率")
    void testCalculateSendSuccessRate() {
        // 执行测试
        metrics.recordSent();
        metrics.recordSent();
        metrics.recordSent();
        metrics.recordSendSuccess();
        metrics.recordSendSuccess();

        // 验证结果
        double rate = metrics.calculateSendSuccessRate();
        assertEquals(66.67, rate, 0.01);
    }

    @Test
    @DisplayName("测试记录投递成功")
    void testRecordDeliverySuccess() {
        // 执行测试
        metrics.recordDeliverySuccess(100);
        metrics.recordDeliverySuccess(200);

        // 验证结果
        assertEquals(2, metrics.collectMetrics().getDeliverySuccess());
    }

    @Test
    @DisplayName("测试记录投递失败")
    void testRecordDeliveryFailed() {
        // 执行测试
        metrics.recordDeliveryFailed();

        // 验证结果
        assertEquals(1, metrics.collectMetrics().getDeliveryFailed());
    }

    @Test
    @DisplayName("测试计算投递成功率")
    void testCalculateDeliverySuccessRate() {
        // 执行测试
        metrics.recordDelivered();
        metrics.recordDelivered();
        metrics.recordDelivered();
        metrics.recordDeliverySuccess(100);
        metrics.recordDeliverySuccess(200);
        metrics.recordDeliveryFailed();

        // 验证结果
        double rate = metrics.calculateDeliverySuccessRate();
        assertEquals(66.67, rate, 0.01);
    }

    @Test
    @DisplayName("测试计算平均投递延迟")
    void testCalculateAvgDeliveryLatency() {
        // 执行测试
        metrics.recordDeliverySuccess(100);
        metrics.recordDeliverySuccess(200);
        metrics.recordDeliverySuccess(300);

        // 验证结果
        double avg = metrics.calculateAvgDeliveryLatency();
        assertEquals(200.0, avg, 0.01);
    }

    @Test
    @DisplayName("测试记录重试")
    void testRecordRetry() {
        // 执行测试
        metrics.recordRetry();
        metrics.recordRetry();

        // 验证结果
        assertEquals(2, metrics.collectMetrics().getTotalRetry());
    }

    @Test
    @DisplayName("测试记录重试成功")
    void testRecordRetrySuccess() {
        // 执行测试
        metrics.recordRetrySuccess();

        // 验证结果
        assertEquals(1, metrics.collectMetrics().getRetrySuccess());
    }

    @Test
    @DisplayName("测试计算重试成功率")
    void testCalculateRetrySuccessRate() {
        // 执行测试
        metrics.recordRetry();
        metrics.recordRetry();
        metrics.recordRetry();
        metrics.recordRetrySuccess();
        metrics.recordRetrySuccess();

        // 验证结果
        double rate = metrics.calculateRetrySuccessRate();
        assertEquals(66.67, rate, 0.01);
    }

    @Test
    @DisplayName("测试记录死信消息")
    void testRecordDeadLetter() {
        // 执行测试
        metrics.recordDeadLetter();
        metrics.recordDeadLetter();

        // 验证结果
        assertEquals(2, metrics.collectMetrics().getDeadLetterCount());
    }

    @Test
    @DisplayName("测试获取最大投递延迟")
    void testGetMaxDeliveryLatency() {
        // 执行测试
        metrics.recordDeliverySuccess(100);
        metrics.recordDeliverySuccess(200);
        metrics.recordDeliverySuccess(300);

        // 验证结果
        long max = metrics.getMaxDeliveryLatency();
        assertEquals(300L, max);
    }

    @Test
    @DisplayName("测试获取最小投递延迟")
    void testGetMinDeliveryLatency() {
        // 执行测试
        metrics.recordDeliverySuccess(100);
        metrics.recordDeliverySuccess(200);
        metrics.recordDeliverySuccess(300);

        // 验证结果
        long min = metrics.getMinDeliveryLatency();
        assertEquals(100L, min);
    }

    @Test
    @DisplayName("测试获取P99投递延迟")
    void testGetP99DeliveryLatency() {
        // 执行测试 - 添加100个样本
        for (int i = 1; i <= 100; i++) {
            metrics.recordDeliverySuccess(i);
        }

        // 验证结果
        long p99 = metrics.getP99DeliveryLatency();
        assertTrue(p99 >= 99 && p99 <= 100);
    }

    @Test
    @DisplayName("测试获取P95投递延迟")
    void testGetP95DeliveryLatency() {
        // 执行测试 - 添加100个样本
        for (int i = 1; i <= 100; i++) {
            metrics.recordDeliverySuccess(i);
        }

        // 验证结果
        long p95 = metrics.getP95DeliveryLatency();
        assertTrue(p95 >= 95 && p95 <= 96);
    }

    @Test
    @DisplayName("测试收集指标数据")
    void testCollectMetrics() {
        // 执行测试
        metrics.recordSent();
        metrics.recordSendSuccess();
        metrics.recordDeliverySuccess(100);
        metrics.recordRetry();
        metrics.recordRetrySuccess();

        ReliabilityMetrics.MetricsData data = metrics.collectMetrics();

        // 验证结果
        assertNotNull(data);
        assertNotNull(data.getTimestamp());
        assertEquals(1, data.getTotalSent());
        assertEquals(1, data.getSendSuccess());
        assertEquals(1, data.getDeliverySuccess());
        assertEquals(1, data.getTotalRetry());
        assertEquals(1, data.getRetrySuccess());
    }

    @Test
    @DisplayName("测试获取统计信息")
    void testGetStats() {
        // 执行测试
        String stats = metrics.getStats();

        // 验证结果
        assertNotNull(stats);
        assertTrue(stats.contains("ReliabilityMetrics"));
    }

    @Test
    @DisplayName("测试重置统计信息")
    void testResetStats() {
        // 准备测试数据
        metrics.recordSent();
        metrics.recordSendSuccess();
        metrics.recordDeliverySuccess(100);

        // 执行测试
        metrics.resetStats();

        // 验证结果
        ReliabilityMetrics.MetricsData data = metrics.collectMetrics();
        assertEquals(0, data.getTotalSent());
        assertEquals(0, data.getSendSuccess());
        assertEquals(0, data.getDeliverySuccess());
    }

    @Test
    @DisplayName("测试格式化JSON")
    void testToJson() {
        // 执行测试
        String json = metrics.toJson();

        // 验证结果
        assertNotNull(json);
        assertFalse(json.isEmpty());
        assertTrue(json.contains("{"));
        assertTrue(json.contains("}"));
    }

    @Test
    @DisplayName("测试保存到Redis成功")
    void testSaveToRedisSuccess() throws Exception {
        // 准备测试数据
        when(redisManager.set(anyString(), anyString())).thenReturn(true);

        // 执行测试
        metrics.saveToRedis();

        // 验证结果
        verify(redisManager, times(1)).set(anyString(), anyString());
    }

    @Test
    @DisplayName("测试保存到Redis失败")
    void testSaveToRedisFailure() throws Exception {
        // 准备测试数据
        when(redisManager.set(anyString(), anyString())).thenReturn(false);

        // 执行测试（不应抛出异常）
        assertDoesNotThrow(() -> metrics.saveToRedis());
    }

    @Test
    @DisplayName("测试从Redis加载指标")
    void testLoadFromRedis() throws Exception {
        // 准备测试数据
        String json = "{\"totalSent\":100,\"sendSuccess\":95}";
        when(redisManager.get(anyString())).thenReturn(json);

        // 执行测试
        ReliabilityMetrics.MetricsData data = metrics.loadFromRedis();

        // 验证结果
        assertNotNull(data);
        assertEquals(100, data.getTotalSent());
        assertEquals(95, data.getSendSuccess());
    }

    @Test
    @DisplayName("测试从Redis加载指标失败（数据不存在）")
    void testLoadFromRedisNotExists() {
        // 准备测试数据
        when(redisManager.get(anyString())).thenReturn(null);

        // 执行测试
        ReliabilityMetrics.MetricsData data = metrics.loadFromRedis();

        // 验证结果
        assertNull(data);
    }
}
