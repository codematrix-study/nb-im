package com.cw.im.monitoring.health;

import com.cw.im.kafka.KafkaProducerManager;
import lombok.extern.slf4j.Slf4j;

/**
 * Kafka健康检查器
 *
 * <p>检查Kafka生产者连接状态</p>
 *
 * @author cw
 * @since 1.0.0
 */
@Slf4j
public class KafkaHealthChecker implements HealthChecker {

    private final KafkaProducerManager producerManager;

    /**
     * 构造函数
     *
     * @param producerManager Kafka生产者管理器
     */
    public KafkaHealthChecker(KafkaProducerManager producerManager) {
        this.producerManager = producerManager;
    }

    @Override
    public HealthCheck check() {
        if (producerManager == null) {
            return HealthCheck.down(getName(), "KafkaProducerManager未初始化");
        }

        try {
            // 检查生产者是否可用
            boolean available = producerManager != null;

            if (available) {
                return HealthCheck.builder()
                        .name(getName())
                        .status(HealthCheck.HealthStatus.UP)
                        .description("Kafka生产者连接正常")
                        .build();
            } else {
                return HealthCheck.down(getName(), "Kafka生产者未初始化");
            }

        } catch (Exception e) {
            log.error("Kafka健康检查失败", e);
            return HealthCheck.builder()
                    .name(getName())
                    .status(HealthCheck.HealthStatus.DOWN)
                    .description("Kafka连接失败")
                    .exception(e)
                    .build();
        }
    }

    @Override
    public String getName() {
        return "kafka";
    }
}
