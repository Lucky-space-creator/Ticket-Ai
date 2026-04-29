package com.ticket.train.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.entity.TicketStock;
import com.ticket.entity.Train;
import com.ticket.enums.BusinessStatus;
import com.ticket.mapper.TicketStockMapper;
import com.ticket.train.service.StockGenerationService;
import com.ticket.train.service.StockLockService;
import com.ticket.train.service.TrainService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 库存生成服务实现
 */
@Service
public class StockGenerationServiceImpl implements StockGenerationService {

    private static final Logger logger = LoggerFactory.getLogger(StockGenerationServiceImpl.class);

    @Resource
    private TrainService trainService;

    @Resource
    private TicketStockMapper ticketStockMapper;

    @Resource
    private StockLockService stockLockService;

    // 座位类型配置：座位类型 -> {价格, 库存数量}
    private static final Map<Integer, SeatConfig> SEAT_CONFIG_MAP = new HashMap<>();

    static {
        // 商务座
        SEAT_CONFIG_MAP.put(BusinessStatus.SEAT_TYPE_BUSINESS, new SeatConfig(new BigDecimal("800.00"), 50));
        // 一等座
        SEAT_CONFIG_MAP.put(BusinessStatus.SEAT_TYPE_FIRST, new SeatConfig(new BigDecimal("500.00"), 100));
        // 二等座
        SEAT_CONFIG_MAP.put(BusinessStatus.SEAT_TYPE_SECOND, new SeatConfig(new BigDecimal("350.00"), 200));
        // 硬卧（跳过软卧，因为用户要求硬卧）
        SEAT_CONFIG_MAP.put(BusinessStatus.SEAT_TYPE_HARD_SLEEPER, new SeatConfig(new BigDecimal("250.00"), 150));
        // 硬座
        SEAT_CONFIG_MAP.put(BusinessStatus.SEAT_TYPE_HARD_SEAT, new SeatConfig(new BigDecimal("150.00"), 500));
    }

    // 默认生成未来7天
    private static final int DEFAULT_DAYS = 7;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StockGenerationResult generateStock(int days) {
        if (days <= 0) {
            days = DEFAULT_DAYS;
        }

        logger.info("开始生成未来 {} 天的库存数据...", days);

        // 1. 查询所有正常状态的车次
        LambdaQueryWrapper<Train> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Train::getStatus, BusinessStatus.TRAIN_STATUS_NORMAL);
        List<Train> trains = trainService.list(wrapper);

        if (trains.isEmpty()) {
            logger.warn("没有找到正常状态的车次，跳过库存生成");
            return new StockGenerationResult(0, 0, 0, 0, "没有找到正常状态的车次");
        }

        int totalStockRecords = 0;
        int successCount = 0;
        int skipCount = 0;

        LocalDate today = LocalDate.now();

        // 2. 为每个车次生成未来 days 天的库存
        for (Train train : trains) {
            for (int i = 0; i < days; i++) {
                LocalDate trainDate = today.plusDays(i);
                String dateStr = trainDate.format(DateTimeFormatter.ISO_LOCAL_DATE);

                try {
                    int generated = generateStockForTrainAndDate(train, dateStr);
                    totalStockRecords += generated;
                    successCount++;
                } catch (Exception e) {
                    logger.error("生成库存失败: trainId={}, trainNo={}, trainDate={}", 
                            train.getId(), train.getTrainNo(), dateStr, e);
                    skipCount++;
                }
            }
        }

        String message = String.format("库存生成完成，共处理 %d 个车次，生成 %d 条库存记录，成功: %d，跳过: %d",
                trains.size(), totalStockRecords, successCount, skipCount);
        logger.info(message);

        return new StockGenerationResult(trains.size(), totalStockRecords, successCount, skipCount, message);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StockGenerationResult generateStockForDate(String trainDate) {
        logger.info("开始为日期 {} 生成库存数据...", trainDate);

        // 验证日期格式
        LocalDate date;
        try {
            date = LocalDate.parse(trainDate);
        } catch (Exception e) {
            logger.error("日期格式错误: {}", trainDate, e);
            return new StockGenerationResult(0, 0, 0, 0, "日期格式错误，请使用 yyyy-MM-dd 格式");
        }

        // 1. 查询所有正常状态的车次
        LambdaQueryWrapper<Train> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Train::getStatus, BusinessStatus.TRAIN_STATUS_NORMAL);
        List<Train> trains = trainService.list(wrapper);

        if (trains.isEmpty()) {
            logger.warn("没有找到正常状态的车次，跳过库存生成");
            return new StockGenerationResult(0, 0, 0, 0, "没有找到正常状态的车次");
        }

