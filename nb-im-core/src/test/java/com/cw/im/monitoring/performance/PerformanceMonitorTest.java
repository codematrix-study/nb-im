package com.cw.im.monitoring.performance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 性能监控器测试
 *
 * @author cw
 * @since 1.0.0
 */
class PerformanceMonitorTest {

    private PerformanceMonitor monitor;

    @BeforeEach
    void setUp() {
        monitor = new PerformanceMonitor();
    }

    @Test
    void testRecordAndCollectMetrics() {
        // 记录请求
        long requestId1 = monitor.recordRequestStart(PerformanceMetrics.MetricType.MESSAGE_SEND);
        monitor.recordRequestSuccess(PerformanceMetrics.MetricType.MESSAGE_SEND, requestId1);

        long requestId2 = monitor.recordRequestStart(PerformanceMetrics.MetricType.MESSAGE_SEND);
        monitor.recordRequestSuccess(PerformanceMetrics.MetricType.MESSAGE_SEND, requestId2);

        // 收集指标
        PerformanceMetrics metrics = monitor.getMetrics(PerformanceMetrics.MetricType.MESSAGE_SEND);

        assertNotNull(metrics);
        assertEquals(2, metrics.getTotalCount());
        assertEquals(2, metrics.getSuccessCount());
        assertEquals(0, metrics.getFailureCount());
        assertTrue(metrics.getThroughput() >= 0);
    }

    @Test
    void testRecordFailure() {
        // 记录失败请求
        long requestId = monitor.recordRequestStart(PerformanceMetrics.MetricType.MESSAGE_RECEIVE);
        monitor.recordRequestFailure(PerformanceMetrics.MetricType.MESSAGE_RECEIVE, requestId);

        // 收集指标
        PerformanceMetrics metrics = monitor.getMetrics(PerformanceMetrics.MetricType.MESSAGE_RECEIVE);

        assertNotNull(metrics);
        assertEquals(1, metrics.getTotalCount());
        assertEquals(0, metrics.getSuccessCount());
        assertEquals(1, metrics.getFailureCount());
        assertEquals(100.0, metrics.getErrorRate());
    }

    @Test
    void testLatencyCalculation() {
        // 记录请求并计算延迟
        long requestId = monitor.recordRequestStart(PerformanceMetrics.MetricType.MESSAGE_DELIVERY);

        // 模拟一些处理时间
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        monitor.recordRequestSuccess(PerformanceMetrics.MetricType.MESSAGE_DELIVERY, requestId);

        // 收集指标
        PerformanceMetrics metrics = monitor.getMetrics(PerformanceMetrics.MetricType.MESSAGE_DELIVERY);

        assertNotNull(metrics);
        assertTrue(metrics.getAvgLatency() >= 10);
        assertTrue(metrics.getMaxLatency() >= 10);
    }

    @Test
    void testResetMetrics() {
        // 记录一些请求
        for (int i = 0; i < 10; i++) {
            long requestId = monitor.recordRequestStart(PerformanceMetrics.MetricType.MESSAGE_SEND);
            monitor.recordRequestSuccess(PerformanceMetrics.MetricType.MESSAGE_SEND, requestId);
        }

        // 重置指标
        monitor.resetMetrics(PerformanceMetrics.MetricType.MESSAGE_SEND);

        // 验证重置
        PerformanceMetrics metrics = monitor.getMetrics(PerformanceMetrics.MetricType.MESSAGE_SEND);
        assertEquals(0, metrics.getTotalCount());
    }

    @Test
    void testGetAllMetrics() {
        // 记录不同类型的请求
        long requestId1 = monitor.recordRequestStart(PerformanceMetrics.MetricType.MESSAGE_SEND);
        monitor.recordRequestSuccess(PerformanceMetrics.MetricType.MESSAGE_SEND, requestId1);

        long requestId2 = monitor.recordRequestStart(PerformanceMetrics.MetricType.MESSAGE_RECEIVE);
        monitor.recordRequestSuccess(PerformanceMetrics.MetricType.MESSAGE_RECEIVE, requestId2);

        // 获取所有指标
        PerformanceMetrics[] allMetrics = monitor.getAllMetrics();

        assertNotNull(allMetrics);
        assertTrue(allMetrics.length > 0);
    }
}
