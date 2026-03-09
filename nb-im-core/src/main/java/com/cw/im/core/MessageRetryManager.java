package com.cw.im.core;

import com.cw.im.common.constants.IMConstants;
import com.cw.im.common.constants.RedisKeys;
import com.cw.im.common.model.MessageStatus;
import com.cw.im.common.protocol.IMMessage;
import com.cw.im.redis.RedisManager;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 消息重试管理器
 *
 * <p>负责处理消息发送失败后的重试逻辑，使用指数退避策略</p>
 *
 * <h3>核心功能</h3>
 * <ul>
 *     <li>异步重试：使用线程池异步执行重试任务</li>
 *     <li>指数退避：重试间隔按指数增长（1s, 2s, 4s, 8s...）</li>
 *     <li>最大重试次数：达到最大次数后不再重试</li>
 *     <li>重试计数：记录每条消息的重试次数</li>
 *     <li>失败处理：超过最大重试次数后进入死信队列</li>
 * </ul>
 *
 * <h3>重试策略</h3>
 * <pre>
 * 第1次重试：延迟 1 秒
 * 第2次重试：延迟 2 秒
 * 第3次重试：延迟 4 秒
 * ...
 * 最大延迟：60 秒
 * </pre>
 *
 * @author cw
 * @since 1.0.0
 */
@Slf4j
public class MessageRetryManager {

    private final RedisManager redisManager;
    private final MessageStatusTracker statusTracker;
    private final MessagePersistenceStore persistenceStore;
    private final DeadLetterQueue deadLetterQueue;

    private final ScheduledExecutorService retryExecutor;
    private final ExecutorService callbackExecutor;

    private final int maxRetryCount;
    private final long initialDelayMs;
    private final double backoffMultiplier;
    private final long maxDelayMs;

    /**
     * 统计信息
     */
    private final AtomicLong totalRetryCount = new AtomicLong(0);
    private final AtomicLong successRetryCount = new AtomicLong(0);
    private final AtomicLong failedRetryCount = new AtomicLong(0);
    private final AtomicLong abandonedRetryCount = new AtomicLong(0);

    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    /**
     * 重试回调接口
     */
    public interface RetryCallback {
        /**
         * 重试发送消息
         *
         * @param message 消息对象
         * @return true-发送成功, false-发送失败
         */
        boolean onRetry(IMMessage message);
    }

    /**
     * 构造函数（使用默认配置）
     *
     * @param redisManager      Redis管理器
     * @param statusTracker     状态跟踪器
     * @param persistenceStore  持久化存储
     * @param deadLetterQueue   死信队列
     */
    public MessageRetryManager(RedisManager redisManager,
                                MessageStatusTracker statusTracker,
                                MessagePersistenceStore persistenceStore,
                                DeadLetterQueue deadLetterQueue) {
        this(
                redisManager,
                statusTracker,
                persistenceStore,
                deadLetterQueue,
                IMConstants.MAX_MESSAGE_RETRY_COUNT,
                IMConstants.RETRY_INITIAL_DELAY_MS,
                IMConstants.RETRY_BACKOFF_MULTIPLIER,
                IMConstants.MAX_RETRY_DELAY_MS
        );
    }

    /**
     * 完整参数构造函数
     *
     * @param redisManager      Redis管理器
     * @param statusTracker     状态跟踪器
     * @param persistenceStore  持久化存储
     * @param deadLetterQueue   死信队列
     * @param maxRetryCount     最大重试次数
     * @param initialDelayMs    初始延迟（毫秒）
     * @param backoffMultiplier 退避倍数
     * @param maxDelayMs        最大延迟（毫秒）
     */
    public MessageRetryManager(RedisManager redisManager,
                                MessageStatusTracker statusTracker,
                                MessagePersistenceStore persistenceStore,
                                DeadLetterQueue deadLetterQueue,
                                int maxRetryCount,
                                long initialDelayMs,
                                double backoffMultiplier,
                                long maxDelayMs) {
        this.redisManager = redisManager;
        this.statusTracker = statusTracker;
        this.persistenceStore = persistenceStore;
        this.deadLetterQueue = deadLetterQueue;
        this.maxRetryCount = maxRetryCount;
        this.initialDelayMs = initialDelayMs;
        this.backoffMultiplier = backoffMultiplier;
        this.maxDelayMs = maxDelayMs;

        // 创建线程池
        this.retryExecutor = Executors.newScheduledThreadPool(
                Runtime.getRuntime().availableProcessors(),
                new ThreadFactoryBuilder("message-retry")
        );
        this.callbackExecutor = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors() * 2,
                new ThreadFactoryBuilder("retry-callback")
        );

