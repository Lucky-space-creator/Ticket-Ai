package com.ticket.task;

import com.ticket.service.StockGenerationService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每日库存生成定时任务
 * 每天凌晨1点自动生成未来7天的车票库存
 */
@Component
public class DailyStockGenerationTask {

    private static final Logger logger = LoggerFactory.getLogger(DailyStockGenerationTask.class);

    @Resource
    private StockGenerationService stockGenerationService;

    /**
     * 每日凌晨1点执行，生成未来7天的库存
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void generateDailyStock() {
        logger.info("开始执行每日库存生成任务...");

        try {
            // 生成未来7天的库存
            StockGenerationService.StockGenerationResult result = stockGenerationService.generateStock(7);
            logger.info("每日库存生成任务完成: {}", result.getMessage());
        } catch (Exception e) {
            logger.error("每日库存生成任务异常", e);
        }
    }

    /**
     * 应用启动后延迟5分钟执行一次，确保当天有库存
     * 注意：此方法仅作为备用，防止定时任务错过执行时间
     */
    @Scheduled(initialDelay = 5 * 60 * 1000, fixedDelay = Long.MAX_VALUE)
    public void initStockOnStartup() {
        logger.info("应用启动后初始化库存...");

        try {
            // 生成未来7天的库存
            StockGenerationService.StockGenerationResult result = stockGenerationService.generateStock(7);
            logger.info("应用启动库存初始化完成: {}", result.getMessage());
        } catch (Exception e) {
            logger.error("应用启动库存初始化异常", e);
        }
    }
}