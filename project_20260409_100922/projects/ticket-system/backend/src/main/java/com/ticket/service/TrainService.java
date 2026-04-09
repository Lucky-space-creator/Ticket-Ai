package com.ticket.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ticket.entity.TicketStock;
import com.ticket.entity.Train;

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
     * 获取车次详情
     */
    Train getTrainDetail(Long trainId);

    /**
     * 获取余票信息
     */
    List<TicketStock> getTicketStocks(Long trainId, String trainDate);

    /**
     * 检查并扣减库存
     */
    boolean deductStock(Long trainId, String trainDate, String startStation, String endStation, Integer seatType, Integer count);

    /**
     * 回滚库存
     */
    boolean rollbackStock(Long trainId, String trainDate, String startStation, String endStation, Integer seatType, Integer count);
}
