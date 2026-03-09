package com.cw.im.redis;

import com.cw.im.common.constants.IMConstants;
import com.cw.im.common.constants.RedisKeys;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 在线用户管理器
 *
 * <p>负责管理用户的在线状态、多端连接和心跳维护</p>
 *
 * <h3>核心功能</h3>
 * <ul>
 *     <li>用户上线/下线管理</li>
 *     <li>多端连接支持（一个用户多个Channel）</li>
 *     <li>心跳维护和超时检测</li>
 *     <li>在线状态查询</li>
 * </ul>
 *
 * <h3>Redis数据结构</h3>
 * <ul>
 *     <li>im:online:user:{userId} → gatewayId (String)</li>
 *     <li>im:user:channel:{userId} → Set[channelId1, channelId2, ...]</li>
 *     <li>im:heartbeat:user:{userId}:{channelId} → timestamp (String, TTL: 120秒)</li>
 * </ul>
 *
 * @author cw
 * @since 1.0.0
 */
@Slf4j
public class OnlineUserManager {

    private final RedisManager redisManager;

    /**
     * 构造函数
     *
     * @param redisManager Redis管理器
     */
    public OnlineUserManager(RedisManager redisManager) {
        this.redisManager = redisManager;
        log.info("OnlineUserManager 初始化完成");
    }

