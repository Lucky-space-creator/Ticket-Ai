package com.ticket.dto;

import com.ticket.dto.internal.TrainStockCommand;
import com.ticket.entity.OrderRouteLeg;
import com.ticket.util.StationNameUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * 构造库存操作命令（线段 id + 规范站名 + 席别/日期）；各段票数必须一致以满足 batch Lua。
 */
public final class TrainStockCommands {

    private TrainStockCommands() {
    }

    public static List<TrainStockCommand> fromRouteLegs(List<RouteLeg> legs,
                                                        String trainDate,
                                                        Integer seatType,
                                                        int passengerCount) {
        if (legs == null || legs.isEmpty()) {
            return List.of();
        }
        List<TrainStockCommand> list = new ArrayList<>(legs.size());
        for (RouteLeg leg : legs) {
            list.add(new TrainStockCommand(
                    leg.getSegmentId(),
                    trainDate,
                    StationNameUtil.normalize(leg.getFromStation()),
                    StationNameUtil.normalize(leg.getToStation()),
                    seatType,
                    passengerCount));
        }
        return list;
    }

    public static List<TrainStockCommand> fromOrderRouteLegs(List<OrderRouteLeg> legs,
                                                            String trainDate,
                                                            Integer seatType,
                                                            int passengerCount) {
        if (legs == null || legs.isEmpty()) {
            return List.of();
        }
        legs.sort(java.util.Comparator.comparing(OrderRouteLeg::getLegSeq));
        List<RouteLeg> dto = new ArrayList<>(legs.size());
        for (OrderRouteLeg ol : legs) {
            RouteLeg r = new RouteLeg();
            r.setSegmentId(ol.getSegmentTrainId());
            r.setTrainNo(ol.getTrainNo());
            r.setFromStation(ol.getFromStation());
            r.setToStation(ol.getToStation());
            dto.add(r);
        }
        return fromRouteLegs(dto, trainDate, seatType, passengerCount);
    }
}
