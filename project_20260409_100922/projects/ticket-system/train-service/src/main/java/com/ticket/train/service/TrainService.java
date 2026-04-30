package com.ticket.train.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ticket.entity.TicketStock;
import com.ticket.entity.Train;

import java.math.BigDecimal;
import java.util.List;

/**
 * 车次服务接口
 */
public interface TrainService extends IService<Train> {

    /**
     * 搜索车次
     */
    List<Train> searchTrains(String startStation, String endStation, String trainDate);

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
     * 回滚库存
     */
    boolean rollbackStock(Long trainId, String trainDate, String startStation, String endStation, Integer seatType, Integer count);

    /**
     * 初始化库存到Redis（系统启动或数据变更时调用）
     */
    void initStockToRedis(Long trainId, String trainDate, Integer seatType, String startStation, String endStation, int stock);
}