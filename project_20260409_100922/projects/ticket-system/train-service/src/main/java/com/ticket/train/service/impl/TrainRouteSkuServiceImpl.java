package com.ticket.train.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.dto.RouteLeg;
import com.ticket.dto.ValidateRouteSkuRequest;
import com.ticket.dto.ValidatedRouteSkuResponse;
import com.ticket.entity.TicketStock;
import com.ticket.entity.Train;
import com.ticket.entity.TrainRouteStop;
import com.ticket.enums.BusinessStatus;
import com.ticket.enums.RouteType;
import com.ticket.train.mapper.TicketStockMapper;
import com.ticket.train.mapper.TrainMapper;
import com.ticket.train.mapper.TrainRouteStopMapper;
import com.ticket.train.route.TrainSegmentRules;
import com.ticket.train.service.TrainRouteSkuService;
import com.ticket.util.StationNameUtil;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class TrainRouteSkuServiceImpl implements TrainRouteSkuService {

    @Resource
    private TrainMapper trainMapper;

    @Resource
    private TrainRouteStopMapper trainRouteStopMapper;

    @Resource
    private TicketStockMapper ticketStockMapper;

    @Value("${train.route-search.min-transfer-minutes:20}")
    private int minTransferMinutes;

    @Override
    public ValidatedRouteSkuResponse validateSku(ValidateRouteSkuRequest request) {
        if (request == null || request.getRouteSku() == null || request.getRouteSku().isBlank()) {
            return invalid("routeSku 为空");
        }
        if (request.getTrainDate() == null || request.getSeatType() == null) {
            return invalid("日期或席别缺失");
        }
        LocalDate trainDate;
        try {
            trainDate = LocalDate.parse(request.getTrainDate());
        } catch (Exception e) {
            return invalid("乘车日期格式无效");
        }
        String sku = request.getRouteSku().trim();
        String outerStart = StationNameUtil.normalize(request.getStartStation());
        String outerEnd = StationNameUtil.normalize(request.getEndStation());
        if (outerStart.isEmpty() || outerEnd.isEmpty()) {
            return invalid("外层起讫站不能为空");
        }
        List<Train> segments = loadSegmentsBySku(sku);
        if (segments.size() != sku.split("-").length) {
            return invalid("SKU 含无效线段 id");
        }
        return validateResolvedPath(segments, outerStart, outerEnd, trainDate, request.getSeatType());
    }

    @Override
    public ValidatedRouteSkuResponse validateResolvedPath(List<Train> segmentsOrdered,
                                                          String outerStart,
                                                          String outerEnd,
                                                          LocalDate trainDate,
                                                          Integer seatType) {
        if (segmentsOrdered == null || segmentsOrdered.isEmpty()) {
            return invalid("行程为空");
        }
        String outerS = StationNameUtil.normalize(outerStart);
        String outerE = StationNameUtil.normalize(outerEnd);
        Train first = segmentsOrdered.get(0);
        Train last = segmentsOrdered.get(segmentsOrdered.size() - 1);
        if (!StationNameUtil.sameStation(first.getStartStation(), outerS)
                || !StationNameUtil.sameStation(last.getEndStation(), outerE)) {
            return invalid("外层 OD 与线段序列不符");
        }
        for (Train t : segmentsOrdered) {
            if (!Objects.equals(t.getStatus(), BusinessStatus.TRAIN_STATUS_NORMAL)) {
                return invalid("线段已停运: " + t.getId());
            }
        }

        List<TrainRouteStop> allStops = trainRouteStopMapper.selectList(null);
        Map<String, Integer> stopOrderIndex = TrainSegmentRules.buildStopOrderIndex(allStops);

        for (int i = 0; i < segmentsOrdered.size() - 1; i++) {
            Train a = segmentsOrdered.get(i);
            Train b = segmentsOrdered.get(i + 1);
            if (!StationNameUtil.sameStation(a.getEndStation(), b.getStartStation())) {
                return invalid("相邻线段无法衔接");
            }
            if (Objects.equals(a.getTrainNo(), b.getTrainNo())) {
                if (!TrainSegmentRules.sameTrainOrderOk(a, b, stopOrderIndex)) {
                    return invalid("同车次线段与停靠序不一致");
                }
            } else {
                if (!TrainSegmentRules.transferOk(a, b, trainDate, minTransferMinutes, allStops)) {
                    return invalid("换乘时间不足");
                }
            }
        }

        String routeType;
        if (segmentsOrdered.size() == 1) {
            routeType = RouteType.SINGLE;
        } else {
            String tn0 = segmentsOrdered.get(0).getTrainNo();
            boolean sameNo = segmentsOrdered.stream().allMatch(t -> Objects.equals(tn0, t.getTrainNo()));
            routeType = sameNo ? RouteType.DIRECT : RouteType.TRANSFER;
        }

        List<RouteLeg> legs = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        List<String> idParts = new ArrayList<>();

        for (Train t : segmentsOrdered) {
            String fromNorm = StationNameUtil.normalize(t.getStartStation());
            String toNorm = StationNameUtil.normalize(t.getEndStation());
            TicketStock stockRow = pickStockRow(t.getId(), trainDate, seatType, fromNorm, toNorm);
            if (stockRow == null || (stockRow.getSaleEnabled() != null && stockRow.getSaleEnabled() == 0)) {
                return invalid("区段暂未开售或不存在库存行: segment=" + t.getId());
            }
            RouteLeg leg = new RouteLeg();
            leg.setSegmentId(t.getId());
            leg.setTrainNo(t.getTrainNo());
            leg.setFromStation(fromNorm);
            leg.setToStation(toNorm);
            leg.setSegmentPrice(stockRow.getPrice());
            leg.setPlannedDepartAt(LocalDateTime.of(trainDate, t.getStartTime()));
            leg.setPlannedArriveAt(LocalDateTime.of(trainDate, t.getEndTime()));
            legs.add(leg);
            total = total.add(stockRow.getPrice());
            idParts.add(String.valueOf(t.getId()));
            if (stockRow.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                return invalid("票价无效");
            }
        }

        return ValidatedRouteSkuResponse.builder()
                .routeSku(String.join("-", idParts))
                .routeType(routeType)
                .legs(legs)
                .totalPrice(total)
                .valid(true)
                .build();
    }

    private TicketStock pickStockRow(Long segmentId, LocalDate trainDate, Integer seatType,
                                     String startNorm, String endNorm) {
        LambdaQueryWrapper<TicketStock> w = new LambdaQueryWrapper<>();
        w.eq(TicketStock::getTrainId, segmentId)
                .eq(TicketStock::getTrainDate, trainDate)
                .eq(TicketStock::getSeatType, seatType)
                .eq(TicketStock::getStartStation, startNorm)
                .eq(TicketStock::getEndStation, endNorm);
        return ticketStockMapper.selectOne(w);
    }

    private List<Train> loadSegmentsBySku(String sku) {
        String[] parts = sku.split("-");
        List<Long> ids = new ArrayList<>();
        for (String p : parts) {
            try {
                ids.add(Long.parseLong(p.trim()));
            } catch (NumberFormatException e) {
                return List.of();
            }
        }
        if (ids.isEmpty()) {
            return List.of();
        }
        List<Train> list = trainMapper.selectBatchIds(ids);
        Map<Long, Train> byId = list.stream().collect(Collectors.toMap(Train::getId, t -> t, (a, b) -> a));
        List<Train> ordered = new ArrayList<>();
        for (Long id : ids) {
            Train t = byId.get(id);
            if (t == null) {
                return List.of();
            }
            ordered.add(t);
        }
        return ordered;
    }

    private ValidatedRouteSkuResponse invalid(String reason) {
        return ValidatedRouteSkuResponse.builder()
                .valid(false)
                .reason(reason)
                .build();
    }
}
