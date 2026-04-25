package com.ticket.service;

import com.ticket.entity.ReconciliationResult;

/**
 * 库存对账服务
 * 用于定期对比Redis与MySQL的库存数据，修复不一致
 */
public interface StockReconciliationService {

    /**
     * 执行全量对账
     * @return 对账结果：修复数量、不一致数量等
     */
    ReconciliationResult reconcileAll();

    /**
     * 对账指定车次库存
     * @param trainId 车次ID
     * @param trainDate 乘车日期
     * @param seatType 座位类型
     * @return 对账结果
     */
    ReconciliationResult reconcileStock(Long trainId, String trainDate, Integer seatType);
}