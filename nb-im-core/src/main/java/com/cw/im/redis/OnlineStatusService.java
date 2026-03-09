package com.cw.im.redis;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 在线状态服务
 *
 * <p>统一管理用户在线状态的顶层服务，组合了 OnlineUserManager 和 HeartbeatMonitor</p>
 *
 * <h3>核心职责</h3>
 * <ul>
 *     <li>初始化和关闭在线状态管理服务</li>
 *     <li>提供统一的在线状态管理接口</li>
 *     <li>协调心跳检测和用户管理</li>
 *     <li>管理服务生命周期</li>
 * </ul>
 *
 * @author cw
 * @since 1.0.0
 */
@Slf4j
public class OnlineStatusService {

    private final RedisManager redisManager;
    private final OnlineUserManager onlineUserManager;
    private final HeartbeatMonitor heartbeatMonitor;

    // 服务运行状态
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    // 心跳检测扫描间隔（秒）
    private final int heartbeatScanInterval;

    /**
     * 构造函数（使用默认心跳扫描间隔30秒）
     *
     * @param redisManager Redis管理器
     */
    public OnlineStatusService(RedisManager redisManager) {
        this(redisManager, 30);
    }

    /**
     * 构造函数
     *
     * @param redisManager         Redis管理器
     * @param heartbeatScanInterval 心跳扫描间隔（秒）
     */
    public OnlineStatusService(RedisManager redisManager, int heartbeatScanInterval) {
        this.redisManager = redisManager;
        this.heartbeatScanInterval = heartbeatScanInterval;

        // 初始化在线用户管理器
        this.onlineUserManager = new OnlineUserManager(redisManager);

        // 初始化心跳检测器
        this.heartbeatMonitor = new HeartbeatMonitor(onlineUserManager, redisManager, heartbeatScanInterval);

        log.info("OnlineStatusService 初始化完成, heartbeatScanInterval={}s", heartbeatScanInterval);
    }

    /**
     * 初始化服务
     *
     * <p>启动心跳检测器，开始监控用户在线状态</p>
     */
    public void init() {
        if (initialized.compareAndSet(false, true)) {
            try {
                // 检查Redis连接
                boolean healthy = redisManager.healthCheck();
                if (!healthy) {
                    throw new IllegalStateException("Redis连接不健康，无法启动在线状态服务");
                }

                // 启动心跳检测器
                heartbeatMonitor.start();

                log.info("在线状态服务启动成功");
            } catch (Exception e) {
                initialized.set(false);
                log.error("在线状态服务启动失败: {}", e.getMessage(), e);
                throw e;
            }
        } else {
            log.warn("在线状态服务已经初始化");
        }
    }

    /**
     * 关闭服务
     *
     * <p>优雅关闭，停止心跳检测</p>
     */
    public void shutdown() {
        if (initialized.compareAndSet(true, false)) {
            try {
                // 停止心跳检测器
                heartbeatMonitor.stop();

                log.info("在线状态服务已关闭");
            } catch (Exception e) {
                log.error("关闭在线状态服务失败: {}", e.getMessage(), e);
            }
        } else {
            log.warn("在线状态服务未在运行");
        }
    }

    /**
     * ==================== 用户上线/下线 ====================
     */

    /**
     * 用户上线
     *
     * @param userId    用户ID
     * @param gatewayId 网关ID
     * @param channelId 连接ID
     */
    public void registerUser(Long userId, String gatewayId, String channelId) {
        ensureInitialized();
        onlineUserManager.registerUser(userId, gatewayId, channelId);
    }

    /**
     * 用户下线
     *
     * @param userId    用户ID
     * @param channelId 连接ID
     */
    public void unregisterUser(Long userId, String channelId) {
        ensureInitialized();
        onlineUserManager.unregisterUser(userId, channelId);
    }

    /**
     * ==================== 心跳管理 ====================
     */

    /**
     * 更新心跳
     *
     * @param userId    用户ID
     * @param channelId 连接ID
     */
    public void heartbeat(Long userId, String channelId) {
        ensureInitialized();
        onlineUserManager.heartbeat(userId, channelId);
    }