        log.info("消息重试管理器初始化完成: maxRetry={}, initialDelay={}ms, backoff={%.1f}, maxDelay={}ms",
                maxRetryCount, initialDelayMs, backoffMultiplier, maxDelayMs);
    }

    /**
     * 启动重试管理器
     */
    public void start() {
        if (isRunning.compareAndSet(false, true)) {
            log.info("消息重试管理器已启动");
        }
    }

    /**
     * 停止重试管理器
     */
    public void stop() {
        if (isRunning.compareAndSet(true, false)) {
            log.info("正在停止消息重试管理器...");

            // 关闭线程池
            retryExecutor.shutdown();
            callbackExecutor.shutdown();

            try {
                if (!retryExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    retryExecutor.shutdownNow();
                }
                if (!callbackExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    callbackExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                retryExecutor.shutdownNow();
                callbackExecutor.shutdownNow();
            }

            log.info("消息重试管理器已停止");
        }
    }

    /**
     * 提交重试任务
     *
     * @param msgId   消息ID
     * @param message 消息对象
     * @param callback 重试回调
     */
    public void submitRetry(String msgId, IMMessage message, RetryCallback callback) {
        if (!isRunning.get()) {
            log.warn("重试管理器未运行，拒绝重试: msgId={}", msgId);
            return;
        }

        if (msgId == null || message == null) {
            log.warn("消息ID或消息对象为空，无法重试");
            return;
        }

        // 异步提交重试任务
        callbackExecutor.submit(() -> {
            try {
                doRetry(msgId, message, callback);
            } catch (Exception e) {
                log.error("重试任务异常: msgId={}", msgId, e);
            }
        });
    }

    /**
     * 执行重试逻辑
     *
     * @param msgId    消息ID
     * @param message  消息对象
     * @param callback 重试回调
     */
    private void doRetry(String msgId, IMMessage message, RetryCallback callback) {
        // 1. 获取当前重试次数
        int currentRetryCount = getRetryCount(msgId);

        // 2. 检查是否超过最大重试次数
        if (currentRetryCount >= maxRetryCount) {
            log.warn("超过最大重试次数，放弃重试: msgId={}, retryCount={}", msgId, currentRetryCount);
            abandonedRetryCount.incrementAndGet();

            // 移入死信队列
            moveToDeadLetterQueue(msgId, message, "超过最大重试次数");
            return;
        }

        // 3. 增加重试计数
        currentRetryCount = incrementRetryCount(msgId);
        totalRetryCount.incrementAndGet();

        log.info("准备重试消息: msgId={}, retryCount={}/{}", msgId, currentRetryCount, maxRetryCount);

        // 4. 计算延迟时间（指数退避）
        long delayMs = calculateDelay(currentRetryCount);

        // 5. 更新状态为发送中
        statusTracker.updateStatus(msgId, MessageStatus.Status.SENDING);

        // 6. 延迟执行重试
        int finalCurrentRetryCount = currentRetryCount;
        retryExecutor.schedule(() -> {
            try {
                executeRetry(msgId, message, callback, finalCurrentRetryCount);
            } catch (Exception e) {
                log.error("执行重试异常: msgId={}", msgId, e);
                handleRetryFailed(msgId, message, e.getMessage());
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 执行重试
     *
     * @param msgId         消息ID
     * @param message       消息对象
     * @param callback      重试回调
     * @param currentRetryCount 当前重试次数
     */
    private void executeRetry(String msgId, IMMessage message, RetryCallback callback, int currentRetryCount) {
        log.info("执行重试: msgId={}, retryCount={}/{}", msgId, currentRetryCount, maxRetryCount);

        try {
            // 调用回调执行重试
            boolean success = callback.onRetry(message);

            if (success) {
                // 重试成功
                handleRetrySuccess(msgId);
            } else {
                // 重试失败，继续重试
                handleRetryFailed(msgId, message, "重试回调返回失败");
            }

        } catch (Exception e) {
            log.error("重试回调异常: msgId={}", msgId, e);
            handleRetryFailed(msgId, message, e.getMessage());
        }
    }

    /**
     * 处理重试成功
     *
     * @param msgId 消息ID
     */
    private void handleRetrySuccess(String msgId) {
        successRetryCount.incrementAndGet();

        // 更新状态为已发送
        statusTracker.updateStatus(msgId, MessageStatus.Status.SENT);

        // 清除重试计数
        clearRetryCount(msgId);

        log.info("重试成功: msgId={}", msgId);
    }

    /**
     * 处理重试失败
     *
     * @param msgId    消息ID
     * @param message  消息对象
     * @param reason   失败原因
     */
    private void handleRetryFailed(String msgId, IMMessage message, String reason) {
        failedRetryCount.incrementAndGet();

        // 更新状态为失败
        statusTracker.updateStatus(msgId, MessageStatus.Status.FAILED, reason);

        // 检查是否可以继续重试
        int currentRetryCount = getRetryCount(msgId);
        if (currentRetryCount < maxRetryCount) {
            // 继续重试
            log.info("重试失败，准备下次重试: msgId={}, retryCount={}/{}", msgId, currentRetryCount, maxRetryCount);
            submitRetry(msgId, message, null); // 这里的callback需要从外部传入
        } else {
            // 超过最大重试次数，移入死信队列
            log.warn("重试失败且超过最大次数，移入死信队列: msgId={}", msgId);
            abandonedRetryCount.incrementAndGet();
            moveToDeadLetterQueue(msgId, message, reason);
        }
    }

    /**
     * 移入死信队列
     *
     * @param msgId   消息ID
     * @param message 消息对象
     * @param reason  失败原因
     */
    private void moveToDeadLetterQueue(String msgId, IMMessage message, String reason) {
        if (deadLetterQueue != null) {
            deadLetterQueue.enqueue(message, reason);
            log.warn("消息已移入死信队列: msgId={}, reason={}", msgId, reason);
        }

        // 清除重试计数
        clearRetryCount(msgId);
    }

    /**
     * 计算延迟时间（指数退避）
     *
     * @param retryCount 重试次数
     * @return 延迟时间（毫秒）
     */
    private long calculateDelay(int retryCount) {
        // 指数退避: initialDelay * (multiplier ^ (retryCount - 1))
        long delay = (long) (initialDelayMs * Math.pow(backoffMultiplier, retryCount - 1));

        // 限制最大延迟
        return Math.min(delay, maxDelayMs);
    }

    /**
     * 获取重试次数
     *
     * @param msgId 消息ID
     * @return 重试次数
     */
    private int getRetryCount(String msgId) {
        try {
            String key = buildKey(msgId);
            String count = redisManager.get(key);
            return count == null ? 0 : Integer.parseInt(count);
        } catch (Exception e) {
            log.error("获取重试次数异常: msgId={}", msgId, e);
            return 0;
        }
    }

    /**
     * 增加重试次数
     *
     * @param msgId 消息ID
     * @return 新的重试次数
     */
    private int incrementRetryCount(String msgId) {
        try {
            String key = buildKey(msgId);
            // 使用Redis原子递增
            long newCount = redisManager.incr(key);
            // 设置TTL
            redisManager.expire(key, (int) RedisKeys.MESSAGE_RETRY_COUNT_TTL.getSeconds());
            return (int) newCount;
        } catch (Exception e) {
            log.error("增加重试次数异常: msgId={}", msgId, e);
            return 0;
        }
    }

    /**
     * 清除重试次数
     *
     * @param msgId 消息ID
     */
    private void clearRetryCount(String msgId) {
        try {
            String key = buildKey(msgId);
            redisManager.del(key);
        } catch (Exception e) {
            log.error("清除重试次数异常: msgId={}", msgId, e);
        }
    }

    /**
     * 构建Redis Key
     *
     * @param msgId 消息ID
     * @return Redis Key
     */
    private String buildKey(String msgId) {
        return String.format(RedisKeys.MESSAGE_RETRY_COUNT, msgId);
    }

    // ==================== 统计方法 ====================

    /**
     * 获取总重试次数
     */
    public long getTotalRetryCount() {
        return totalRetryCount.get();
    }

    /**
     * 获取重试成功次数
     */
    public long getSuccessRetryCount() {
        return successRetryCount.get();
    }

    /**
     * 获取重试失败次数
     */
    public long getFailedRetryCount() {
        return failedRetryCount.get();
    }

    /**
     * 获取放弃重试次数
     */
    public long getAbandonedRetryCount() {
        return abandonedRetryCount.get();
    }

    /**
     * 获取重试成功率
     */
    public double getRetrySuccessRate() {
        long total = totalRetryCount.get();
        if (total == 0) {
            return 0.0;
        }
        return (double) successRetryCount.get() / total * 100;
    }

    /**
     * 获取统计信息
     */
    public String getStats() {
        return String.format(
                "MessageRetryManager{total=%d, success=%d(%.2f%%), failed=%d, abandoned=%d, maxRetry=%d}",
                totalRetryCount.get(),
                successRetryCount.get(),
                getRetrySuccessRate(),
                failedRetryCount.get(),
                abandonedRetryCount.get(),
                maxRetryCount
        );
    }

    /**
     * 重置统计信息
     */
    public void resetStats() {
        totalRetryCount.set(0);
        successRetryCount.set(0);
        failedRetryCount.set(0);
        abandonedRetryCount.set(0);
        log.info("重试管理器统计信息已重置");
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
