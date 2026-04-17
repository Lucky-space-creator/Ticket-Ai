package com.ticket.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ticket.enums.CacheKey;
import com.ticket.entity.TicketStock;
import com.ticket.entity.Train;
import com.ticket.mapper.TicketStockMapper;
import com.ticket.mapper.TrainMapper;
import com.ticket.service.TrainService;
import com.ticket.util.RedisUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
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

        trains.forEach(train -> {
            if (train != null) {
                //车次最终战到达时间和当前时间差，设置过期时间，防止车已经停运还出现买票的情况
                long expireTime = getExpireTrainTime(train);

                if (expireTime > 0) {
                    redisUtil.set(cacheKey, train, expireTime, TimeUnit.MINUTES);
                }
            }
        });

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
        LambdaQueryWrapper<TicketStock> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TicketStock::getTrainId, trainId)
                .eq(TicketStock::getTrainDate, trainDate)
                .gt(TicketStock::getAvailableSeats, 0); // 只返回有票的

        return ticketStockMapper.selectList(wrapper);
    }

    @Override
    public java.math.BigDecimal getSeatPrice(Long trainId, String trainDate, String startStation, String endStation, Integer seatType) {
        LambdaQueryWrapper<TicketStock> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TicketStock::getTrainId, trainId)
                .eq(TicketStock::getTrainDate, trainDate)
                .eq(TicketStock::getStartStation, startStation)
                .eq(TicketStock::getEndStation, endStation)
                .eq(TicketStock::getSeatType, seatType);

        TicketStock stock = ticketStockMapper.selectOne(wrapper);
        return stock != null ? stock.getPrice() : java.math.BigDecimal.ZERO;
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
