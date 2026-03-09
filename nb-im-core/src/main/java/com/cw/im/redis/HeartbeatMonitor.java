package com.cw.im.redis;

import com.cw.im.common.constants.IMConstants;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 心跳检测器
 *
 * <p>定期扫描并清理心跳超时的用户连接</p>
 *
 * <h3>工作原理</h3>
 * <ul>
 *     <li>使用定时任务定期扫描用户心跳</li>
 *     <li>对比当前时间与心跳时间戳，判断是否超时</li>
 *     <li>清理超时用户的在线状态</li>
 *     <li>支持优雅停止</li>
 * </ul>
 *
 * @author cw
 * @since 1.0.0
 */
@Slf4j
public class HeartbeatMonitor {

    private final OnlineUserManager onlineUserManager;
    private final RedisManager redisManager;

    // 定时任务线程池
    private ScheduledExecutorService scheduler;

    // 运行状态标志
    private final AtomicBoolean running = new AtomicBoolean(false);

    // 默认扫描间隔（秒）
    private static final int DEFAULT_SCAN_INTERVAL_SECONDS = 30;

    // 扫描间隔（秒）
    private final int scanIntervalSeconds;

    /**
     * 构造函数（使用默认扫描间隔）
     *
     * @param onlineUserManager 在线用户管理器
     * @param redisManager      Redis管理器
     */
    public HeartbeatMonitor(OnlineUserManager onlineUserManager, RedisManager redisManager) {
        this(onlineUserManager, redisManager, DEFAULT_SCAN_INTERVAL_SECONDS);
    }

    /**
     * 构造函数
     *
     * @param onlineUserManager 在线用户管理器
     * @param redisManager      Redis管理器
     * @param scanIntervalSeconds 扫描间隔（秒）
     */
    public HeartbeatMonitor(OnlineUserManager onlineUserManager, RedisManager redisManager, int scanIntervalSeconds) {
        this.onlineUserManager = onlineUserManager;
        this.redisManager = redisManager;
        this.scanIntervalSeconds = scanIntervalSeconds;
        log.info("HeartbeatMonitor 初始化完成, scanIntervalSeconds={}", scanIntervalSeconds);
    }

