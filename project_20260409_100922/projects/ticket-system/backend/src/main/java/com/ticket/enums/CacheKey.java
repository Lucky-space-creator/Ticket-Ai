package com.ticket.enums;

/**
 * 缓存键常量
 */
public class CacheKey {

    /**
     * 用户 Token 缓存（7天）
     * 格式: user:token:{userId}
     */
    public static final String USER_TOKEN = "user:token:%s";

    /**
     * 用户信息缓存（30分钟）
     * 格式: user:info:{userId}
     */
    public static final String USER_INFO = "user:info:%s";

    /**
     * 车次搜索缓存（30分钟）
     * 格式: train:search:{startStation}:{endStation}:{trainDate}
     */
    public static final String TRAIN_SEARCH = "train:search:%s:%s:%s";

    /**
     * 车次余票缓存（10分钟）
     * 格式: train:stock:{trainId}:{trainDate}:{seatType}
     */
    public static final String TRAIN_STOCK = "train:stock:%d:%s:%d";

    /**
     * 车次详情缓存（1小时）
     * 格式: train:detail:{trainId}
     */
    public static final String TRAIN_DETAIL = "train:detail:%d";

    /**
     * 车次详情缓存（按车次号）（1小时）
     * 格式: train:detail:no:{trainNo}
     */
    public static final String TRAIN_DETAIL_NO = "train:detail:no:%s";

    /**
     * 订单缓存（30分钟）
     * 格式: order:info:{orderId}
     */
    public static final String ORDER_INFO = "order:info:%s";

    /**
     * 用户订单列表缓存（10分钟）
     * 格式: user:orders:{userId}
     */
    public static final String USER_ORDERS = "user:orders:%d";

    /**
     * 常用联系人缓存（1小时）
     * 格式: user:passengers:{userId}
     */
    public static final String USER_PASSENGERS = "user:passengers:%d";

    /**
     * 车次信息缓存（1小时）
     * 格式: train:info:{trainNo}
     */
    public static final String TRAIN_INFO = "train:info:%s";

    /**
     * 知识库缓存（1天）
     * 格式: knowledge:all
     */
    public static final String KNOWLEDGE_ALL = "knowledge:all";

    /**
     * 热门问题缓存（1天）
     * 格式: knowledge:hot
     */
    public static final String KNOWLEDGE_HOT = "knowledge:hot";

    /**
     * 站点信息缓存（1小时）
     * 格式: station:info:{stationId}
     */
    public static final String STATION_INFO = "station:info:%d";

    /**
     * 所有站点列表缓存（1小时）
     * 格式: station:list:all
     */
    public static final String STATION_LIST_ALL = "station:list:all";

    /**
     * 站点名称搜索缓存（30分钟）
     * 格式: station:search:{keyword}
     */
    public static final String STATION_SEARCH = "station:search:%s";

    /**
     * 车次站点缓存（1小时）
     * 格式: train:stations:{trainId}
     */
    public static final String TRAIN_STATIONS = "train:stations:%d";
}
