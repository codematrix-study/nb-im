package com.cw.im.redis;

/**
 * Redis 测试类 - 演示如何使用
 *
 * @author cw
 */
public class RedisTest {

    public static void main(String[] args) {
        // 创建 Redis 管理器（使用默认配置）
        RedisManager redis = new RedisManager();

        // 测试连接
        String pong = redis.ping();
        System.out.println("Ping: " + pong);

        // ==================== String 操作示例 ====================
        redis.set("user:1001:name", "张三");
        redis.set("user:1001:age", "25");

        String name = redis.get("user:1001:name");
        System.out.println("用户名: " + name);

        // 设置过期时间（10秒）
        redis.setex("user:1001:token", 10, "abc123xyz");

        // ==================== Hash 操作示例 ====================
        redis.hset("user:1001", "name", "李四");
        redis.hset("user:1001", "age", "30");
        redis.hset("user:1001", "city", "北京");

        String userName = redis.hget("user:1001", "name");
        System.out.println("Hash 用户名: " + userName);

        // ==================== Set 操作示例 ====================
        redis.sadd("user:1001:channels", "channel-1", "channel-2", "channel-3");

        Boolean isMember = redis.sismember("user:1001:channels", "channel-1");
        System.out.println("channel-1 是否存在: " + isMember);

        // ==================== List 操作示例 ====================
        redis.lpush("im:messages:1001:1002", "消息3", "消息2", "消息1");

        String message = redis.rpop("im:messages:1001:1002");
        System.out.println("弹出的消息: " + message);

        // 关闭连接
        redis.close();
    }
}
