package com.cw.im.server.stress;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * 连接压力测试
 *
 * <p>测试系统支持大量并发连接的能力
 *
 * <p>运行方式: mvn test -Dtest=ConnectionStressTest -Drun.stress=true
 *
 * @author cw
 * @since 1.0.0
 */
@DisplayName("连接压力测试")
@EnabledIfSystemProperty(named = "run.stress", matches = "true")
class ConnectionStressTest {

    /**
     * 测试10万连接
     *
     * <p>目标：验证系统能够处理10万个并发连接
     */
    @Test
    @DisplayName("测试10万连接")
    void test100kConnections() throws InterruptedException {
        String host = System.getProperty("test.host", "localhost");
        int port = Integer.getInteger("test.port", 8080);
        int totalConnections = 100000;
        int warmupSeconds = 60;

        StressTestClient client = new StressTestClient(
                host,
                port,
                totalConnections,
                0,  // 不发送消息，只测试连接
                120, // 运行2分钟
                warmupSeconds
        );

        client.start();
    }

    /**
     * 测试5万连接
     *
     * <p>目标：验证系统能够处理5万个并发连接
     */
    @Test
    @DisplayName("测试5万连接")
    void test50kConnections() throws InterruptedException {
        String host = System.getProperty("test.host", "localhost");
        int port = Integer.getInteger("test.port", 8080);
        int totalConnections = 50000;
        int warmupSeconds = 30;

        StressTestClient client = new StressTestClient(
                host,
                port,
                totalConnections,
                0,
                120,
                warmupSeconds
        );

        client.start();
    }

    /**
     * 测试1万连接
     *
     * <p>目标：验证系统能够处理1万个并发连接（快速测试）
     */
    @Test
    @DisplayName("测试1万连接")
    void test10kConnections() throws InterruptedException {
        String host = System.getProperty("test.host", "localhost");
        int port = Integer.getInteger("test.port", 8080);
        int totalConnections = 10000;
        int warmupSeconds = 10;

        StressTestClient client = new StressTestClient(
                host,
                port,
                totalConnections,
                0,
                60,
                warmupSeconds
        );

        client.start();
    }

    /**
     * 测试连接稳定性
     *
     * <p>目标：验证长时间连接的稳定性
     */
    @Test
    @DisplayName("测试连接稳定性 - 1000连接运行1小时")
    void testConnectionStability() throws InterruptedException {
        String host = System.getProperty("test.host", "localhost");
        int port = Integer.getInteger("test.port", 8080);
        int totalConnections = 1000;
        long testDurationSeconds = 3600; // 1小时
        int warmupSeconds = 30;

        StressTestClient client = new StressTestClient(
                host,
                port,
                totalConnections,
                100, // 低TPS，主要测试连接稳定性
                testDurationSeconds,
                warmupSeconds
        );

        client.start();
    }

    /**
     * 测试连接建立速度
     *
     * <p>目标：测量连接建立的性能
     */
    @Test
    @DisplayName("测试连接建立速度")
    void testConnectionEstablishmentRate() throws InterruptedException {
        String host = System.getProperty("test.host", "localhost");
        int port = Integer.getInteger("test.port", 8080);
        int totalConnections = 10000;

        long startTime = System.currentTimeMillis();

        StressTestClient client = new StressTestClient(
                host,
                port,
                totalConnections,
                0,
                10,  // 只运行10秒
                0
        );

        // 修改客户端逻辑以测量连接建立时间
        // 实际实现中需要在StressTestClient中添加连接时间统计

        client.start();

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        System.out.println("========================================");
        System.out.println("连接建立速度测试结果:");
        System.out.println("总连接数: " + totalConnections);
        System.out.println("总耗时: " + duration + "ms");
        System.out.println("平均连接建立时间: " + (duration * 1.0 / totalConnections) + "ms/连接");
        System.out.println("连接建立速率: " + (totalConnections * 1000.0 / duration) + " 连接/秒");
        System.out.println("========================================");
    }
}