        int totalStockRecords = 0;
        int successCount = 0;
        int skipCount = 0;

        // 2. 为每个车次生成指定日期的库存
        for (Train train : trains) {
            try {
                int generated = generateStockForTrainAndDate(train, trainDate);
                totalStockRecords += generated;
                successCount++;
            } catch (Exception e) {
                logger.error("生成库存失败: trainId={}, trainNo={}, trainDate={}", 
                        train.getId(), train.getTrainNo(), trainDate, e);
                skipCount++;
            }
        }

        String message = String.format("库存生成完成，共处理 %d 个车次，生成 %d 条库存记录，成功: %d，跳过: %d",
                trains.size(), totalStockRecords, successCount, skipCount);
        logger.info(message);

        return new StockGenerationResult(trains.size(), totalStockRecords, successCount, skipCount, message);
    }

    /**
     * 为指定车次和日期生成库存记录
     * @param train 车次
     * @param trainDate 乘车日期（yyyy-MM-dd）
     * @return 生成的库存记录数量
     */
    private int generateStockForTrainAndDate(Train train, String trainDate) {
        List<TicketStock> stocksToSave = new ArrayList<>();

        for (Map.Entry<Integer, SeatConfig> entry : SEAT_CONFIG_MAP.entrySet()) {
            Integer seatType = entry.getKey();
            SeatConfig config = entry.getValue();

            // 创建库存记录
            TicketStock stock = new TicketStock();
            stock.setTrainId(train.getId());
            stock.setTrainDate(LocalDate.parse(trainDate));
            stock.setStartStation(train.getStartStation());
            stock.setEndStation(train.getEndStation());
            stock.setSeatType(seatType);
            stock.setPrice(config.getPrice());
            stock.setAvailableSeats(config.getStock());
            stock.setVersion(0); // 乐观锁版本号初始为0

            stocksToSave.add(stock);
        }

        if (stocksToSave.isEmpty()) {
            return 0;
        }

        int savedCount = 0;
        for (TicketStock stock : stocksToSave) {
            try {
                // 先查询是否已存在
                LambdaQueryWrapper<TicketStock> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(TicketStock::getTrainId, stock.getTrainId())
                        .eq(TicketStock::getTrainDate, stock.getTrainDate())
                        .eq(TicketStock::getStartStation, stock.getStartStation())
                        .eq(TicketStock::getEndStation, stock.getEndStation())
                        .eq(TicketStock::getSeatType, stock.getSeatType());

                TicketStock existing = ticketStockMapper.selectOne(wrapper);
                if (existing != null) {
                    // 记录已存在，只更新价格（如果价格有变化）
                    if (existing.getPrice().compareTo(stock.getPrice()) != 0) {
                        existing.setPrice(stock.getPrice());
                        existing.setVersion(existing.getVersion() + 1);
                        ticketStockMapper.updateById(existing);
                        logger.debug("更新价格: trainId={}, trainDate={}, seatType={}, oldPrice={}, newPrice={}",
                                stock.getTrainId(), trainDate, stock.getSeatType(), existing.getPrice(), stock.getPrice());
                    }
                    // 库存数量保持不变，不覆盖已售出的库存
                    // 不调用 initStock，因为 Redis 中库存已存在且数量正确
                } else {
                    // 记录不存在，插入新记录
                    ticketStockMapper.insert(stock);
                    savedCount++;

                    // 同步到Redis
                    stockLockService.initStock(
                            stock.getTrainId(),
                            trainDate,
                            stock.getSeatType(),
                            stock.getStartStation(),
                            stock.getEndStation(),
                            stock.getAvailableSeats()
                    );
                    logger.debug("新增库存记录: trainId={}, trainDate={}, seatType={}, stock={}",
                            stock.getTrainId(), trainDate, stock.getSeatType(), stock.getAvailableSeats());
                }
            } catch (Exception e) {
                logger.error("保存库存记录失败: trainId={}, trainDate={}, seatType={}", 
                        stock.getTrainId(), trainDate, stock.getSeatType(), e);
                throw e; // 抛出异常让外层处理
            }
        }

        logger.debug("为车次 {} 日期 {} 生成 {} 条新库存记录", train.getTrainNo(), trainDate, savedCount);
        return savedCount;
    }

    /**
     * 座位配置类
     */
    private static class SeatConfig {
        private final BigDecimal price;
        private final int stock;

        public SeatConfig(BigDecimal price, int stock) {
            this.price = price;
            this.stock = stock;
        }

        public BigDecimal getPrice() {
            return price;
        }

        public int getStock() {
            return stock;
        }
    }
}