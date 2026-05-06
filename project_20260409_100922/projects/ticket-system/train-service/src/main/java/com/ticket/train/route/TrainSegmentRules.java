package com.ticket.train.route;

import com.ticket.entity.Train;
import com.ticket.entity.TrainRouteStop;
import com.ticket.util.StationNameUtil;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 同车衔接 / 换乘时刻校验。
 */
public final class TrainSegmentRules {

    private TrainSegmentRules() {
    }

    public static Map<String, Integer> buildStopOrderIndex(List<TrainRouteStop> stops) {
        Map<String, Integer> m = new HashMap<>();
        for (TrainRouteStop s : stops) {
            String key = stopKey(s.getTrainNo(), s.getStationName());
            m.put(key, s.getStationNo());
        }
        return m;
    }

    private static String stopKey(String trainNo, String stationName) {
        return trainNo + "|" + StationNameUtil.normalize(stationName);
    }

    /**
     * 同车：上一段终点须等于下一段起点；若停靠表能解析出站序则需 a&lt;b&lt;c，否则仅靠线段首尾衔接兜底（避免因未维护 train_route_stop 而无法搜路径）。
     */
    public static boolean sameTrainOrderOk(Train prev, Train next, Map<String, Integer> orderIndex) {
        if (!Objects.equals(prev.getTrainNo(), next.getTrainNo())) {
            return false;
        }
        if (!StationNameUtil.normalize(prev.getEndStation()).equals(StationNameUtil.normalize(next.getStartStation()))) {
            return false;
        }
        Integer a = orderIndex.get(stopKey(prev.getTrainNo(), prev.getStartStation()));
        Integer b = orderIndex.get(stopKey(prev.getTrainNo(), prev.getEndStation()));
        Integer c = orderIndex.get(stopKey(next.getTrainNo(), next.getEndStation()));
        if (a != null && b != null && c != null) {
            if (a >= b || b >= c) {
                return false;
            }
            return true;
        }
        return true;
    }

    /**
     * 换乘：两段不同车次、同一接续站；
     * 若下一段在乘车日的发车时刻早于或等于上一段到达，则视作<strong>次日</strong>发车再算候车分钟（仅存 LocalTime 的跨日车次常见）。
     */
    public static boolean transferOk(Train prev, Train next, LocalDate trainDate, int minTransferMinutes) {
        if (Objects.equals(prev.getTrainNo(), next.getTrainNo())) {
            return false;
        }
        if (!StationNameUtil.normalize(prev.getEndStation()).equals(StationNameUtil.normalize(next.getStartStation()))) {
            return false;
        }
        LocalDateTime arrive = at(trainDate, prev.getEndTime());
        LocalDateTime depart = at(trainDate, next.getStartTime());
        if (arrive == null || depart == null) {
            return false;
        }
        LocalDateTime departEff = depart;
        if (!departEff.isAfter(arrive)) {
            departEff = departEff.plusDays(1);
        }
        long need = Duration.between(arrive, departEff).toMinutes();
        return need >= minTransferMinutes;
    }

    private static LocalDateTime at(LocalDate d, LocalTime t) {
        if (d == null || t == null) {
            return null;
        }
        return LocalDateTime.of(d, t);
    }
}
