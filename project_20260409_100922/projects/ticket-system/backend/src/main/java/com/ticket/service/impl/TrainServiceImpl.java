package com.ticket.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ticket.entity.TicketStock;
import com.ticket.entity.Train;
import com.ticket.enums.CacheKey;
import com.ticket.mapper.TicketStockMapper;
import com.ticket.mapper.TrainMapper;
import com.ticket.service.TrainService;
import com.ticket.util.RedisUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 车次服务实现
 */
@Service
public class TrainServiceImpl extends ServiceImpl<TrainMapper, Train> implements TrainService {

    @Resource
    private TicketStockMapper ticketStockMapper;

    @Resource
    private RedisUtil redisUtil;

    @Override
    public List<Train> searchTrains(String startStation, String endStation, String trainDate) {
        // 先查缓存
        String cacheKey = String.format(CacheKey.TRAIN_SEARCH, startStation == null ? "" : startStation, endStation == null ? "" : endStation, trainDate == null ? "" : trainDate);
        List<Train> cached = redisUtil.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        // 查询车次（通过余票表关联查询）
        LambdaQueryWrapper<Train> wrapper = new LambdaQueryWrapper<>();

        // 如果出发站不为空，添加条件
        if (startStation != null && !startStation.trim().isEmpty()) {
            wrapper.eq(Train::getStartStation, startStation);
        }

        // 如果到达站不为空，添加条件
        if (endStation != null && !endStation.trim().isEmpty()) {
            wrapper.eq(Train::getEndStation, endStation);
        }

        // 只查询正常状态的车次
        wrapper.eq(Train::getStatus, 1);

        List<Train> trains = list(wrapper);

        // 存缓存（30分钟）
        redisUtil.set(cacheKey, trains, 30, TimeUnit.MINUTES);

        return trains;
    }

    @Override
    public Train getTrainDetail(Long trainId) {
        // 先查缓存
        String cacheKey = String.format(CacheKey.TRAIN_DETAIL, trainId);
        Train cached = redisUtil.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        Train train = getById(trainId);

        // 存缓存（1小时）
        if (train != null) {
            redisUtil.set(cacheKey, train, 60, TimeUnit.MINUTES);
        }

        return train;
    }

    @Override
    public List<TicketStock> getTicketStocks(Long trainId, String trainDate) {
        LambdaQueryWrapper<TicketStock> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TicketStock::getTrainId, trainId)
                .eq(TicketStock::getTrainDate, trainDate)
                .gt(TicketStock::getAvailableSeats, 0); // 只返回有票的

        return ticketStockMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deductStock(Long trainId, String trainDate, String startStation, String endStation, Integer seatType, Integer count) {
        LambdaQueryWrapper<TicketStock> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TicketStock::getTrainId, trainId)
                .eq(TicketStock::getTrainDate, trainDate)
                .eq(TicketStock::getStartStation, startStation)
                .eq(TicketStock::getEndStation, endStation)
                .eq(TicketStock::getSeatType, seatType);

        // 使用数据库锁防止超卖
        TicketStock stock = ticketStockMapper.selectOne(wrapper);

        if (stock == null) {
            throw new RuntimeException("余票信息不存在");
        }

        if (stock.getAvailableSeats() < count) {
            throw new RuntimeException("余票不足");
        }

        // 扣减库存
        stock.setAvailableSeats(stock.getAvailableSeats() - count);
        int result = ticketStockMapper.updateById(stock);

        if (result > 0) {
            // 清除缓存
            String cacheKey = String.format(CacheKey.TRAIN_STOCK, trainId, trainDate, seatType);
            redisUtil.delete(cacheKey);
        }

        return result > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean rollbackStock(Long trainId, String trainDate, String startStation, String endStation, Integer seatType, Integer count) {
        LambdaQueryWrapper<TicketStock> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TicketStock::getTrainId, trainId)
                .eq(TicketStock::getTrainDate, trainDate)
                .eq(TicketStock::getStartStation, startStation)
                .eq(TicketStock::getEndStation, endStation)
                .eq(TicketStock::getSeatType, seatType);

        TicketStock stock = ticketStockMapper.selectOne(wrapper);

        if (stock == null) {
            return false;
        }

        // 回滚库存
        stock.setAvailableSeats(stock.getAvailableSeats() + count);
        int result = ticketStockMapper.updateById(stock);

        if (result > 0) {
            // 清除缓存
            String cacheKey = String.format(CacheKey.TRAIN_STOCK, trainId, trainDate, seatType);
            redisUtil.delete(cacheKey);
        }

        return result > 0;
    }
}
