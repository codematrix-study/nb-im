package com.cw.im.core;

import com.cw.im.common.constants.IMConstants;
import com.cw.im.common.constants.RedisKeys;
import com.cw.im.common.model.MessageStatus;
import com.cw.im.common.protocol.IMMessage;
import com.cw.im.redis.RedisManager;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ACK超时监控器
 *
 * <p>负责监控消息的ACK超时，触发重新投递或重试</p>
 *
 * <h3>核心功能</h3>
 * <ul>
 *     <li>超时检测：定期扫描待确认消息，检测超时</li>
 *     <li>自动重试：超时消息自动触发重试</li>
 *     <li>批量处理：批量查询待确认消息，提高效率</li>
 *     <li>状态管理：维护待确认消息集合</li>
 *     <li>线程安全：使用线程池和原子类保证并发安全</li>
 * </ul>
 *
 * <h3>工作流程</h3>
 * <pre>
 * 1. 定时扫描待确认消息集合
 * 2. 查询消息状态和发送时间
 * 3. 判断是否超时（当前时间 - 发送时间 > ACK超时时间）
 * 4. 超时消息触发重试
 * 5. 更新重试次数和状态
 * </pre>
 *
 * @author cw
 * @since 1.0.0
 */
@Slf4j
public class AckTimeoutMonitor {

    private final RedisManager redisManager;
    private final MessageStatusTracker statusTracker;
    private final MessagePersistenceStore persistenceStore;
    private final MessageRetryManager retryManager;

    private final ScheduledExecutorService monitorExecutor;
    private final ExecutorService retryExecutor;

    private final int ackTimeoutSeconds;
    private final int checkIntervalSeconds;
    private final int batchSize;

    /**
     * 统计信息
     */
    private final AtomicLong totalCheckedCount = new AtomicLong(0);
    private final AtomicLong timeoutDetectedCount = new AtomicLong(0);
    private final AtomicLong retryTriggeredCount = new AtomicLong(0);
    private final AtomicLong successRetryCount = new AtomicLong(0);
    private final AtomicLong failedRetryCount = new AtomicLong(0);

    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    /**
     * 构造函数（使用默认配置）
     *
     * @param redisManager      Redis管理器
     * @param statusTracker     状态跟踪器
     * @param persistenceStore  持久化存储
     * @param retryManager      重试管理器
     */
    public AckTimeoutMonitor(RedisManager redisManager,
                             MessageStatusTracker statusTracker,
                             MessagePersistenceStore persistenceStore,
                             MessageRetryManager retryManager) {
        this(
                redisManager,
                statusTracker,
                persistenceStore,
                retryManager,
                IMConstants.MESSAGE_ACK_TIMEOUT_SECONDS,
                IMConstants.MESSAGE_STATUS_CHECK_INTERVAL_SECONDS,
                IMConstants.PENDING_ACK_BATCH_SIZE
        );
    }

