package com.ticket.service;

/**
 * 库存锁服务 - 基于Redis Lua脚本的原子库存操作
 * 解决高并发下的超卖问题
 */
public interface StockLockService {

    /**
     * 预扣库存（原子操作）
     * @param trainId 车次ID
     * @param trainDate 乘车日期（格式：yyyy-MM-dd）
     * @param seatType 座位类型
     * @param startStation 出发站
     * @param endStation 到达站
     * @param count 扣减数量
     * @return true=扣减成功, false=库存不足
     */
    boolean tryDeduct(Long trainId, String trainDate, Integer seatType, String startStation, String endStation, int count);

    /**
     * 回滚库存（下单失败/取消时调用）
     * @param trainId 车次ID
     * @param trainDate 乘车日期
     * @param seatType 座位类型
     * @param startStation 出发站
     * @param endStation 到达站
     * @param count 回滚数量
     */
    void rollback(Long trainId, String trainDate, Integer seatType, String startStation, String endStation, int count);

    /**
     * 确认扣减（支付成功后调用）
     * @param trainId 车次ID
     * @param trainDate 乘车日期
     * @param seatType 座位类型
     * @param startStation 出发站
     * @param endStation 到达站
     * @param count 确认数量
     */
    void confirm(Long trainId, String trainDate, Integer seatType, String startStation, String endStation, int count);

    /**
     * 初始化库存到Redis（系统启动/数据变更时调用）
     * @param trainId 车次ID
     * @param trainDate 乘车日期
     * @param seatType 座位类型
     * @param startStation 出发站
     * @param endStation 到达站
     * @param stock 库存数量
     */
    void initStock(Long trainId, String trainDate, Integer seatType, String startStation, String endStation, int stock);
}