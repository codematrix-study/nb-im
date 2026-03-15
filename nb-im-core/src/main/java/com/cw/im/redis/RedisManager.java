package com.cw.im.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisException;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.codec.StringCodec;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Redis 管理器
 *
 * <p>提供 Redis 连接管理、基本操作和连接池监控功能</p>
 *
 * @author cw
 */
@Slf4j
public class RedisManager {

    // Static initializer to set system property BEFORE Lettuce classes load
    static {
        System.setProperty("io.netty.noNative", "true");
    }

    private static final String DEFAULT_HOST = "192.168.1.48";
    private static final int DEFAULT_PORT = 6379;
    private static final String DEFAULT_PASSWORD = null;
    private static final int DEFAULT_DATABASE = 0;

    // 重试配置
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1000;

    private RedisClient redisClient;
    private StatefulRedisConnection<String, String> connection;
    private RedisCommands<String, String> syncCommands;

    // 连接池监控统计
    private final AtomicInteger connectionRetryCount = new AtomicInteger(0);
    private final AtomicInteger operationFailureCount = new AtomicInteger(0);
    private volatile long lastHealthCheckTime = 0L;
    private volatile boolean isHealthy = true;

    public RedisManager() {
        this(DEFAULT_HOST, DEFAULT_PORT, DEFAULT_PASSWORD, DEFAULT_DATABASE);
    }

    public RedisManager(String host, int port, String password, int database) {
        init(host, port, password, database);
    }

    /**
     * 初始化 Redis 连接
     */
    private void init(String host, int port, String password, int database) {
        try {
            // 构建 RedisURI
            RedisURI.Builder builder = RedisURI.builder()
                .withHost(host)
                .withPort(port)
                .withDatabase(database);

            // 设置密码
            if (password != null && !password.isEmpty()) {
                builder.withPassword(password.toCharArray());
            }

            // 设置超时时间
            builder.withTimeout(Duration.of(5, ChronoUnit.SECONDS));

            RedisURI redisURI = builder.build();

            // 创建 RedisClient
            redisClient = RedisClient.create(redisURI);

            // 创建连接
            connection = redisClient.connect(StringCodec.UTF8);

            // 获取同步命令接口
            syncCommands = connection.sync();

            // 执行健康检查
            healthCheck();

            log.info("Redis 连接初始化成功: {}:{}", host, port);
        } catch (Exception e) {
            log.error("Redis 连接初始化失败: {}:{}, 错误: {}", host, port, e.getMessage(), e);
            throw new RedisException("Redis 连接初始化失败", e);
        }
    }

    /**
     * ==================== String 操作 ====================
     */

    /**
     * 设置键值对
     */
    public void set(String key, String value) {
        executeWithRetry(() -> {
            syncCommands.set(key, value);
            log.debug("Redis SET: key={}, value={}", key, value);
            return null;
        });
    }

    /**
     * 设置键值对（带过期时间）
     */
    public void setex(String key, long seconds, String value) {
        executeWithRetry(() -> {
            syncCommands.setex(key, seconds, value);
            log.debug("Redis SETEX: key={}, seconds={}, value={}", key, seconds, value);
            return null;
        });
    }

    /**
     * 设置键值对（带过期时间，使用Duration）
     */
    public void setex(String key, String value, Duration ttl) {
        setex(key, ttl.getSeconds(), value);
    }

    /**
     * 设置键值对（仅当键不存在时，SETNX）
     *
     * @param key   键
     * @param value 值
     * @param ttl   过期时间（秒）
     * @return true-设置成功（键不存在）, false-键已存在
     */
    public Boolean setnx(String key, String value, long ttl) {
        return executeWithRetry(() -> {
            Boolean result = syncCommands.setnx(key, value);
            if (Boolean.TRUE.equals(result)) {
                // 设置成功，添加过期时间
                syncCommands.expire(key, ttl);
            }
            log.debug("Redis SETNX: key={}, value={}, ttl={}, result={}", key, value, ttl, result);
            return result;
        });
    }

    /**
     * 设置键值对（仅当键不存在时，SETNX，使用Duration）
     *
     * @param key   键
     * @param value 值
     * @param ttl   过期时间
     * @return true-设置成功（键不存在）, false-键已存在
     */
    public Boolean setnx(String key, String value, Duration ttl) {
        return setnx(key, value, ttl.getSeconds());
    }

