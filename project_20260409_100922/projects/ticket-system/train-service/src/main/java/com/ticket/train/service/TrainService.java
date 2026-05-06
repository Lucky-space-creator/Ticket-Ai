package com.ticket.train.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ticket.dto.internal.TrainStockCommand;
import com.ticket.dto.train.RouteSearchOption;
import com.ticket.entity.TicketStock;
import com.ticket.entity.Train;

import java.math.BigDecimal;
import java.util.List;

/**
 * 车次服务接口
 */
public interface TrainService extends IService<Train> {

    /**
     * 搜索车次（直筒/线段 LIKE 匹配，短期保留）
     *
     * @deprecated 请改用 {@link #searchRoutes}; 前端迁完后移除此接口。
     */
    @Deprecated
    List<Train> searchTrains(String startStation, String endStation, String trainDate);

    /**
     * 联程/直达方案检索（同车多段 + 换乘），余量为各段最小值。
     */
    List<RouteSearchOption> searchRoutes(String startStation, String endStation, String trainDate, Integer seatType);

    /**
     * 全部站点名称（来自 station 表，升序），供购票页下拉选择。
     */
    List<String> listStationNames();

    /**
     * 获取车次详情（按车次号）
     * @param trainNo 车次号（如 "G1234"）
     */
    Train getTrainDetail(String trainNo);

    /**
     * 获取车次详情（按车次ID）- 内部使用
     */
    Train getTrainDetailById(Long trainId);

    /**
     * 获取余票信息
     */
    List<TicketStock> getTicketStocks(Long trainId, String trainDate);

    /**
     * 获取指定席别的票价
     */
    BigDecimal getSeatPrice(Long trainId, String trainDate, String startStation, String endStation, Integer seatType);

    /**
     * 检查并扣减库存
     */
    boolean deductStock(Long trainId, String trainDate, String startStation, String endStation, Integer seatType, Integer count);

    /**
     * 联程原子预扣 + 校验每段数据库余票记录存在（与 deductStock 一致，多段用单笔 Lua）。
     */
    boolean deductStocksBatch(List<TrainStockCommand> segments);

    /**
     * 联程 Redis 释占（单笔 Lua）并对每段回填 MySQL 可用席（与 rollbackStock 一致）。
     */
    boolean rollbackStocksBatch(List<TrainStockCommand> segments);
    /**
     * 回滚库存
     */
    boolean rollbackStock(Long trainId, String trainDate, String startStation, String endStation, Integer seatType, Integer count);

    /**
     * 初始化库存到Redis（系统启动或数据变更时调用）
     */
    void initStockToRedis(Long trainId, String trainDate, Integer seatType, String startStation, String endStation, int stock);
}