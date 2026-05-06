package com.ticket.train.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ticket.dto.internal.TrainStockCommand;
import com.ticket.dto.train.RouteSearchOption;
import com.ticket.enums.CacheKey;
import com.ticket.entity.Station;
import com.ticket.entity.TicketStock;
import com.ticket.entity.Train;
import com.ticket.train.mapper.StationMapper;
import com.ticket.train.mapper.TicketStockMapper;
import com.ticket.train.mapper.TrainMapper;
import com.ticket.train.route.RouteSearchPlanner;
import com.ticket.train.service.TrainService;
import com.ticket.service.StockLockService;
import com.ticket.util.StationNameUtil;
import com.ticket.util.RedisUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
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
    private StationMapper stationMapper;

    @Resource
    private RedisUtil redisUtil;

    @Resource
    private StockLockService stockLockService;

    @Resource
    private RouteSearchPlanner routeSearchPlanner;

    /**
     * 直筒搜索缓存 TTL（分钟）；短 TTL 接受短暂脏读（计划：缩短 TRAIN_SEARCH）
     */
    private static final long TRAIN_SEARCH_CACHE_TTL_MINUTES = 5L;

    @Override
    public List<RouteSearchOption> searchRoutes(String startStation, String endStation, String trainDate, Integer seatType) {
        return routeSearchPlanner.search(startStation, endStation, LocalDate.parse(trainDate), seatType);
    }

    @Override
    public List<String> listStationNames() {
        LambdaQueryWrapper<Station> w = new LambdaQueryWrapper<>();
        w.orderByAsc(Station::getName);
        return stationMapper.selectList(w).stream().map(Station::getName).filter(n -> n != null && !n.isBlank()).toList();
    }

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
            wrapper.like(Train::getStartStation, StationNameUtil.normalize(startStation));
        }

        // 如果到达站不为空，添加条件
        if (endStation != null && !endStation.trim().isEmpty()) {
            wrapper.like(Train::getEndStation, StationNameUtil.normalize(endStation));
        }

        // 只查询正常状态的车次
        wrapper.eq(Train::getStatus, 1);

        List<Train> trains = list(wrapper);

        if (!trains.isEmpty()) {
            redisUtil.set(cacheKey, trains, TRAIN_SEARCH_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
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
               .eq(Train::getStatus, 1)
               .orderByAsc(Train::getStartTime);
        List<Train> segments = list(wrapper);
        Train train = segments.isEmpty() ? null : segments.get(0);

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
     * 车次相关缓存过期时间（分钟）。
     * 不可使用 Long.MAX_VALUE：写入 Redis TTL 时换算为毫秒会溢出为负数，导致 Lettuce 抛错或接口 500。
     */
    private static final long TRAIN_CACHE_TTL_MINUTES = 30L;

    private long getExpireTrainTime(Train train) {
        return TRAIN_CACHE_TTL_MINUTES;
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
        TicketStock stock = pickTicketStock(trainId, trainDate, startStation, endStation, seatType);
        return stock != null ? stock.getPrice() : BigDecimal.ZERO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deductStock(Long trainId, String trainDate, String startStation, String endStation, Integer seatType, Integer count) {
        assertStockRowOpenForDeduct(trainId, trainDate, startStation, endStation, seatType);
        // 1. 先通过Redis原子操作预扣库存
        boolean deducted = stockLockService.tryDeduct(trainId, trainDate, seatType, startStation, endStation, count);
        if (!deducted) {
            throw new RuntimeException("余票不足");
        }

        // 2. 检查数据库记录是否存在（仅做校验，不扣减数据库库存）
        TicketStock stock = pickTicketStock(trainId, trainDate, startStation, endStation, seatType);

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
    public boolean deductStocksBatch(List<TrainStockCommand> segments) {
        if (segments == null || segments.isEmpty()) {
            throw new IllegalArgumentException("联程段落为空");
        }
        for (TrainStockCommand cmd : segments) {
            assertStockRowOpenForDeduct(
                    cmd.getTrainId(), cmd.getTrainDate(),
                    cmd.getStartStation(), cmd.getEndStation(), cmd.getSeatType());
        }
        boolean deducted = stockLockService.tryDeductBatch(segments);
        if (!deducted) {
            throw new RuntimeException("余票不足");
        }
        for (TrainStockCommand cmd : segments) {
            TicketStock stock = pickTicketStock(
                    cmd.getTrainId(), cmd.getTrainDate(),
                    cmd.getStartStation(), cmd.getEndStation(), cmd.getSeatType());
            if (stock == null) {
                stockLockService.rollbackBatch(segments);
                throw new RuntimeException("余票信息不存在");
            }
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean rollbackStocksBatch(List<TrainStockCommand> segments) {
        if (segments == null || segments.isEmpty()) {
            return true;
        }
        stockLockService.rollbackBatch(segments);
        Integer countObj = segments.get(0).getCount();
        int count = countObj != null ? countObj : 0;
        for (TrainStockCommand cmd : segments) {
            TicketStock stock = pickTicketStock(
                    cmd.getTrainId(), cmd.getTrainDate(),
                    cmd.getStartStation(), cmd.getEndStation(), cmd.getSeatType());
            if (stock == null) {
                continue;
            }
            stock.setAvailableSeats(stock.getAvailableSeats() + count);
            ticketStockMapper.updateById(stock);
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean rollbackStock(Long trainId, String trainDate, String startStation, String endStation, Integer seatType, Integer count) {
        // 1. 先回滚Redis库存
        stockLockService.rollback(trainId, trainDate, seatType, startStation, endStation, count);

        // 2. 更新数据库库存
        TicketStock stock = pickTicketStock(trainId, trainDate, startStation, endStation, seatType);

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

    /** 开售校验：须存在 ticket_stock 行且未停售，站名按库内规范化键匹配 */
    private void assertStockRowOpenForDeduct(Long trainId, String trainDateStr, String startStation, String endStation, Integer seatType) {
        TicketStock stock = pickTicketStock(trainId, trainDateStr, startStation, endStation, seatType);
        if (stock == null) {
            throw new RuntimeException("余票信息不存在");
        }
        if (stock.getSaleEnabled() != null && stock.getSaleEnabled() == 0) {
            throw new RuntimeException("该席别已停售");
        }
    }

    private TicketStock pickTicketStock(Long trainId, String trainDateStr, String startStation, String endStation, Integer seatType) {
        LocalDate trainDate = LocalDate.parse(trainDateStr);
        String from = StationNameUtil.normalize(startStation);
        String to = StationNameUtil.normalize(endStation);
        LambdaQueryWrapper<TicketStock> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TicketStock::getTrainId, trainId)
                .eq(TicketStock::getTrainDate, trainDate)
                .eq(TicketStock::getStartStation, from)
                .eq(TicketStock::getEndStation, to)
                .eq(TicketStock::getSeatType, seatType);
        return ticketStockMapper.selectOne(wrapper);
    }
}