    /**
     * 用户上线
     *
     * <p>注册用户到指定的网关和Channel，并初始化心跳</p>
     *
     * @param userId    用户ID
     * @param gatewayId 网关ID
     * @param channelId 连接ID
     * @throws IllegalArgumentException 如果参数为空
     */
    public void registerUser(Long userId, String gatewayId, String channelId) {
        if (userId == null || gatewayId == null || channelId == null) {
            throw new IllegalArgumentException("参数不能为空: userId, gatewayId, channelId");
        }

        try {
            // 1. 设置用户在线网关
            String onlineUserKey = RedisKeys.buildOnlineUserKey(userId);
            redisManager.set(onlineUserKey, gatewayId);
            log.debug("设置用户在线网关: userId={}, gatewayId={}", userId, gatewayId);

            // 2. 添加用户Channel到Set
            String userChannelsKey = RedisKeys.buildUserChannelsKey(userId);
            redisManager.sadd(userChannelsKey, channelId);
            log.debug("添加用户Channel: userId={}, channelId={}", userId, channelId);

            // 3. 初始化心跳（带TTL）
            String heartbeatKey = RedisKeys.buildHeartbeatKey(userId, channelId);
            long currentTimestamp = IMConstants.getCurrentTimestamp();
            redisManager.setex(heartbeatKey, RedisKeys.HEARTBEAT_TIMEOUT_SECONDS, String.valueOf(currentTimestamp));
            log.debug("初始化用户心跳: userId={}, channelId={}, timestamp={}", userId, channelId, currentTimestamp);

            log.info("用户上线成功: userId={}, gatewayId={}, channelId={}", userId, gatewayId, channelId);
        } catch (Exception e) {
            log.error("用户上线失败: userId={}, gatewayId={}, channelId={}, error={}",
                userId, gatewayId, channelId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 用户下线
     *
     * <p>移除指定的Channel，如果用户没有其他Channel则清理所有在线数据</p>
     *
     * @param userId    用户ID
     * @param channelId 连接ID
     * @throws IllegalArgumentException 如果参数为空
     */
    public void unregisterUser(Long userId, String channelId) {
        if (userId == null || channelId == null) {
            throw new IllegalArgumentException("参数不能为空: userId, channelId");
        }

        try {
            // 1. 移除心跳记录
            String heartbeatKey = RedisKeys.buildHeartbeatKey(userId, channelId);
            redisManager.del(heartbeatKey);
            log.debug("移除用户心跳: userId={}, channelId={}", userId, channelId);

            // 2. 从Set中移除Channel
            String userChannelsKey = RedisKeys.buildUserChannelsKey(userId);
            redisManager.srem(userChannelsKey, channelId);
            log.debug("移除用户Channel: userId={}, channelId={}", userId, channelId);

            // 3. 检查用户是否还有其他Channel
            Set<String> remainingChannels = getUserChannels(userId);
            if (remainingChannels == null || remainingChannels.isEmpty()) {
                // 没有其他Channel，清理所有在线数据
                String onlineUserKey = RedisKeys.buildOnlineUserKey(userId);
                redisManager.del(onlineUserKey);
                redisManager.del(userChannelsKey);
                log.info("用户完全下线: userId={}, channelId={}", userId, channelId);
            } else {
                log.info("用户部分下线: userId={}, channelId={}, remainingChannels={}",
                    userId, channelId, remainingChannels.size());
            }
        } catch (Exception e) {
            log.error("用户下线失败: userId={}, channelId={}, error={}",
                userId, channelId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 获取用户所在网关
     *
     * @param userId 用户ID
     * @return 网关ID，如果用户不在线则返回null
     * @throws IllegalArgumentException 如果userId为空
     */
    public String getOnlineGateway(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId不能为空");
        }

        try {
            String onlineUserKey = RedisKeys.buildOnlineUserKey(userId);
            String gatewayId = redisManager.get(onlineUserKey);
            log.debug("查询用户网关: userId={}, gatewayId={}", userId, gatewayId);
            return gatewayId;
        } catch (Exception e) {
            log.error("查询用户网关失败: userId={}, error={}", userId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 更新心跳
     *
     * <p>更新用户心跳时间戳，并重置TTL</p>
     *
     * @param userId    用户ID
     * @param channelId 连接ID
     * @throws IllegalArgumentException 如果参数为空
     */
    public void heartbeat(Long userId, String channelId) {
        if (userId == null || channelId == null) {
            throw new IllegalArgumentException("参数不能为空: userId, channelId");
        }

        try {
            String heartbeatKey = RedisKeys.buildHeartbeatKey(userId, channelId);
            long currentTimestamp = IMConstants.getCurrentTimestamp();
            redisManager.setex(heartbeatKey, RedisKeys.HEARTBEAT_TIMEOUT_SECONDS, String.valueOf(currentTimestamp));
            log.debug("更新用户心跳: userId={}, channelId={}, timestamp={}", userId, channelId, currentTimestamp);
        } catch (Exception e) {
            log.error("更新用户心跳失败: userId={}, channelId={}, error={}",
                userId, channelId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 判断用户是否在线
     *
     * <p>通过检查用户在线网关记录判断用户是否在线</p>
     *
     * @param userId 用户ID
     * @return true-在线, false-不在线
     * @throws IllegalArgumentException 如果userId为空
     */
    public boolean isOnline(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId不能为空");
        }

        try {
            String onlineUserKey = RedisKeys.buildOnlineUserKey(userId);
            Boolean exists = redisManager.exists(onlineUserKey);
            log.debug("检查用户在线状态: userId={}, online={}", userId, exists);
            return exists;
        } catch (Exception e) {
            log.error("检查用户在线状态失败: userId={}, error={}", userId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 获取用户所有Channel
     *
     * @param userId 用户ID
     * @return Channel集合，如果用户不在线或没有Channel则返回空集合
     * @throws IllegalArgumentException 如果userId为空
     */
    public Set<String> getUserChannels(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId不能为空");
        }

        try {
            String userChannelsKey = RedisKeys.buildUserChannelsKey(userId);
            Set<String> channels = redisManager.smembers(userChannelsKey);
            log.debug("获取用户Channel列表: userId={}, channelCount={}", userId, channels != null ? channels.size() : 0);
            return channels;
        } catch (Exception e) {
            log.error("获取用户Channel列表失败: userId={}, error={}", userId, e.getMessage(), e);
            return Set.of();
        }
    }

    /**
     * 批量查询用户在线状态
     *
     * <p>批量查询多个用户的在线状态，使用Pipeline优化性能</p>
     *
     * @param userIds 用户ID列表
     * @return 用户ID到在线状态的映射
     * @throws IllegalArgumentException 如果userIds为空
     */
    public Map<Long, Boolean> batchCheckOnline(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            throw new IllegalArgumentException("userIds不能为空");
        }

        Map<Long, Boolean> result = new HashMap<>(userIds.size());

        try {
            // 批量查询
            for (Long userId : userIds) {
                if (userId != null) {
                    result.put(userId, isOnline(userId));
                }
            }
            log.debug("批量查询用户在线状态: totalCount={}, onlineCount={}",
                result.size(), result.values().stream().filter(Boolean::booleanValue).count());
        } catch (Exception e) {
            log.error("批量查询用户在线状态失败: userIds={}, error={}", userIds, e.getMessage(), e);
            // 返回所有用户都不在线
            userIds.forEach(userId -> {
                if (userId != null) {
                    result.put(userId, false);
                }
            });
        }

        return result;
    }

    /**
     * 获取在线用户总数
     *
     * <p>注意：此方法需要遍历所有在线用户Key，性能开销较大</p>
     *
     * @return 在线用户总数
     */
    public long getOnlineUserCount() {
        log.warn("获取在线用户总数操作可能性能开销较大，请谨慎使用");
        // TODO: 可以使用Redis的SCAN命令优化
        // 目前先返回-1表示不支持
        return -1;
    }

    /**
     * 获取用户心跳时间戳
     *
     * @param userId    用户ID
     * @param channelId 连接ID
     * @return 心跳时间戳（毫秒），如果不存在则返回-1
     */
    public long getHeartbeatTimestamp(Long userId, String channelId) {
        if (userId == null || channelId == null) {
            throw new IllegalArgumentException("参数不能为空: userId, channelId");
        }

        try {
            String heartbeatKey = RedisKeys.buildHeartbeatKey(userId, channelId);
            String timestampStr = redisManager.get(heartbeatKey);
            if (timestampStr != null && !timestampStr.isEmpty()) {
                return Long.parseLong(timestampStr);
            }
            return -1;
        } catch (NumberFormatException e) {
            log.error("解析心跳时间戳失败: userId={}, channelId={}", userId, channelId, e);
            return -1;
        } catch (Exception e) {
            log.error("获取用户心跳时间戳失败: userId={}, channelId={}, error={}",
                userId, channelId, e.getMessage(), e);
            return -1;
        }
    }

    /**
     * 清理用户所有在线数据
     *
     * <p>强制清理用户的所有在线状态数据（包括网关、Channel和心跳）</p>
     *
     * @param userId 用户ID
     */
    public void clearUserData(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId不能为空");
        }

        try {
            // 1. 清理所有心跳
            Set<String> channels = getUserChannels(userId);
            for (String channelId : channels) {
                String heartbeatKey = RedisKeys.buildHeartbeatKey(userId, channelId);
                redisManager.del(heartbeatKey);
            }

            // 2. 清理Channel列表
            String userChannelsKey = RedisKeys.buildUserChannelsKey(userId);
            redisManager.del(userChannelsKey);

            // 3. 清理在线网关
            String onlineUserKey = RedisKeys.buildOnlineUserKey(userId);
            redisManager.del(onlineUserKey);

            log.info("清理用户所有在线数据: userId={}, clearedChannels={}", userId, channels.size());
        } catch (Exception e) {
            log.error("清理用户在线数据失败: userId={}, error={}", userId, e.getMessage(), e);
            throw e;
        }
    }
}
