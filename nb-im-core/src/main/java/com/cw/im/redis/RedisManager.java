package com.cw.im.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.codec.StringCodec;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

/**
 * Redis 管理器 - 最小版本
 *
 * @author cw
 */
@Slf4j
public class RedisManager {

    // Static initializer to set system property BEFORE Lettuce classes load
    static {
        System.setProperty("io.netty.noNative", "true");
    }

    private static final String DEFAULT_HOST = "192.168.215.3";
    private static final int DEFAULT_PORT = 6379;
    private static final String DEFAULT_PASSWORD = null;
    private static final int DEFAULT_DATABASE = 0;

    private RedisClient redisClient;
    private StatefulRedisConnection<String, String> connection;
    private RedisCommands<String, String> syncCommands;

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

        log.info("Redis 连接初始化成功: {}:{}", host, port);
    }

    /**
     * ==================== String 操作 ====================
     */

    /**
     * 设置键值对
     */
    public void set(String key, String value) {
        syncCommands.set(key, value);
        log.debug("Redis SET: key={}, value={}", key, value);
    }

    /**
     * 设置键值对（带过期时间）
     */
    public void setex(String key, long seconds, String value) {
        syncCommands.setex(key, seconds, value);
        log.debug("Redis SETEX: key={}, seconds={}, value={}", key, seconds, value);
    }

    /**
     * 获取值
     */
    public String get(String key) {
        String value = syncCommands.get(key);
        log.debug("Redis GET: key={}, value={}", key, value);
        return value;
    }

    /**
     * 删除键
     */
    public Long del(String key) {
        Long result = syncCommands.del(key);
        log.debug("Redis DEL: key={}, result={}", key, result);
        return result;
    }

    /**
     * 判断键是否存在
     */
    public Boolean exists(String key) {
        Boolean result = syncCommands.exists(key) > 0;
        log.debug("Redis EXISTS: key={}, result={}", key, result);
        return result;
    }

    /**
     * 设置过期时间
     */
    public Boolean expire(String key, long seconds) {
        Boolean result = syncCommands.expire(key, seconds);
        log.debug("Redis EXPIRE: key={}, seconds={}, result={}", key, seconds, result);
        return result;
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
        Long result = syncCommands.sadd(key, members);
        log.debug("Redis SADD: key={}, members={}, result={}", key, members, result);
        return result;
    }

    /**
     * 获取 Set 所有元素
     */
    public java.util.Set<String> smembers(String key) {
        java.util.Set<String> members = syncCommands.smembers(key);
        log.debug("Redis SMEMBERS: key={}, size={}", key, members.size());
        return members;
    }

    /**
     * 移除 Set 元素
     */
    public Long srem(String key, String... members) {
        Long result = syncCommands.srem(key, members);
        log.debug("Redis SREM: key={}, members={}, result={}", key, members, result);
        return result;
    }

    /**
     * 判断元素是否在 Set 中
     */
    public Boolean sismember(String key, String member) {
        Boolean result = syncCommands.sismember(key, member);
        log.debug("Redis SISMEMBER: key={}, member={}, result={}", key, member, result);
        return result;
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
