package com.ticket.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ticket.admin.client.TrainStockCacheFeignClient;
import com.ticket.admin.dto.TicketStockListItem;
import com.ticket.admin.dto.TicketStockPageResult;
import com.ticket.admin.dto.TicketStockSaleEnabledRequest;
import com.ticket.admin.dto.TicketStockSeatsRequest;
import com.ticket.admin.exception.AdminBizException;
import com.ticket.admin.mapper.TicketStockMapper;
import com.ticket.admin.mapper.TrainMapper;
import com.ticket.admin.service.AdminTicketStockService;
import com.ticket.entity.TicketStock;
import com.ticket.entity.Train;
import jakarta.annotation.Resource;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class AdminTicketStockServiceImpl implements AdminTicketStockService {

    @Resource
    private TicketStockMapper ticketStockMapper;

    @Resource
    private TrainMapper trainMapper;

    @Resource
    private TrainStockCacheFeignClient trainStockCacheFeignClient;

    @Override
    public TicketStockPageResult page(long current, long size, Long trainId, LocalDate trainDate, String trainNo, Integer saleEnabled) {
        if (saleEnabled != null && saleEnabled != 0 && saleEnabled != 1) {
            throw new AdminBizException("saleEnabled 须为 0、1 或省略");
        }
        LambdaQueryWrapper<TicketStock> w = new LambdaQueryWrapper<>();
        if (trainId != null) {
            w.eq(TicketStock::getTrainId, trainId);
        }
        if (trainDate != null) {
            w.eq(TicketStock::getTrainDate, trainDate);
        }
        if (saleEnabled != null) {
            w.eq(TicketStock::getSaleEnabled, saleEnabled);
        }
        if (StringUtils.hasText(trainNo)) {
            String key = trainNo.trim();
            List<Train> trains = trainMapper.selectList(
                    new LambdaQueryWrapper<Train>().like(Train::getTrainNo, key));
            if (trains.isEmpty()) {
                TicketStockPageResult empty = new TicketStockPageResult();
                empty.setRecords(Collections.emptyList());
                empty.setTotal(0);
                empty.setCurrent(current);
                empty.setSize(size);
                return empty;
            }
            w.in(TicketStock::getTrainId,
                    trains.stream().map(Train::getId).collect(Collectors.toList()));
        }
        w.orderByDesc(TicketStock::getTrainDate).orderByDesc(TicketStock::getId);
        Page<TicketStock> mp = ticketStockMapper.selectPage(new Page<>(current, size), w);
        List<Long> trainIds = mp.getRecords().stream()
                .map(TicketStock::getTrainId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> trainNoById = trainIds.isEmpty()
                ? Collections.emptyMap()
                : trainMapper.selectBatchIds(trainIds).stream()
                .collect(Collectors.toMap(Train::getId, Train::getTrainNo, (a, b) -> a));

        List<TicketStockListItem> rows = mp.getRecords().stream().map(ts -> {
            TicketStockListItem item = new TicketStockListItem();
            BeanUtils.copyProperties(ts, item);
            item.setTrainNo(trainNoById.getOrDefault(ts.getTrainId(), ""));
            return item;
        }).toList();

        TicketStockPageResult result = new TicketStockPageResult();
        result.setRecords(rows);
        result.setTotal(mp.getTotal());
        result.setCurrent(mp.getCurrent());
        result.setSize(mp.getSize());
        return result;
    }

    @Override
    public void updateSeats(Long id, TicketStockSeatsRequest body) {
        if (body == null || body.getTotalSeats() == null || body.getAvailableSeats() == null) {
            throw new AdminBizException("请提供 totalSeats 与 availableSeats");
        }
        if (body.getTotalSeats() < 0 || body.getAvailableSeats() < 0) {
            throw new AdminBizException("座位数不能为负");
        }
        if (body.getAvailableSeats() > body.getTotalSeats()) {
            throw new AdminBizException("剩余座位不能大于总座位");
        }
        TicketStock ts = ticketStockMapper.selectById(id);
        if (ts == null) {
            throw new AdminBizException("库存记录不存在");
        }
        ts.setTotalSeats(body.getTotalSeats());
        ts.setAvailableSeats(body.getAvailableSeats());
        ticketStockMapper.updateById(ts);
        trainStockCacheFeignClient.invalidateTicketStock(id);
    }

    @Override
    public void updateSaleEnabled(Long id, TicketStockSaleEnabledRequest body) {
        if (body == null || (body.getSaleEnabled() != 0 && body.getSaleEnabled() != 1)) {
            throw new AdminBizException("saleEnabled 须为 0 或 1");
        }
        TicketStock ts = ticketStockMapper.selectById(id);
        if (ts == null) {
            throw new AdminBizException("库存记录不存在");
        }
        ts.setSaleEnabled(body.getSaleEnabled());
        ticketStockMapper.updateById(ts);
        trainStockCacheFeignClient.invalidateTicketStock(id);
    }
}
