package com.ticket.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 内存缓存工具类（替代 Redis）
 * 使用 ConcurrentHashMap + 定时清理过期数据
 */
public class CacheUtil {

    /**
     * 缓存存储
     */
    private static final Map<String, CacheItem> cache = new ConcurrentHashMap<>();

    /**
     * 定时清理线程池
     */
    private static final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

    /**
     * 默认过期时间（30分钟）
     */
    private static final long DEFAULT_TTL = 30 * 60 * 1000;

    static {
        // 每 5 分钟清理一次过期数据
        executor.scheduleAtFixedRate(CacheUtil::cleanExpired, 5, 5, TimeUnit.MINUTES);
    }

    /**
     * 设置缓存（默认过期时间）
     */
    public static void set(String key, Object value) {
        set(key, value, DEFAULT_TTL);
    }

    /**
     * 设置缓存（自定义过期时间，毫秒）
     */
    public static void set(String key, Object value, long ttl) {
        if (key == null || value == null) {
            return;
        }
        long expireTime = System.currentTimeMillis() + ttl;
        cache.put(key, new CacheItem(value, expireTime));
    }

    /**
     * 获取缓存
     */
    @SuppressWarnings("unchecked")
    public static <T> T get(String key) {
        if (key == null) {
            return null;
        }
        CacheItem item = cache.get(key);
        if (item == null) {
            return null;
        }
        // 检查是否过期
        if (item.isExpired()) {
            cache.remove(key);
            return null;
        }
        return (T) item.getValue();
    }

    /**
     * 删除缓存
     */
    public static void delete(String key) {
        if (key != null) {
            cache.remove(key);
        }
    }

    /**
     * 判断缓存是否存在
     */
    public static boolean exists(String key) {
        if (key == null) {
            return false;
        }
        CacheItem item = cache.get(key);
        if (item == null) {
            return false;
        }
        if (item.isExpired()) {
            cache.remove(key);
            return false;
        }
        return true;
    }

    /**
     * 清空所有缓存
     */
    public static void clear() {
        cache.clear();
    }

    /**
     * 获取缓存大小
     */
    public static int size() {
        return cache.size();
    }

    /**
     * 清理过期数据
     */
    private static void cleanExpired() {
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    /**
     * 缓存项
     */
    private static class CacheItem {
        private final Object value;
        private final long expireTime;

        public CacheItem(Object value, long expireTime) {
            this.value = value;
            this.expireTime = expireTime;
        }

        public Object getValue() {
            return value;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expireTime;
        }
    }
}