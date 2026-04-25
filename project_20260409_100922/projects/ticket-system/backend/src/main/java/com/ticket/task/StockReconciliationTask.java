package com.ticket.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.entity.ReconciliationResult;
import com.ticket.entity.TicketStock;
import com.ticket.mapper.TicketStockMapper;
import com.ticket.service.StockReconciliationService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.util.List;

/**
 * 库存对账定时任务
 * 每小时执行一次全量对账，每10分钟执行一次关键车次对账
 */
@Component
public class StockReconciliationTask {

    private static final Logger logger = LoggerFactory.getLogger(StockReconciliationTask.class);

    @Resource
    private StockReconciliationService stockReconciliationService;

    @Resource
    private TicketStockMapper ticketStockMapper;

    /**
     * 每小时执行一次全量对账（整点执行）
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void hourlyFullReconciliation() {
        logger.info("开始每小时全量库存对账...");
        try {
            ReconciliationResult result = stockReconciliationService.reconcileAll();
            logger.info("每小时全量库存对账完成，耗时 {} ms，成功: {}，不一致: {}，修复: {}，错误: {}",
                    result.getDurationMs(), result.getSuccessCount(),
                    result.getMismatchCount(), result.getFixedCount(), result.getErrorCount());
        } catch (Exception e) {
            logger.error("每小时全量库存对账异常", e);
        }
    }

    /**
     * 每10分钟执行一次关键车次对账（检查最近3天内的车次）
     */
    @Scheduled(cron = "0 */10 * * * ?")
    public void frequentKeyReconciliation() {
        logger.info("开始关键车次库存对账...");
        try {
            // 查询最近3天的车次，按总座位数降序排列（热门车次通常座位数多）
            LocalDate today = LocalDate.now();
            LocalDate threeDaysLater = today.plusDays(3);
            
            LambdaQueryWrapper<TicketStock> wrapper = new LambdaQueryWrapper<>();
            wrapper.between(TicketStock::getTrainDate, today, threeDaysLater)
                   .orderByDesc(TicketStock::getTotalSeats)
                   .last("LIMIT 30");
            
            List<TicketStock> hotStocks = ticketStockMapper.selectList(wrapper);
            logger.info("关键车次对账，共筛选出 {} 个热门车次", hotStocks.size());
            
            int success = 0, error = 0;
            for (TicketStock stock : hotStocks) {
                try {
                    // 调用对账服务，传入车次ID、日期和座位类型
                    stockReconciliationService.reconcileStock(
                        stock.getTrainId(), 
                        stock.getTrainDate().toString(), 
                        stock.getSeatType()
                    );
                    success++;
                } catch (Exception e) {
                    logger.error("关键车次对账失败: trainId={}, trainDate={}, seatType={}", 
                            stock.getTrainId(), stock.getTrainDate(), stock.getSeatType(), e);
                    error++;
                }
            }
            
            logger.info("关键车次库存对账完成，成功: {}，失败: {}", success, error);
        } catch (Exception e) {
            logger.error("关键车次库存对账异常", e);
        }
    }

    /**
     * 每天凌晨3点执行深度对账（包含预占库存清理）
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void dailyDeepReconciliation() {
        logger.info("开始每日深度库存对账...");
        // 可以执行更彻底的对账，比如清理过期的预占库存等
        // 目前调用全量对账即可
        try {
            ReconciliationResult result = stockReconciliationService.reconcileAll();
            logger.info("每日深度库存对账完成，耗时 {} ms，成功: {}，不一致: {}，修复: {}，错误: {}",
                    result.getDurationMs(), result.getSuccessCount(),
                    result.getMismatchCount(), result.getFixedCount(), result.getErrorCount());
        } catch (Exception e) {
            logger.error("每日深度库存对账异常", e);
        }
    }
}