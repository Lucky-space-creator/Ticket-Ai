package com.ticket.service;

import com.ticket.dto.internal.TrainStockCommand;

import java.util.List;

/**
 * 库存锁服务 - 基于 Redis 的原子库存操作（与 backend 中接口一致，供 train-service 实现）
 */
public interface StockLockService {

    boolean tryDeduct(Long trainId, String trainDate, Integer seatType, String startStation, String endStation, int count);

    /**
     * 联程/N 线段单笔 Lua 原子预扣；各段必须使用相同 {@link TrainStockCommand#getCount()}
     */
    boolean tryDeductBatch(List<TrainStockCommand> segments);

    void rollback(Long trainId, String trainDate, Integer seatType, String startStation, String endStation, int count);

    /**
     * 联程单笔 Lua 原子释占（先做各段 locked 校验再写）。
     */
    void rollbackBatch(List<TrainStockCommand> segments);

    void confirm(Long trainId, String trainDate, Integer seatType, String startStation, String endStation, int count);

    /**
     * 联程单笔 Lua 原子确认预占转为售出（仅递减各段 locked）。
     */
    void confirmBatch(List<TrainStockCommand> segments);

    default void initStock(Long trainId, String trainDate, Integer seatType, String startStation, String endStation, int stock) {
        initStock(trainId, trainDate, seatType, startStation, endStation, stock, false);
    }

    void initStock(Long trainId, String trainDate, Integer seatType, String startStation, String endStation, int stock, boolean force);
}