    /**
     * 手动触发一次过期用户扫描
     */
    public void scanExpiredUsers() {
        ensureInitialized();
        heartbeatMonitor.scanExpiredUsers();
    }

    /**
     * ==================== 在线状态查询 ====================
     */

    /**
     * 获取用户所在网关
     *
     * @param userId 用户ID
     * @return 网关ID
     */
    public String getOnlineGateway(Long userId) {
        ensureInitialized();
        return onlineUserManager.getOnlineGateway(userId);
    }

    /**
     * 判断用户是否在线
     *
     * @param userId 用户ID
     * @return true-在线, false-不在线
     */
    public boolean isOnline(Long userId) {
        ensureInitialized();
        return onlineUserManager.isOnline(userId);
    }

    /**
     * 获取用户所有Channel
     *
     * @param userId 用户ID
     * @return Channel集合
     */
    public Set<String> getUserChannels(Long userId) {
        ensureInitialized();
        return onlineUserManager.getUserChannels(userId);
    }

    /**
     * 批量查询用户在线状态
     *
     * @param userIds 用户ID列表
     * @return 用户ID到在线状态的映射
     */
    public Map<Long, Boolean> batchCheckOnline(List<Long> userIds) {
        ensureInitialized();
        return onlineUserManager.batchCheckOnline(userIds);
    }

    /**
     * 获取用户心跳时间戳
     *
     * @param userId    用户ID
     * @param channelId 连接ID
     * @return 心跳时间戳（毫秒）
     */
    public long getHeartbeatTimestamp(Long userId, String channelId) {
        ensureInitialized();
        return onlineUserManager.getHeartbeatTimestamp(userId, channelId);
    }

    /**
     * ==================== 清理操作 ====================
     */

    /**
     * 清理用户所有在线数据
     *
     * @param userId 用户ID
     */
    public void clearUserData(Long userId) {
        ensureInitialized();
        onlineUserManager.clearUserData(userId);
    }

    /**
     * 清理用户的所有过期连接
     *
     * @param userId 用户ID
     * @return 清理的连接数量
     */
    public int cleanupExpiredConnections(Long userId) {
        ensureInitialized();
        return heartbeatMonitor.cleanupExpiredConnections(userId);
    }

    /**
     * ==================== 状态查询 ====================
     */

    /**
     * 服务是否已初始化
     *
     * @return true-已初始化, false-未初始化
     */
    public boolean isInitialized() {
        return initialized.get();
    }

    /**
     * 心跳检测器是否正在运行
     *
     * @return true-运行中, false-已停止
     */
    public boolean isHeartbeatMonitorRunning() {
        return heartbeatMonitor.isRunning();
    }

    /**
     * 获取心跳扫描间隔
     *
     * @return 扫描间隔（秒）
     */
    public int getHeartbeatScanInterval() {
        return heartbeatScanInterval;
    }

    /**
     * 获取Redis连接统计信息
     *
     * @return 统计信息JSON字符串
     */
    public String getRedisStats() {
        return redisManager.getConnectionStats();
    }

    /**
     * 确保服务已初始化
     */
    private void ensureInitialized() {
        if (!initialized.get()) {
            throw new IllegalStateException("在线状态服务未初始化，请先调用 init() 方法");
        }
    }

    /**
     * 获取在线用户管理器（仅供内部使用）
     *
     * @return OnlineUserManager实例
     */
    public OnlineUserManager getOnlineUserManager() {
        return onlineUserManager;
    }

    /**
     * 获取心跳检测器（仅供内部使用）
     *
     * @return HeartbeatMonitor实例
     */
    public HeartbeatMonitor getHeartbeatMonitor() {
        return heartbeatMonitor;
    }

    /**
     * 获取Redis管理器（仅供内部使用）
     *
     * @return RedisManager实例
     */
    public RedisManager getRedisManager() {
        return redisManager;
    }

    @Override
    public String toString() {
        return "OnlineStatusService{" +
            "initialized=" + initialized.get() +
            ", heartbeatRunning=" + heartbeatMonitor.isRunning() +
            ", scanInterval=" + heartbeatScanInterval + "s" +
            '}';
    }
}
