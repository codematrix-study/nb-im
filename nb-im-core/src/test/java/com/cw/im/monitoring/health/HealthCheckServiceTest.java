package com.cw.im.monitoring.health;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 健康检查服务测试
 *
 * @author cw
 * @since 1.0.0
 */
class HealthCheckServiceTest {

    private HealthCheckService healthCheckService;

    @BeforeEach
    void setUp() {
        healthCheckService = new HealthCheckService(1);
    }

    @Test
    void testRegisterChecker() {
        HealthChecker checker = () -> HealthCheck.up("test_component");
        healthCheckService.registerChecker(checker);

        healthCheckService.checkNow();
        List<HealthCheck> results = healthCheckService.getAllResults();

        assertFalse(results.isEmpty());
        assertEquals("test_component", results.get(0).getName());
    }

    @Test
    void testUnregisterChecker() {
        HealthChecker checker = () -> HealthCheck.up("test_component");
        healthCheckService.registerChecker(checker);
        healthCheckService.unregisterChecker("test_component");

        healthCheckService.checkNow();
        List<HealthCheck> results = healthCheckService.getAllResults();

        assertTrue(results.isEmpty());
    }

    @Test
    void testGetResult() {
        HealthChecker checker = () -> HealthCheck.up("test_component");
        healthCheckService.registerChecker(checker);

        healthCheckService.checkNow();
        HealthCheck result = healthCheckService.getResult("test_component");

        assertNotNull(result);
        assertEquals("test_component", result.getName());
        assertEquals(HealthCheck.HealthStatus.UP, result.getStatus());
    }

    @Test
    void testOverallStatus() {
        HealthChecker checker1 = () -> HealthCheck.up("component1");
        HealthChecker checker2 = () -> HealthCheck.down("component2", "Test failure");

        healthCheckService.registerChecker(checker1);
        healthCheckService.registerChecker(checker2);

        healthCheckService.checkNow();

        // 整体状态应该是DOWN（有组件不健康）
        assertEquals(HealthCheckService.HealthStatus.DOWN, healthCheckService.getOverallStatus());
    }

    @Test
    void testStartAndStop() {
        healthCheckService.start();
        assertTrue(healthCheckService.isHealthy());

        healthCheckService.stop();
        // 停止后不应该再执行检查
    }

    @Test
    void testHealthCheckBuilder() {
        HealthCheck up = HealthCheck.up("test");
        assertEquals("test", up.getName());
        assertEquals(HealthCheck.HealthStatus.UP, up.getStatus());
        assertTrue(up.isHealthy());

        HealthCheck down = HealthCheck.down("test", "Failed");
        assertEquals("test", down.getName());
        assertEquals(HealthCheck.HealthStatus.DOWN, down.getStatus());
        assertFalse(down.isHealthy());

        HealthCheck degraded = HealthCheck.degraded("test", "Slow");
        assertEquals("test", degraded.getName());
        assertEquals(HealthCheck.HealthStatus.DEGRADED, degraded.getStatus());
        assertTrue(degraded.isAvailable());

        HealthCheck unknown = HealthCheck.unknown("test");
        assertEquals("test", unknown.getName());
        assertEquals(HealthCheck.HealthStatus.UNKNOWN, unknown.getStatus());
    }
}