    /**
     * 获取值
     */
    public String get(String key) {
        return executeWithRetry(() -> {
            String value = syncCommands.get(key);
            log.debug("Redis GET: key={}, value={}", key, value);
            return value;
        });
    }

    /**
     * 删除键
     */
    public Long del(String key) {
        return executeWithRetry(() -> {
            Long result = syncCommands.del(key);
            log.debug("Redis DEL: key={}, result={}", key, result);
            return result;
        });
    }

    /**
     * 判断键是否存在
     */
    public Boolean exists(String key) {
        return executeWithRetry(() -> {
            Boolean result = syncCommands.exists(key) > 0;
            log.debug("Redis EXISTS: key={}, result={}", key, result);
            return result;
        });
    }

    /**
     * 设置过期时间
     */
    public Boolean expire(String key, long seconds) {
        return executeWithRetry(() -> {
            Boolean result = syncCommands.expire(key, seconds);
            log.debug("Redis EXPIRE: key={}, seconds={}, result={}", key, seconds, result);
            return result;
        });
    }

    /**
     * 获取键的剩余过期时间
     *
     * @param key 键
     * @return 剩余秒数，-1表示键存在但无过期时间，-2表示键不存在
     */
    public Long ttl(String key) {
        return executeWithRetry(() -> {
            Long result = syncCommands.ttl(key);
            log.debug("Redis TTL: key={}, ttl={}", key, result);
            return result;
        });
    }

    /**
     * 原子递增
     *
     * @param key 键
     * @return 递增后的值
     */
    public Long incr(String key) {
        return executeWithRetry(() -> {
            Long result = syncCommands.incr(key);
            log.debug("Redis INCR: key={}, result={}", key, result);
            return result;
        });
    }

    /**
     * ==================== Hash 操作 ====================
     */

    /**
     * 设置 Hash 字段
     */
    public void hset(String key, String field, String value) {
        syncCommands.hset(key, field, value);
        log.debug("Redis HSET: key={}, field={}, value={}", key, field, value);
    }

    /**
     * 获取 Hash 字段
     */
    public String hget(String key, String field) {
        String value = syncCommands.hget(key, field);
        log.debug("Redis HGET: key={}, field={}, value={}", key, field, value);
        return value;
    }

    /**
     * 获取 Hash 所有字段
     */
    public java.util.Map<String, String> hgetall(String key) {
        java.util.Map<String, String> map = syncCommands.hgetall(key);
        log.debug("Redis HGETALL: key={}, size={}", key, map.size());
        return map;
    }

    /**
     * 删除 Hash 字段
     */
    public Long hdel(String key, String... fields) {
        Long result = syncCommands.hdel(key, fields);
        log.debug("Redis HDEL: key={}, fields={}, result={}", key, fields, result);
        return result;
    }

    /**
     * ==================== Set 操作 ====================
     */

    /**
     * 向 Set 添加元素
     */
    public Long sadd(String key, String... members) {
        return executeWithRetry(() -> {
            Long result = syncCommands.sadd(key, members);
            log.debug("Redis SADD: key={}, members={}, result={}", key, members, result);
            return result;
        });
    }

    /**
     * 获取 Set 所有元素
     */
    public java.util.Set<String> smembers(String key) {
        return executeWithRetry(() -> {
            java.util.Set<String> members = syncCommands.smembers(key);
            log.debug("Redis SMEMBERS: key={}, size={}", key, members.size());
            return members;
        });
    }

    /**
     * 移除 Set 元素
     */
    public Long srem(String key, String... members) {
        return executeWithRetry(() -> {
            Long result = syncCommands.srem(key, members);
            log.debug("Redis SREM: key={}, members={}, result={}", key, members, result);
            return result;
        });
    }

    /**
     * 判断元素是否在 Set 中
     */
    public Boolean sismember(String key, String member) {
        return executeWithRetry(() -> {
            Boolean result = syncCommands.sismember(key, member);
            log.debug("Redis SISMEMBER: key={}, member={}, result={}", key, member, result);
            return result;
        });
    }

    /**
     * ==================== List 操作 ====================
     */

    /**
     * 从左侧推入元素
     */
    public Long lpush(String key, String... values) {
        Long result = syncCommands.lpush(key, values);
        log.debug("Redis LPUSH: key={}, values={}, result={}", key, values, result);
        return result;
    }

    /**
     * 从右侧推入元素
     */
    public Long rpush(String key, String... values) {
        Long result = syncCommands.rpush(key, values);
        log.debug("Redis RPUSH: key={}, values={}, result={}", key, values, result);
        return result;
    }

