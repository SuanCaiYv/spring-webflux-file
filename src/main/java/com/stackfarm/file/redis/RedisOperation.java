package com.stackfarm.file.redis;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * @author Mr.M
 */
@Component
public class RedisOperation {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    public boolean setValue(String key, String value) {
        redisTemplate.opsForValue().set(key, value);
        return true;
    }

    public boolean setValue(String key, String value, long timeoutMs) {
        redisTemplate.opsForValue().set(key, value, timeoutMs, TimeUnit.MILLISECONDS);
        return true;
    }

    public boolean setKey(String key) {
        return setValue(key, "no-value");
    }

    public boolean setKey(String key, long timeoutMs) {
        return setValue(key, "no-value", timeoutMs);
    }

    public boolean getKey(String key) {
        String value = redisTemplate.opsForValue().get(key);
        return value != null && !"null".equals(value);
    }

    public String getValue(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public boolean deleteKey(String key) {
        redisTemplate.delete(key);
        return true;
    }

    public boolean deleteByKey(String key) {
        redisTemplate.delete(key);
        return true;
    }

    public boolean deleteByPattern(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        assert keys != null;
        keys.forEach(k -> {
            redisTemplate.delete(k);
        });
        return true;
    }

    public boolean flushAll() {
        Set<String> keys = redisTemplate.keys("*");
        assert keys != null;
        keys.forEach(k -> {
            redisTemplate.delete(k);
        });
        return true;
    }
}
