package com.mic.common.util;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Redis操作工具（字符串值场景：图形验证码、计数、简单缓存）
 */
@Component
@RequiredArgsConstructor
public class RedisUtil {

    private final StringRedisTemplate stringRedisTemplate;

    /** 设置缓存（不过期） */
    public void set(String key, String value) {
        stringRedisTemplate.opsForValue().set(key, value);
    }

    /** 设置缓存并指定过期时间 */
    public void setEx(String key, String value, long timeout, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    /** 获取缓存值 */
    public String get(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    /**
     * 获取并删除（一次性消费场景，如验证码校验）
     * 兼容 Redis 3.2：GETDEL 命令需 Redis 6.2+，此处用 GET + DEL 两步实现
     */
    public String getAndDelete(String key) {
        String value = stringRedisTemplate.opsForValue().get(key);
        if (value != null) {
            stringRedisTemplate.delete(key);
        }
        return value;
    }

    /** 删除缓存 */
    public boolean delete(String key) {
        return Boolean.TRUE.equals(stringRedisTemplate.delete(key));
    }

    /** 判断key是否存在 */
    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
    }

    /** 设置key过期时间 */
    public boolean expire(String key, long timeout, TimeUnit unit) {
        return Boolean.TRUE.equals(stringRedisTemplate.expire(key, timeout, unit));
    }
}