    /**
     * 从左侧弹出元素
     */
    public String lpop(String key) {
        String value = syncCommands.lpop(key);
        log.debug("Redis LPOP: key={}, value={}", key, value);
        return value;
    }

    /**
     * 从右侧弹出元素
     */
    public String rpop(String key) {
        String value = syncCommands.rpop(key);
        log.debug("Redis RPOP: key={}, value={}", key, value);
        return value;
    }

    /**
     * 获取列表长度
     */
    public Long llen(String key) {
        Long length = syncCommands.llen(key);
        log.debug("Redis LLEN: key={}, length={}", key, length);
        return length;
    }

    /**
     * ==================== 其他操作 ====================
     */

    /**
     * Ping 测试连接
     */
    public String ping() {
        String result = syncCommands.ping();
        log.debug("Redis PING: result={}", result);
        return result;
    }

    /**
     * 选择数据库
     */
    public void select(int database) {
        syncCommands.select(database);
        log.debug("Redis SELECT: database={}", database);
    }

    /**
     * ==================== 增强功能 ====================
     */

    /**
     * 带重试的操作执行
     *
     * @param supplier 操作
     * @param <T>      返回类型
     * @return 操作结果
     */
    private <T> T executeWithRetry(Supplier<T> supplier) {
        int attempts = 0;
        RedisException lastException = null;

        while (attempts < MAX_RETRY_ATTEMPTS) {
            try {
                return supplier.get();
            } catch (RedisException e) {
                lastException = e;
                attempts++;
                operationFailureCount.incrementAndGet();

                if (attempts < MAX_RETRY_ATTEMPTS) {
                    connectionRetryCount.incrementAndGet();
                    log.warn("Redis 操作失败，第 {} 次重试: {}", attempts, e.getMessage());

                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RedisException("重试被中断", ie);
                    }
                }
            }
        }

        log.error("Redis 操作失败，已达到最大重试次数: {}", MAX_RETRY_ATTEMPTS);
        throw lastException;
    }

    /**
     * 健康检查
     *
     * @return true-健康, false-不健康
     */
    public boolean healthCheck() {
        try {
            String result = syncCommands.ping();
            lastHealthCheckTime = System.currentTimeMillis();
            isHealthy = "PONG".equalsIgnoreCase(result);

            if (!isHealthy) {
                log.warn("Redis 健康检查失败: PING 返回 {}", result);
            } else {
                log.debug("Redis 健康检查通过");
            }

            return isHealthy;
        } catch (Exception e) {
            lastHealthCheckTime = System.currentTimeMillis();
            isHealthy = false;
            log.error("Redis 健康检查异常: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 获取连接统计信息
     *
     * @return 统计信息JSON字符串
     */
    public String getConnectionStats() {
        return String.format(
            "{\"retryCount\":%d,\"failureCount\":%d,\"isHealthy\":%s,\"lastHealthCheck\":%d}",
            connectionRetryCount.get(),
            operationFailureCount.get(),
            isHealthy,
            lastHealthCheckTime
        );
    }

    /**
     * 获取重试次数
     *
     * @return 重试次数
     */
    public int getConnectionRetryCount() {
        return connectionRetryCount.get();
    }

    /**
     * 获取操作失败次数
     *
     * @return 失败次数
     */
    public int getOperationFailureCount() {
        return operationFailureCount.get();
    }

    /**
     * 是否健康
     *
     * @return true-健康, false-不健康
     */
    public boolean isHealthy() {
        return isHealthy;
    }

    /**
     * 获取最后一次健康检查时间
     *
     * @return 时间戳（毫秒）
     */
    public long getLastHealthCheckTime() {
        return lastHealthCheckTime;
    }

    /**
     * 重置统计信息
     */
    public void resetStats() {
        connectionRetryCount.set(0);
        operationFailureCount.set(0);
        log.info("Redis 统计信息已重置");
    }

    /**
     * ==================== 基础操作（带重试） ====================
     */

    @Override
    public String toString() {
        return "RedisManager{" +
            "healthy=" + isHealthy +
            ", retries=" + connectionRetryCount.get() +
            ", failures=" + operationFailureCount.get() +
            '}';
    }

    /**
     * 关闭连接
     */
    public void close() {
        if (connection != null) {
            connection.close();
            log.info("Redis 连接已关闭");
        }
        if (redisClient != null) {
            redisClient.shutdown();
            log.info("Redis Client 已关闭");
        }
    }
}
