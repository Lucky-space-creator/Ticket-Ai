package com.ticket.enums;

import com.ticket.util.StationNameUtil;

import java.util.Objects;

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
     * 车次搜索缓存（分钟级 TTL，计划：接受短暂脏读）
     * 格式: train:search:{startStation}:{endStation}:{trainDate}
     */
    public static final String TRAIN_SEARCH = "train:search:%s:%s:%s";

    /**
     * 车次余票缓存（10分钟）
     * 格式: train:stock:{线段id}:{trainDate}:{seatType}:{startStation}-{endStation}
     */
    public static final String TRAIN_STOCK = "train:stock:%d:%s:%d:%s-%s";

    /**
     * 同车次号当日各线段余票列表缓存
     * 格式: train:stocks:list:{trainNo}:{trainDate}
     */
    public static final String TRAIN_STOCKS_LIST = "train:stocks:list:%s:%s";

    /**
     * 车次预占库存缓存（30分钟）
     */
    public static final String TRAIN_LOCKED = "train:locked:%d:%s:%d:%s:%s";

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
     * @deprecated 线段化后请使用 {@link #TRAIN_ROUTE_STOPS}
     */
    @Deprecated
    public static final String TRAIN_STATIONS = "train:stations:%d";

    /**
     * 某车次号全程停靠序缓存
     */
    public static final String TRAIN_ROUTE_STOPS = "train:route_stops:%s";

    /**
     * 用户权限缓存（10分钟）
     * 格式: user:permissions:{userId}
     */
    public static final String USER_PERMISSIONS = "user:permissions:%d";

    /**
     * 格式化车次余票缓存键，处理null值
     */
    public static String formatTrainStockKey(Long trainId, String trainDate, Integer seatType, String startStation, String endStation) {
        Objects.requireNonNull(trainId, "trainId不能为null");
        Objects.requireNonNull(seatType, "seatType不能为null");
        String from = startStation != null ? StationNameUtil.normalize(startStation) : "";
        String to = endStation != null ? StationNameUtil.normalize(endStation) : "";
        return String.format(TRAIN_STOCK,
                trainId,
                trainDate != null ? trainDate : "",
                seatType,
                from, to);
    }

    /**
     * 格式化车次预占库存缓存键，处理null值
     */
    public static String formatTrainLockedKey(Long trainId, String trainDate, Integer seatType, String startStation, String endStation) {
        Objects.requireNonNull(trainId, "trainId不能为null");
        Objects.requireNonNull(seatType, "seatType不能为null");
        String from = startStation != null ? StationNameUtil.normalize(startStation) : "";
        String to = endStation != null ? StationNameUtil.normalize(endStation) : "";
        return String.format(TRAIN_LOCKED,
                trainId,
                trainDate != null ? trainDate : "",
                seatType,
                from, to);
    }

    public static String formatTrainStocksListKey(String trainNo, String trainDate) {
        String no = trainNo != null ? trainNo : "";
        String d = trainDate != null ? trainDate : "";
        return String.format(TRAIN_STOCKS_LIST, no, d);
    }

    public static String formatTrainRouteStopsKey(String trainNo) {
        return String.format(TRAIN_ROUTE_STOPS, trainNo != null ? trainNo : "");
    }
}