    /**
     * 启动心跳检测定时任务
     *
     * <p>启动后会按照设定的间隔定期扫描并清理过期用户</p>
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            try {
                // 创建单线程的定时任务执行器
                scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread thread = new Thread(r, "heartbeat-monitor");
                    thread.setDaemon(true); // 设置为守护线程
                    return thread;
                });

                // 启动定时任务
                scheduler.scheduleWithFixedDelay(
                    this::scanExpiredUsers,
                    scanIntervalSeconds, // 初始延迟
                    scanIntervalSeconds, // 执行间隔
                    TimeUnit.SECONDS
                );

                log.info("心跳检测器已启动, scanIntervalSeconds={}", scanIntervalSeconds);
            } catch (Exception e) {
                running.set(false);
                log.error("启动心跳检测器失败: {}", e.getMessage(), e);
                throw e;
            }
        } else {
            log.warn("心跳检测器已经在运行中");
        }
    }

    /**
     * 停止心跳检测
     *
     * <p>优雅停止，等待当前任务完成</p>
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            try {
                if (scheduler != null && !scheduler.isShutdown()) {
                    scheduler.shutdown();
                    // 等待最多10秒让任务完成
                    if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                        log.warn("心跳检测器停止超时，强制关闭");
                        scheduler.shutdownNow();
                    }
                }
                log.info("心跳检测器已停止");
            } catch (InterruptedException e) {
                log.error("停止心跳检测器被中断: {}", e.getMessage(), e);
                Thread.currentThread().interrupt();
            }
        } else {
            log.warn("心跳检测器未在运行");
        }
    }

    /**
     * 扫描并清理过期用户
     *
     * <p>这是核心的扫描逻辑，会遍历所有在线用户并检查心跳</p>
     */
    public void scanExpiredUsers() {
        if (!running.get()) {
            log.debug("心跳检测器已停止，跳过扫描");
            return;
        }

        long startTime = System.currentTimeMillis();
        int totalChecked = 0;
        int totalExpired = 0;

        try {
            log.debug("开始扫描过期用户");

            // TODO: 这里需要优化的实现方式
            // 当前实现：由于Redis没有直接的遍历方式，这里提供框架代码
            // 实际生产环境需要：
            // 1. 使用SCAN命令遍历所有 im:heartbeat:user:* key
            // 2. 解析key获取userId和channelId
            // 3. 检查心跳是否过期
            // 4. 清理过期的连接

            // 示例伪代码：
            // ScanCursor cursor = ScanCursor.INITIAL;
            // do {
            //     KeyScanCursor<String> scanResult = redisManager.syncCommands().scan(cursor);
            //     for (String key : scanResult.getKeys()) {
            //         if (key.startsWith("im:heartbeat:user:")) {
            //             // 解析userId和channelId
            //             // 检查是否过期
            //             // 清理过期连接
            //         }
            //     }
            //     cursor = ScanCursor.of(scanResult.getCursor());
            // } while (!cursor.isFinished());

            log.debug("扫描过期用户完成: totalChecked={}, totalExpired={},耗时={}ms",
                totalChecked, totalExpired, System.currentTimeMillis() - startTime);

        } catch (Exception e) {
            log.error("扫描过期用户失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 检查用户心跳是否超时
     *
     * @param userId    用户ID
     * @param channelId 连接ID
     * @return true-超时, false-未超时
     */
    public boolean isHeartbeatExpired(Long userId, String channelId) {
        if (userId == null || channelId == null) {
            return false;
        }

        try {
            long heartbeatTimestamp = onlineUserManager.getHeartbeatTimestamp(userId, channelId);
            if (heartbeatTimestamp < 0) {
                // 心跳记录不存在，视为已过期
                log.debug("用户心跳记录不存在: userId={}, channelId={}", userId, channelId);
                return true;
            }

            long currentTimestamp = IMConstants.getCurrentTimestamp();
            boolean expired = IMConstants.isHeartbeatTimeout(heartbeatTimestamp, currentTimestamp);

            if (expired) {
                log.info("用户心跳超时: userId={}, channelId={}, heartbeatTime={}, currentTime={}",
                    userId, channelId, heartbeatTimestamp, currentTimestamp);
            }

            return expired;
        } catch (Exception e) {
            log.error("检查用户心跳超时失败: userId={}, channelId={}, error={}",
                userId, channelId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 清理过期的用户连接
     *
     * @param userId    用户ID
     * @param channelId 连接ID
     */
    public void cleanupExpiredConnection(Long userId, String channelId) {
        try {
            log.info("清理过期连接: userId={}, channelId={}", userId, channelId);
            onlineUserManager.unregisterUser(userId, channelId);
        } catch (Exception e) {
            log.error("清理过期连接失败: userId={}, channelId={}, error={}",
                userId, channelId, e.getMessage(), e);
        }
    }

    /**
     * 清理用户的所有过期连接
     *
     * @param userId 用户ID
     * @return 清理的连接数量
     */
    public int cleanupExpiredConnections(Long userId) {
        if (userId == null) {
            return 0;
        }

        try {
            Set<String> channels = onlineUserManager.getUserChannels(userId);
            int cleanedCount = 0;

            for (String channelId : channels) {
                if (isHeartbeatExpired(userId, channelId)) {
                    cleanupExpiredConnection(userId, channelId);
                    cleanedCount++;
                }
            }

            if (cleanedCount > 0) {
                log.info("清理用户过期连接: userId={}, cleanedCount={}", userId, cleanedCount);
            }

            return cleanedCount;
        } catch (Exception e) {
            log.error("清理用户过期连接失败: userId={}, error={}", userId, e.getMessage(), e);
            return 0;
        }
    }

    /**
     * 是否正在运行
     *
     * @return true-运行中, false-已停止
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * 获取扫描间隔
     *
     * @return 扫描间隔（秒）
     */
    public int getScanIntervalSeconds() {
        return scanIntervalSeconds;
    }
}
