package com.cw.im.monitoring.alerting;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 告警引擎测试
 *
 * @author cw
 * @since 1.0.0
 */
class AlertingEngineTest {

    private AlertingEngine alertingEngine;

    @BeforeEach
    void setUp() {
        alertingEngine = new AlertingEngine(1); // 1秒检查间隔
        alertingEngine.addHandler(new LogAlertHandler());
    }

    @Test
    void testAddRule() {
        AlertRule rule = AlertRule.builder()
                .name("test_rule")
                .metricName("test_metric")
                .operator(AlertRule.Operator.GREATER_THAN)
                .threshold(100.0)
                .level(Alert.AlertLevel.WARNING)
                .enabled(true)
                .durationSeconds(0)
                .build();

        alertingEngine.addRule(rule);

        List<AlertRule> rules = alertingEngine.getRules();
        assertEquals(1, rules.size());
        assertEquals("test_rule", rules.get(0).getName());
    }

    @Test
    void testRemoveRule() {
        AlertRule rule = AlertRule.builder()
                .name("test_rule")
                .metricName("test_metric")
                .operator(AlertRule.Operator.GREATER_THAN)
                .threshold(100.0)
                .level(Alert.AlertLevel.WARNING)
                .enabled(true)
                .durationSeconds(0)
                .build();

        alertingEngine.addRule(rule);
        alertingEngine.removeRule("test_rule");

        List<AlertRule> rules = alertingEngine.getRules();
        assertTrue(rules.isEmpty());
    }

    @Test
    void testAlertTriggered() {
        // 添加告警规则：指标大于100时触发告警
        AlertRule rule = AlertRule.builder()
                .name("high_value_alert")
                .metricName("test_metric")
                .operator(AlertRule.Operator.GREATER_THAN)
                .threshold(100.0)
                .level(Alert.AlertLevel.WARNING)
                .enabled(true)
                .durationSeconds(0)
                .build();

        alertingEngine.addRule(rule);
        alertingEngine.start();

        // 更新指标值为150（超过阈值）
        alertingEngine.updateMetric("test_metric", 150.0);

        // 等待告警检查
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 检查是否有活跃告警
        List<Alert> alerts = alertingEngine.getActiveAlerts();
        assertFalse(alerts.isEmpty());

        Alert alert = alerts.get(0);
        assertEquals("high_value_alert", alert.getRuleName());
        assertEquals(Alert.AlertLevel.WARNING, alert.getLevel());

        alertingEngine.stop();
    }

    @Test
    void testAlertResolved() {
        // 添加告警规则
        AlertRule rule = AlertRule.builder()
                .name("test_alert")
                .metricName("test_metric")
                .operator(AlertRule.Operator.GREATER_THAN)
                .threshold(100.0)
                .level(Alert.AlertLevel.WARNING)
                .enabled(true)
                .durationSeconds(0)
                .build();

        alertingEngine.addRule(rule);
        alertingEngine.start();

        // 触发告警
        alertingEngine.updateMetric("test_metric", 150.0);

        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        List<Alert> alerts = alertingEngine.getActiveAlerts();
        assertEquals(1, alerts.size());

        // 恢复正常
        alertingEngine.updateMetric("test_metric", 50.0);

        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 告警应该已恢复
        alerts = alertingEngine.getActiveAlerts();
        assertTrue(alerts.isEmpty());

        alertingEngine.stop();
    }

    @Test
    void testRuleMatches() {
        AlertRule rule = AlertRule.builder()
                .name("test_rule")
                .operator(AlertRule.Operator.GREATER_THAN)
                .threshold(100.0)
                .build();

        assertTrue(rule.matches(150.0));
        assertFalse(rule.matches(50.0));

        AlertRule rule2 = AlertRule.builder()
                .name("test_rule2")
                .operator(AlertRule.Operator.LESS_THAN)
                .threshold(100.0)
                .build();

        assertTrue(rule2.matches(50.0));
        assertFalse(rule2.matches(150.0));
    }

    @Test
    void testInitDefaultRules() {
        alertingEngine.initDefaultRules();

        List<AlertRule> rules = alertingEngine.getRules();
        assertTrue(rules.size() > 0);

        // 验证默认规则存在
        boolean hasSuccessRateRule = rules.stream()
                .anyMatch(rule -> rule.getName().equals("success_rate_low"));
        assertTrue(hasSuccessRateRule);
    }
}
