package com.ticket.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.entity.TicketStock;
import com.ticket.service.TrainService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 库存初始化器
 * 应用启动时自动将MySQL库存数据同步到Redis
 */
@Slf4j
@Component
public class StockInitializer implements ApplicationRunner {

    @Resource
    private com.ticket.mapper.TicketStockMapper ticketStockMapper;

    @Resource
    private TrainService trainService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("开始同步库存数据到Redis...");

        try {
            // 查询所有库存记录
            LambdaQueryWrapper<TicketStock> wrapper = new LambdaQueryWrapper<>();
            wrapper.select(TicketStock::getTrainId, TicketStock::getTrainDate, 
                          TicketStock::getSeatType, TicketStock::getAvailableSeats);
            List<TicketStock> stocks = ticketStockMapper.selectList(wrapper);

            int successCount = 0;
            int skipCount = 0;

            for (TicketStock stock : stocks) {
                try {
                    // 初始化到Redis
                    trainService.initStockToRedis(
                            stock.getTrainId(),
                            stock.getTrainDate().toString(),
                            stock.getSeatType(),
                            stock.getStartStation(),
                            stock.getEndStation(),
                            stock.getAvailableSeats()
                    );
                    successCount++;
                } catch (Exception e) {
                    log.warn("初始化库存失败: trainId={}, trainDate={}, seatType={}, stock={}", 
                            stock.getTrainId(), stock.getTrainDate(), stock.getSeatType(), 
                            stock.getAvailableSeats(), e);
                    skipCount++;
                }
            }

            log.info("库存数据同步完成，成功: {} 条，跳过: {} 条", successCount, skipCount);
        } catch (Exception e) {
            log.error("同步库存数据到Redis异常", e);
        }
    }
}