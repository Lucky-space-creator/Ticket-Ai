package com.ticket.train.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.dto.RouteLeg;
import com.ticket.dto.train.RouteValidationRequest;
import com.ticket.dto.train.RouteValidationResult;
import com.ticket.entity.TicketStock;
import com.ticket.entity.Train;
import com.ticket.entity.TrainRouteStop;
import com.ticket.enums.RouteType;
import com.ticket.train.mapper.TicketStockMapper;
import com.ticket.train.mapper.TrainMapper;
import com.ticket.train.mapper.TrainRouteStopMapper;
import com.ticket.train.route.TrainSegmentRules;
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

/**
 * 校验客户端提交的行程与票价，防止篡改 SKU / OD。
 */
@Service
public class RouteBookingValidationService {

    @Resource
    private TrainMapper trainMapper;

    @Resource
    private TrainRouteStopMapper trainRouteStopMapper;

    @Resource
    private TicketStockMapper ticketStockMapper;

    @Value("${train.route-search.min-transfer-minutes:20}")
    private int minTransferMinutes;

    public RouteValidationResult validate(RouteValidationRequest req) {
        if (req == null || req.getLegs() == null || req.getLegs().isEmpty()) {
            throw new IllegalArgumentException("行程段不能为空");
        }
        LocalDate trainDate = LocalDate.parse(req.getTrainDate());
        Integer seatType = req.getSeatType();
        String outerFrom = StationNameUtil.normalize(req.getStartStation());
        String outerTo = StationNameUtil.normalize(req.getEndStation());

        List<TrainRouteStop> stops = trainRouteStopMapper.selectList(null);
        Map<String, Integer> stopOrder = TrainSegmentRules.buildStopOrderIndex(stops);

        List<Train> path = new ArrayList<>();
        for (RouteLeg leg : req.getLegs()) {
            if (leg.getSegmentId() == null) {
                throw new IllegalArgumentException("线段 id 不能为空");
            }
            Train t = trainMapper.selectById(leg.getSegmentId());
            if (t == null || !Objects.equals(t.getStatus(), 1)) {
                throw new IllegalArgumentException("车次线段不可用: " + leg.getSegmentId());
            }
            path.add(t);
        }

        Train first = path.get(0);
        Train last = path.get(path.size() - 1);
        if (!StationNameUtil.normalize(first.getStartStation()).equals(outerFrom)
                || !StationNameUtil.normalize(last.getEndStation()).equals(outerTo)) {
            throw new IllegalArgumentException("行程与起讫站点不一致");
        }

        if (req.getRouteSku() != null && !req.getRouteSku().isBlank()) {
            String expected = path.stream().map(Train::getId).map(String::valueOf).collect(Collectors.joining("-"));
            if (!expected.equals(req.getRouteSku().trim())) {
                throw new IllegalArgumentException("route_sku 与线段序列不一致");
            }
        }

        for (int i = 1; i < path.size(); i++) {
            Train prev = path.get(i - 1);
            Train next = path.get(i);
            boolean sameTrain = Objects.equals(prev.getTrainNo(), next.getTrainNo());
            boolean ok = sameTrain
                    ? TrainSegmentRules.sameTrainOrderOk(prev, next, stopOrder)
                    : TrainSegmentRules.transferOk(prev, next, trainDate, minTransferMinutes);
            if (!ok) {
                throw new IllegalArgumentException(sameTrain ? "同车线段衔接不合法" : "换乘衔接或时刻不满足规则");
            }
        }

        String expectedType;
        if (path.size() == 1) {
            expectedType = RouteType.SINGLE;
        } else {
            String tn0 = path.get(0).getTrainNo();
            boolean allSame = path.stream().allMatch(t -> Objects.equals(tn0, t.getTrainNo()));
            expectedType = allSame ? RouteType.DIRECT : RouteType.TRANSFER;
        }
        if (req.getRouteType() != null && !req.getRouteType().isBlank()
                && !expectedType.equals(req.getRouteType())) {
            throw new IllegalArgumentException("route_type 与线段组合不一致");
        }

        BigDecimal sum = BigDecimal.ZERO;
        List<RouteLeg> out = new ArrayList<>();
        for (Train t : path) {
            LambdaQueryWrapper<TicketStock> w = new LambdaQueryWrapper<>();
            w.eq(TicketStock::getTrainId, t.getId())
                    .eq(TicketStock::getTrainDate, trainDate)
                    .eq(TicketStock::getStartStation, StationNameUtil.normalize(t.getStartStation()))
                    .eq(TicketStock::getEndStation, StationNameUtil.normalize(t.getEndStation()))
                    .eq(TicketStock::getSeatType, seatType);
            TicketStock stock = ticketStockMapper.selectOne(w);
            if (stock == null) {
                throw new IllegalArgumentException("库存记录不存在: 线段 " + t.getId());
            }
            if (stock.getSaleEnabled() != null && stock.getSaleEnabled() == 0) {
                throw new IllegalArgumentException("该线段已停售");
            }
            sum = sum.add(stock.getPrice());
            RouteLeg rl = new RouteLeg();
            rl.setSegmentId(t.getId());
            rl.setTrainNo(t.getTrainNo());
            rl.setFromStation(StationNameUtil.normalize(t.getStartStation()));
            rl.setToStation(StationNameUtil.normalize(t.getEndStation()));
            rl.setSegmentPrice(stock.getPrice());
            rl.setPlannedDepartAt(LocalDateTime.of(trainDate, t.getStartTime()));
            rl.setPlannedArriveAt(LocalDateTime.of(trainDate, t.getEndTime()));
            out.add(rl);
        }

        RouteValidationResult res = new RouteValidationResult();
        res.setLegs(out);
        res.setTotalPricePerPassenger(sum);
        return res;
    }
}
