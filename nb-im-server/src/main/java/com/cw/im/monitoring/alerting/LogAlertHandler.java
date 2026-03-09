package com.cw.im.monitoring.alerting;

import lombok.extern.slf4j.Slf4j;

/**
 * 日志告警处理器
 *
 * <p>将告警信息记录到日志</p>
 *
 * @author cw
 * @since 1.0.0
 */
@Slf4j
public class LogAlertHandler implements AlertHandler {

    @Override
    public void handle(Alert alert) {
        String alertLog = String.format(
                "[ALERT] level=%s, rule=%s, title=%s, description=%s, metric=%s, currentValue=%.2f, threshold=%.2f, timestamp=%s",
                alert.getLevel(),
                alert.getRuleName(),
                alert.getTitle(),
                alert.getDescription(),
                alert.getMetricName(),
                alert.getCurrentValue(),
                alert.getThreshold(),
                alert.getTimestamp()
        );

        switch (alert.getLevel()) {
            case CRITICAL:
                log.error(alertLog);
                break;
            case WARNING:
                log.warn(alertLog);
                break;
            case INFO:
            default:
                log.info(alertLog);
                break;
        }
    }

    @Override
    public String getName() {
        return "log";
    }
}
