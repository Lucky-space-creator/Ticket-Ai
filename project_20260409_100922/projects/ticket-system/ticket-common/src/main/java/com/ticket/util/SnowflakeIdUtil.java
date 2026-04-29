package com.ticket.util;

/**
 * 雪花算法 ID 生成工具类
 *
 * 分布式唯一ID算法，结构：
 * 1位符号位 + 41位时间戳 + 10位数据中心和机器ID + 12位序列号
 *
 * @author ticket-system
 */
public class SnowflakeIdUtil {

    /**
     * 开始时间戳 (2024-01-01 00:00:00)
     */
    private static final long START_TIMESTAMP = 1704038400000L;

    /**
     * 数据中心 ID 所占的位数
     */
    private static final long DATA_CENTER_ID_BITS = 5L;

    /**
     * 机器 ID 所占的位数
     */
    private static final long WORKER_ID_BITS = 5L;

    /**
     * 序列号所占的位数
     */
    private static final long SEQUENCE_BITS = 12L;

    /**
     * 数据中心 ID 最大值 (31)
     */
    private static final long MAX_DATA_CENTER_ID = ~(-1L << DATA_CENTER_ID_BITS);

    /**
     * 机器 ID 最大值 (31)
     */
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);

    /**
     * 序列号最大值 (4095)
     */
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);

    /**
     * 机器 ID 左移位数
     */
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;

    /**
     * 数据中心 ID 左移位数
     */
    private static final long DATA_CENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;

    /**
     * 时间戳左移位数
     */
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATA_CENTER_ID_BITS;

    /**
     * 数据中心 ID (0-31)
     */
    private final long dataCenterId;

    /**
     * 机器 ID (0-31)
     */
    private final long workerId;

    /**
     * 序列号
     */
    private long sequence = 0L;

    /**
     * 上一次生成 ID 时的时间戳
     */
    private long lastTimestamp = -1L;

    /**
     * 单例实例
     */
    private static volatile SnowflakeIdUtil instance;

    /**
     * 默认构造函数，使用 dataCenterId=1, workerId=1
     */
    public SnowflakeIdUtil() {
        this(1, 1);
    }

    /**
     * 构造函数
     *
     * @param dataCenterId 数据中心 ID (0-31)
     * @param workerId     机器 ID (0-31)
     */
    public SnowflakeIdUtil(long dataCenterId, long workerId) {
        if (dataCenterId > MAX_DATA_CENTER_ID || dataCenterId < 0) {
            throw new IllegalArgumentException("DataCenter ID 不能大于 " + MAX_DATA_CENTER_ID + " 或小于 0");
        }
        if (workerId > MAX_WORKER_ID || workerId < 0) {
            throw new IllegalArgumentException("Worker ID 不能大于 " + MAX_WORKER_ID + " 或小于 0");
        }
        this.dataCenterId = dataCenterId;
        this.workerId = workerId;
    }

    /**
     * 获取单例实例 (默认 dataCenterId=1, workerId=1)
     */
    public static SnowflakeIdUtil getInstance() {
        if (instance == null) {
            synchronized (SnowflakeIdUtil.class) {
                if (instance == null) {
                    instance = new SnowflakeIdUtil(1, 1);
                }
            }
        }
        return instance;
    }

    /**
     * 获取单例实例
     *
     * @param dataCenterId 数据中心 ID (0-31)
     * @param workerId     机器 ID (0-31)
     */
    public static SnowflakeIdUtil getInstance(long dataCenterId, long workerId) {
        if (instance == null) {
            synchronized (SnowflakeIdUtil.class) {
                if (instance == null) {
                    instance = new SnowflakeIdUtil(dataCenterId, workerId);
                }
            }
        }
        return instance;
    }

    /**
     * 生成下一个 ID
     *
     * @return 雪花算法生成的 ID
     */
    public synchronized long nextId() {
        long timestamp = currentTimeMillis();

        // 如果当前时间小于上一次生成 ID 的时间，说明系统时钟回退
        if (timestamp < lastTimestamp) {
            throw new RuntimeException("系统时钟回退，拒绝生成 ID");
        }

        // 如果是同一时间生成的，则序列号自增
        if (lastTimestamp == timestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            // 如果序列号达到最大值，则等待下一毫秒
            if (sequence == 0) {
                timestamp = waitNextMillis(timestamp);
            }
        } else {
            // 时间改变，序列号重置
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        // 使用雪花算法计算 ID
        return ((timestamp - START_TIMESTAMP) << TIMESTAMP_SHIFT)
                | (dataCenterId << DATA_CENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    /**
     * 生成下一个 ID 字符串
     *
     * @return 雪花算法生成的 ID 字符串
     */
    public String nextIdStr() {
        return String.valueOf(nextId());
    }

    /**
     * 等待下一毫秒
     */
    private long waitNextMillis(long timestamp) {
        while (timestamp <= lastTimestamp) {
            timestamp = currentTimeMillis();
        }
        return timestamp;
    }

    /**
     * 获取当前时间戳
     */
    private long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    /**
     * 解析 ID 的组成信息
     *
     * @param id 雪花算法 ID
     * @return 包含 ID 组成信息的字符串
     */
    public static String parseId(long id) {
        long timestamp = (id >> TIMESTAMP_SHIFT) + START_TIMESTAMP;
        long dataCenterId = (id >> DATA_CENTER_ID_SHIFT) & MAX_DATA_CENTER_ID;
        long workerId = (id >> WORKER_ID_SHIFT) & MAX_WORKER_ID;
        long sequence = id & MAX_SEQUENCE;

        return String.format(
                "时间戳: %d, 数据中心: %d, 机器ID: %d, 序列号: %d",
                timestamp, dataCenterId, workerId, sequence
        );
    }
}