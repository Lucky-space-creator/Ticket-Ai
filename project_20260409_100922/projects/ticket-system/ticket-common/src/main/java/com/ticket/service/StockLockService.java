package com.ticket.service;

/**
 * 库存锁服务 - 基于 Redis 的原子库存操作（与 backend 中接口一致，供 train-service 实现）
 */
public interface StockLockService {

    boolean tryDeduct(Long trainId, String trainDate, Integer seatType, String startStation, String endStation, int count);

    void rollback(Long trainId, String trainDate, Integer seatType, String startStation, String endStation, int count);

    void confirm(Long trainId, String trainDate, Integer seatType, String startStation, String endStation, int count);

    default void initStock(Long trainId, String trainDate, Integer seatType, String startStation, String endStation, int stock) {
        initStock(trainId, trainDate, seatType, startStation, endStation, stock, false);
    }

    void initStock(Long trainId, String trainDate, Integer seatType, String startStation, String endStation, int stock, boolean force);
}
