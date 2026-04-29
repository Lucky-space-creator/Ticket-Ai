package com.ticket.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ticket.enums.CacheKey;
import com.ticket.entity.TicketStock;
import com.ticket.entity.Train;
import com.ticket.mapper.TicketStockMapper;
import com.ticket.mapper.TrainMapper;
import com.ticket.service.StockLockService;
import com.ticket.service.TrainService;
import com.ticket.util.RedisUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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

    @Resource
    private StockLockService stockLockService;

    @Override
    public List<Train> searchTrains(String startStation, String endStation, String trainDate) {
        // 先查Redis缓存
        String cacheKey = String.format(CacheKey.TRAIN_SEARCH,
                startStation == null ? "" : startStation,
                endStation == null ? "" : endStation,
                trainDate == null ? "" : trainDate);
        List<Train> cached = redisUtil.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        // 查询车次（通过余票表关联查询）
        LambdaQueryWrapper<Train> wrapper = new LambdaQueryWrapper<>();

        // 如果出发站不为空，添加条件
        if (startStation != null && !startStation.trim().isEmpty()) {
            wrapper.like(Train::getStartStation, startStation);
        }

        // 如果到达站不为空，添加条件
        if (endStation != null && !endStation.trim().isEmpty()) {
            wrapper.like(Train::getEndStation, endStation);
        }

        // 只查询正常状态的车次
        wrapper.eq(Train::getStatus, 1);

        List<Train> trains = list(wrapper);

        // 缓存整个查询结果列表（在循环外设置一次）
        if (!trains.isEmpty()) {
            long expireTime = getExpireTrainTime(trains.get(0));
            if (expireTime > 0) {
                redisUtil.set(cacheKey, trains, expireTime, TimeUnit.MINUTES);
            }
        }

        return trains;
    }

    @Override
    public Train getTrainDetail(String trainNo) {
        // 先查缓存（按车次号）
        String cacheKey = String.format(CacheKey.TRAIN_DETAIL_NO, trainNo);
        Train cached = redisUtil.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        // 按车次号查询
        LambdaQueryWrapper<Train> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Train::getTrainNo, trainNo)
               .eq(Train::getStatus, 1);
        Train train = getOne(wrapper);

        if (train != null) {
            long expireTime = getExpireTrainTime(train);
            if (expireTime > 0) {
                redisUtil.set(cacheKey, train, expireTime, TimeUnit.MINUTES);
            }
        }

        return train;
    }

    @Override
    public Train getTrainDetailById(Long trainId) {
        // 先查缓存
        String cacheKey = String.format(CacheKey.TRAIN_DETAIL, trainId);
        Train cached = redisUtil.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        Train train = getById(trainId);

        if (train != null) {
            long expireTime = getExpireTrainTime(train);
            if (expireTime > 0) {
                redisUtil.set(cacheKey, train, expireTime, TimeUnit.MINUTES);
            }
        }

        return train;
    }

    /**
     * 车站到达时间 - 当前时间的函数
     */
    private long getExpireTrainTime(Train train) {
        //测试环境先直接返回大于0的天数
//        LocalDateTime endDateTime = LocalDateTime.from(train.getEndTime());
//        return ChronoUnit.SECONDS.between(LocalDateTime.now(), endDateTime);

        return Long.MAX_VALUE;
    }

    @Override
    public List<TicketStock> getTicketStocks(Long trainId, String trainDate) {
        Train train = getTrainDetailById(trainId);
        if (train == null) {
            return new ArrayList<>();
        }

        LambdaQueryWrapper<TicketStock> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TicketStock::getTrainId, trainId)
                .eq(TicketStock::getTrainDate, trainDate)
                .eq(TicketStock::getStartStation, train.getStartStation())
                .eq(TicketStock::getEndStation, train.getEndStation());
        List<TicketStock> result = ticketStockMapper.selectList(wrapper);

        result.forEach(stock -> {
            int i = stock.getSeatType();
            String cacheKey = CacheKey.formatTrainStockKey(
                    train.getId(), trainDate, i,
                    train.getStartStation(), train.getEndStation());

            Integer stockCount = redisUtil.get(cacheKey);

            //缓存查到剩余票数，则设置到库存对象中
            if (stockCount != null) {
                stock.setAvailableSeats(stockCount);
            } else {
                //缓存未查到剩余票数，则设置到缓存中
                redisUtil.set(cacheKey, stock.getAvailableSeats(), 30, TimeUnit.MINUTES);
            }
        });

        return result;
    }

    @Override
    public BigDecimal getSeatPrice(Long trainId, String trainDate, String startStation, String endStation, Integer seatType) {
        LambdaQueryWrapper<TicketStock> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TicketStock::getTrainId, trainId)
                .eq(TicketStock::getTrainDate, trainDate)
                .eq(TicketStock::getStartStation, startStation)
                .eq(TicketStock::getEndStation, endStation)
                .eq(TicketStock::getSeatType, seatType);

        TicketStock stock = ticketStockMapper.selectOne(wrapper);
        return stock != null ? stock.getPrice() : BigDecimal.ZERO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deductStock(Long trainId, String trainDate, String startStation, String endStation, Integer seatType, Integer count) {
        // 1. 先通过Redis原子操作预扣库存
        boolean deducted = stockLockService.tryDeduct(trainId, trainDate, seatType, startStation, endStation, count);
        if (!deducted) {
            throw new RuntimeException("余票不足");
        }

        // 2. 检查数据库记录是否存在（仅做校验，不扣减数据库库存）
        LambdaQueryWrapper<TicketStock> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TicketStock::getTrainId, trainId)
                .eq(TicketStock::getTrainDate, trainDate)
                .eq(TicketStock::getStartStation, startStation)
                .eq(TicketStock::getEndStation, endStation)
                .eq(TicketStock::getSeatType, seatType);

        TicketStock stock = ticketStockMapper.selectOne(wrapper);

        if (stock == null) {
            // Redis预扣成功但数据库记录不存在，回滚Redis
            stockLockService.rollback(trainId, trainDate, seatType, startStation, endStation, count);
            throw new RuntimeException("余票信息不存在");
        }

        // 3. 不再同步更新数据库库存，由定时对账任务保证最终一致性
        // 注意：不能删除Redis库存Key，因为它是实际的库存数据而非查询缓存
        // Redis库存由Lua脚本原子扣减后持久化在Redis中，由对账任务保证与MySQL一致

        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean rollbackStock(Long trainId, String trainDate, String startStation, String endStation, Integer seatType, Integer count) {
        // 1. 先回滚Redis库存
        stockLockService.rollback(trainId, trainDate, seatType, startStation, endStation, count);

        // 2. 更新数据库库存
        LambdaQueryWrapper<TicketStock> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TicketStock::getTrainId, trainId)
                .eq(TicketStock::getTrainDate, trainDate)
                .eq(TicketStock::getStartStation, startStation)
                .eq(TicketStock::getEndStation, endStation)
                .eq(TicketStock::getSeatType, seatType);

        TicketStock stock = ticketStockMapper.selectOne(wrapper);

        if (stock == null) {
            // 数据库记录不存在，但Redis已回滚，返回true
            return true;
        }

        // 回滚数据库库存
        stock.setAvailableSeats(stock.getAvailableSeats() + count);
        int result = ticketStockMapper.updateById(stock);

        // Redis库存已由stockLockService.rollback()更新，无需删除

        return result > 0;
    }

    @Override
    public void initStockToRedis(Long trainId, String trainDate, Integer seatType, String startStation, String endStation, int stock) {
        stockLockService.initStock(trainId, trainDate, seatType, startStation, endStation, stock);
    }
}
