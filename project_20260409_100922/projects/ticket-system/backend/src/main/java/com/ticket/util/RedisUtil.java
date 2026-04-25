package com.ticket.util;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import jakarta.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis 缓存工具类
 */
@Component
public class RedisUtil {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 默认过期时间（30分钟）
     */
    private static final long DEFAULT_TTL = 30 * 60;

    /**
     * 设置缓存（默认过期时间）
     */
    public void set(String key, Object value) {
        set(key, value, DEFAULT_TTL);
    }

    /**
     * 设置缓存（自定义过期时间，秒）
     */
    public void set(String key, Object value, long ttl) {
        if (key == null || value == null) {
            return;
        }
        redisTemplate.opsForValue().set(key, value, ttl, TimeUnit.SECONDS);
    }

    /**
     * 设置缓存（自定义过期时间，指定时间单位）
     */
    public void set(String key, Object value, long ttl, TimeUnit unit) {
        if (key == null || value == null) {
            return;
        }
        redisTemplate.opsForValue().set(key, value, ttl, unit);
    }

    /**
     * 获取缓存
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        if (key == null) {
            return null;
        }
        return (T) redisTemplate.opsForValue().get(key);
    }

    /**
     * 删除缓存
     */
    public Boolean delete(String key) {
        if (key == null) {
            return false;
        }
        return redisTemplate.delete(key);
    }

    /**
     * 批量删除缓存
     */
    public Long delete(Set<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return 0L;
        }
        return redisTemplate.delete(keys);
    }

    /**
     * 删除匹配的所有缓存（支持通配符）
     */
    public Long deleteByPattern(String pattern) {
        if (pattern == null) {
            return 0L;
        }
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys == null || keys.isEmpty()) {
            return 0L;
        }
        return redisTemplate.delete(keys);
    }

    /**
     * 判断缓存是否存在
     */
    public Boolean exists(String key) {
        if (key == null) {
            return false;
        }
        return redisTemplate.hasKey(key);
    }

    /**
     * 设置过期时间
     */
    public Boolean expire(String key, long ttl) {
        if (key == null) {
            return false;
        }
        return redisTemplate.expire(key, ttl, TimeUnit.SECONDS);
    }

    /**
     * 获取剩余过期时间（秒）
     */
    public Long getExpire(String key) {
        if (key == null) {
            return -1L;
        }
        return redisTemplate.getExpire(key, TimeUnit.SECONDS);
    }

    /**
     * 自增（原子操作）
     */
    public Long increment(String key) {
        if (key == null) {
            return null;
        }
        return redisTemplate.opsForValue().increment(key);
    }

    /**
     * 自增（指定步长）
     */
    public Long increment(String key, long delta) {
        if (key == null) {
            return null;
        }
        return redisTemplate.opsForValue().increment(key, delta);
    }

    /**
     * 自减（原子操作）
     */
    public Long decrement(String key) {
        if (key == null) {
            return null;
        }
        return redisTemplate.opsForValue().decrement(key);
    }

    /**
     * 自减（指定步长）
     */
    public Long decrement(String key, long delta) {
        if (key == null) {
            return null;
        }
        return redisTemplate.opsForValue().decrement(key, delta);
    }

    // ==================== Hash 操作 ====================

    /**
     * Hash 设置值
     */
    public void hashPut(String key, String hashKey, Object value) {
        if (key == null || hashKey == null || value == null) {
            return;
        }
        redisTemplate.opsForHash().put(key, hashKey, value);
    }

    /**
     * Hash 获取值
     */
    @SuppressWarnings("unchecked")
    public <T> T hashGet(String key, String hashKey) {
        if (key == null || hashKey == null) {
            return null;
        }
        return (T) redisTemplate.opsForHash().get(key, hashKey);
    }

    /**
     * Hash 删除
     */
    public Long hashDelete(String key, Object... hashKeys) {
        if (key == null) {
            return 0L;
        }
        return redisTemplate.opsForHash().delete(key, hashKeys);
    }

    /**
     * Hash 判断是否存在
     */
    public Boolean hashHasKey(String key, String hashKey) {
        if (key == null || hashKey == null) {
            return false;
        }
        return redisTemplate.opsForHash().hasKey(key, hashKey);
    }

    // ==================== Set 操作 ====================

    /**
     * Set 添加
     */
    public Long setAdd(String key, Object... values) {
        if (key == null || values == null || values.length == 0) {
            return 0L;
        }
        return redisTemplate.opsForSet().add(key, values);
    }

    /**
     * Set 获取所有成员
     */
    public Set<Object> setMembers(String key) {
        if (key == null) {
            return null;
        }
        return redisTemplate.opsForSet().members(key);
    }

    /**
     * Set 判断是否是成员
     */
    public Boolean setIsMember(String key, Object value) {
        if (key == null || value == null) {
            return false;
        }
        return redisTemplate.opsForSet().isMember(key, value);
    }

    /**
     * Set 删除成员
     */
    public Long setRemove(String key, Object... values) {
        if (key == null || values == null || values.length == 0) {
            return 0L;
        }
        return redisTemplate.opsForSet().remove(key, values);
    }

    // ==================== 工具方法 ====================

    /**
     * 清空所有缓存（慎用！）
     */
    public void flushAll() {
        Set<String> keys = redisTemplate.keys("*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    /**
     * 获取缓存数量
     */
    public Long size() {
        Set<String> keys = redisTemplate.keys("*");
        return keys == null ? 0L : (long) keys.size();
    }

    /**
     * 判断 Redis 是否连接
     */
    public Boolean isConnected() {
        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== Lua 脚本执行 ====================

    /**
     * 执行 Lua 脚本
     * @param script Lua 脚本
     * @param keys 键列表
     * @param args 参数列表
     * @return 脚本执行结果
     */
    public Object executeScript(String script, List<String> keys, Object[] args) {
        if (script == null || script.isEmpty()) {
            return null;
        }
        DefaultRedisScript<Object> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(script);
        redisScript.setResultType(Object.class);
        return redisTemplate.execute(redisScript, keys, args);
    }

    /**
     * 执行 Lua 脚本（从资源文件加载）
     * @param scriptPath 脚本资源路径（classpath相对路径）
     * @param keys 键列表
     * @param args 参数列表
     * @return 脚本执行结果
     */
    public Object executeScriptFromResource(String scriptPath, List<String> keys, Object[] args) {
        try {
            ClassPathResource resource = new ClassPathResource(scriptPath);
            if (!resource.exists()) {
                throw new RuntimeException("Lua脚本文件不存在: " + scriptPath);
            }
            String script = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            return executeScript(script, keys, args);
        } catch (Exception e) {
            throw new RuntimeException("加载Lua脚本失败: " + scriptPath, e);
        }
    }
}