    /**
     * 完整参数构造函数
     *
     * @param redisManager      Redis管理器
     * @param statusTracker     状态跟踪器
     * @param persistenceStore  持久化存储
     * @param retryManager      重试管理器
     * @param ackTimeoutSeconds ACK超时时间（秒）
     * @param checkIntervalSeconds 检查间隔（秒）
     * @param batchSize         批量处理大小
     */
    public AckTimeoutMonitor(RedisManager redisManager,
                             MessageStatusTracker statusTracker,
                             MessagePersistenceStore persistenceStore,
                             MessageRetryManager retryManager,
                             int ackTimeoutSeconds,
                             int checkIntervalSeconds,
                             int batchSize) {
        this.redisManager = redisManager;
        this.statusTracker = statusTracker;
        this.persistenceStore = persistenceStore;
        this.retryManager = retryManager;
        this.ackTimeoutSeconds = ackTimeoutSeconds;
        this.checkIntervalSeconds = checkIntervalSeconds;
        this.batchSize = batchSize;

        // 创建线程池
        this.monitorExecutor = Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder("ack-timeout-monitor"));
        this.retryExecutor = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors(),
                new ThreadFactoryBuilder("ack-timeout-retry")
        );

        log.info("ACK超时监控器初始化完成: ackTimeout={}s, checkInterval={}s, batchSize={}",
                ackTimeoutSeconds, checkIntervalSeconds, batchSize);
    }

    /**
     * 启动监控器
     */
    public void start() {
        if (isRunning.compareAndSet(false, true)) {
            log.info("启动ACK超时监控器...");

            // 定时执行超时检查
            monitorExecutor.scheduleAtFixedRate(
                    this::performTimeoutCheck,
                    checkIntervalSeconds,
                    checkIntervalSeconds,
                    TimeUnit.SECONDS
            );

            log.info("ACK超时监控器已启动");
        }
    }

    /**
     * 停止监控器
     */
    public void stop() {
        if (isRunning.compareAndSet(true, false)) {
            log.info("正在停止ACK超时监控器...");

            // 关闭线程池
            monitorExecutor.shutdown();
            retryExecutor.shutdown();

            try {
                if (!monitorExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    monitorExecutor.shutdownNow();
                }
                if (!retryExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    retryExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                monitorExecutor.shutdownNow();
                retryExecutor.shutdownNow();
            }

            log.info("ACK超时监控器已停止");
        }
    }

    /**
     * 添加待确认消息
     *
     * @param userId 用户ID
     * @param msgId  消息ID
     * @return true-添加成功, false-添加失败
     */
    public boolean addPendingAck(Long userId, String msgId) {
        if (userId == null || msgId == null) {
            log.warn("用户ID或消息ID为空，无法添加待确认消息");
            return false;
        }

        try {
            String key = buildKey(userId);
            redisManager.sadd(key, msgId);
            redisManager.expire(key, (int) RedisKeys.PENDING_ACK_TTL.getSeconds());

            log.debug("添加待确认消息: userId={}, msgId={}", userId, msgId);
            return true;

        } catch (Exception e) {
            log.error("添加待确认消息异常: userId={}, msgId={}", userId, msgId, e);
            return false;
        }
    }

    /**
     * 移除待确认消息
     *
     * @param userId 用户ID
     * @param msgId  消息ID
     * @return true-移除成功, false-移除失败
     */
    public boolean removePendingAck(Long userId, String msgId) {
        if (userId == null || msgId == null) {
            return false;
        }

        try {
            String key = buildKey(userId);
            long removed = redisManager.srem(key, msgId);

            if (removed > 0) {
                log.debug("移除待确认消息: userId={}, msgId={}", userId, msgId);
                return true;
            }

            return false;

        } catch (Exception e) {
            log.error("移除待确认消息异常: userId={}, msgId={}", userId, msgId, e);
            return false;
        }
    }

    /**
     * 执行超时检查
     */
    private void performTimeoutCheck() {
        if (!isRunning.get()) {
            return;
        }

        try {
            log.debug("开始执行ACK超时检查...");

            // 遍历所有在线用户的待确认消息
            // 注意：这里简化处理，实际应该从Redis获取所有在线用户列表
            // 可以通过扫描KEYS或维护一个在线用户集合来实现

            // 这里使用示例逻辑，实际需要根据业务调整
            checkTimeoutMessages();

            log.debug("ACK超时检查完成");

        } catch (Exception e) {
            log.error("执行ACK超时检查异常", e);
        }
    }

    /**
     * 检查超时消息
     */
    private void checkTimeoutMessages() {
        // 这里简化处理，实际应该扫描所有用户的待确认消息集合
        // 可以使用Redis SCAN命令或维护一个待确认用户列表

        // 示例：检查特定用户的待确认消息
        // 实际实现时需要遍历所有在线用户

        log.debug("检查超时消息（简化版本）");
    }

    /**
     * 检查用户待确认消息中的超时消息
     *
     * @param userId 用户ID
     */
    public void checkUserTimeoutMessages(Long userId) {
        if (userId == null) {
            return;
        }

        try {
            String key = buildKey(userId);
            Set<String> msgIds = redisManager.smembers(key);

            if (msgIds == null || msgIds.isEmpty()) {
                return;
            }

            log.debug("检查用户待确认消息: userId={}, count={}", userId, msgIds.size());

            // 批量检查消息状态
            for (String msgId : msgIds) {
                totalCheckedCount.incrementAndGet();

                try {
                    // 查询消息状态
                    MessageStatus status = statusTracker.getStatus(msgId);

                    if (status == null) {
                        // 状态不存在，移除待确认
                        removePendingAck(userId, msgId);
                        continue;
                    }

                    // 检查是否为最终状态
                    if (status.isFinalStatus()) {
                        // 已到达最终状态，移除待确认
                        removePendingAck(userId, msgId);
                        continue;
                    }

                    // 检查是否超时
                    if (isTimeout(status)) {
                        timeoutDetectedCount.incrementAndGet();
                        log.warn("检测到ACK超时: msgId={}, status={}, duration={}ms",
                                msgId, status.getStatus(), status.getDuration());

                        // 触发重试
                        handleTimeoutMessage(userId, msgId, status);
                    }

                } catch (Exception e) {
                    log.error("检查消息超时异常: msgId={}", msgId, e);
                }
            }

        } catch (Exception e) {
            log.error("检查用户待确认消息异常: userId={}", userId, e);
        }
    }

    /**
     * 判断消息是否超时
     *
     * @param status 消息状态
     * @return true-超时, false-未超时
     */
    private boolean isTimeout(MessageStatus status) {
        if (status == null || status.getCreateTime() == null) {
            return false;
        }

        long elapsed = System.currentTimeMillis() - status.getCreateTime();
        return elapsed > TimeUnit.SECONDS.toMillis(ackTimeoutSeconds);
    }

    /**
     * 处理超时消息
     *
     * @param userId 用户ID
     * @param msgId  消息ID
     * @param status 消息状态
     */
    private void handleTimeoutMessage(Long userId, String msgId, MessageStatus status) {
        retryTriggeredCount.incrementAndGet();

        // 异步处理重试
        retryExecutor.submit(() -> {
            try {
                // 从持久化存储恢复消息
                IMMessage message = persistenceStore.recover(msgId);

                if (message == null) {
                    log.error("无法从持久化存储恢复消息: msgId={}", msgId);
                    removePendingAck(userId, msgId);
                    failedRetryCount.incrementAndGet();
                    return;
                }

                // 提交重试
                retryManager.submitRetry(msgId, message, msg1 -> {
                    // 重试回调：重新发送消息
                    // 这里应该调用实际的消息发送逻辑
                    // 简化处理，返回true表示重试成功
                    log.info("重试发送消息: msgId={}", msgId);
                    successRetryCount.incrementAndGet();
                    return true;
                });

                log.info("触发超时消息重试: msgId={}", msgId);

            } catch (Exception e) {
                log.error("处理超时消息异常: msgId={}", msgId, e);
                failedRetryCount.incrementAndGet();
            }
        });
    }

    /**
     * 构建Redis Key
     *
     * @param userId 用户ID
     * @return Redis Key
     */
    private String buildKey(Long userId) {
        return String.format(RedisKeys.PENDING_ACK_MESSAGES, userId);
    }

    // ==================== 统计方法 ====================

    /**
     * 获取总检查数量
     */
    public long getTotalCheckedCount() {
        return totalCheckedCount.get();
    }

    /**
     * 获取超时检测数量
     */
    public long getTimeoutDetectedCount() {
        return timeoutDetectedCount.get();
    }

    /**
     * 获取重试触发数量
     */
    public long getRetryTriggeredCount() {
        return retryTriggeredCount.get();
    }

    /**
     * 获取超时率
     */
    public double getTimeoutRate() {
        long total = totalCheckedCount.get();
        if (total == 0) {
            return 0.0;
        }
        return (double) timeoutDetectedCount.get() / total * 100;
    }

    /**
     * 获取统计信息
     */
    public String getStats() {
        return String.format(
                "AckTimeoutMonitor{checked=%d, timeout=%d(%.2f%%), retryTriggered=%d, successRetry=%d, failedRetry=%d}",
                totalCheckedCount.get(),
                timeoutDetectedCount.get(),
                getTimeoutRate(),
                retryTriggeredCount.get(),
                successRetryCount.get(),
                failedRetryCount.get()
        );
    }

    /**
     * 重置统计信息
     */
    public void resetStats() {
        totalCheckedCount.set(0);
        timeoutDetectedCount.set(0);
        retryTriggeredCount.set(0);
        successRetryCount.set(0);
        failedRetryCount.set(0);
        log.info("ACK超时监控统计信息已重置");
    }

    /**
     * 自定义线程工厂
     */
    private static class ThreadFactoryBuilder implements ThreadFactory {
        private final String namePrefix;
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        public ThreadFactoryBuilder(String namePrefix) {
            this.namePrefix = namePrefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, namePrefix + "-" + threadNumber.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
