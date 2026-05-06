package com.ticket.train.service;

import com.ticket.entity.TicketStock;
import com.ticket.entity.Train;
import com.ticket.enums.CacheKey;
import com.ticket.train.mapper.TicketStockMapper;
import com.ticket.train.mapper.TrainMapper;
import com.ticket.util.RedisUtil;
import com.ticket.util.StationNameUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * ticket_stock 变更后删除相关 Redis 缓存，避免继续展示停售前的余票快照。
 */
@Service
public class TrainStockCacheInvalidateService {

    @Resource
    private TicketStockMapper ticketStockMapper;

    @Resource
    private TrainMapper trainMapper;

    @Resource
    private RedisUtil redisUtil;

    public void invalidateByStockId(Long ticketStockId) {
        if (ticketStockId == null) {
            return;
        }
        TicketStock ts = ticketStockMapper.selectById(ticketStockId);
        if (ts == null) {
            return;
        }
        invalidate(ts);
    }

    public void invalidate(TicketStock ts) {
        if (ts == null) {
            return;
        }
        String dateStr = ts.getTrainDate() != null ? ts.getTrainDate().toString() : "";
        String from = StationNameUtil.normalize(ts.getStartStation());
        String to = StationNameUtil.normalize(ts.getEndStation());
        String stockKey = CacheKey.formatTrainStockKey(ts.getTrainId(), dateStr, ts.getSeatType(), from, to);
        redisUtil.delete(stockKey);

        Train train = trainMapper.selectById(ts.getTrainId());
        if (train != null && train.getTrainNo() != null) {
            redisUtil.delete(CacheKey.formatTrainStocksListKey(train.getTrainNo(), dateStr));
        }
    }
}
