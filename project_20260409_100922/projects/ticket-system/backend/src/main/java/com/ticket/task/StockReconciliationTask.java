package com.ticket.task;

import com.ticket.service.ReconciliationResult;
import com.ticket.service.StockReconciliationService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 库存对账定时任务
 * 每小时执行一次全量对账，每10分钟执行一次关键车次对账
 */
@Component
public class StockReconciliationTask {

    private static final Logger logger = LoggerFactory.getLogger(StockReconciliationTask.class);

    @Resource
    private StockReconciliationService stockReconciliationService;

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
        logger.debug("开始关键车次库存对账...");
        // 这里可以添加逻辑，查询最近3天内的热门车次进行对账
        // 目前先留空，后续可根据业务需求扩展
        // 例如：从数据库查询最近3天内的车次，逐个调用 reconcileStock
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