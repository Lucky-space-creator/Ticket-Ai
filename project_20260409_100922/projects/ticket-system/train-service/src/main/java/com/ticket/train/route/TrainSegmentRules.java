package com.ticket.train.route;

import com.ticket.entity.Train;
import com.ticket.entity.TrainRouteStop;
import com.ticket.util.StationNameUtil;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 同车衔接 / 换乘时刻校验。
 */
public final class TrainSegmentRules {

    /** 与时间戳换算用固定时区，避免默认时区漂移 */
    private static final ZoneId CHINA_ZONE = ZoneId.of("Asia/Shanghai");

    private TrainSegmentRules() {
    }

    /**
     * 构建站序索引
     */
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
     * 同车：上一段终点须等于下一段起点；若停靠表能解析出站序则需 a&lt;b&lt;c，否则仅靠线段首尾衔接兜底。
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
            return a < b && b < c;
        }
        return true;
    }

    /**
     * 换乘：未传停靠表时用车次线段表首尾时刻。
     */
    public static boolean transferOk(Train prev, Train next, LocalDate trainDate, int minTransferMinutes) {
        return transferOk(prev, next, trainDate, minTransferMinutes, null);
    }

    /**
     * 换乘：两段不同车次、同一接续站。
     * <p>上一程<strong>到达换乘站</strong>的时刻与下一程<strong>从换乘站发车</strong>的时刻，统一换成当天（或顺延次日发车）毫秒时间戳比较：
     * 下一程有效发车时间不早于「上一程到达 + 最小换乘时间」。</p>
     * @param prev 上一段
     * @param next 下一段
     * @param trainDate 日期
     * @param minTransferMinutes 最小换乘时间
     * @param stops 停靠表
     * @return 是否可继续扩展
     */
    public static boolean transferOk(Train prev,
                                       Train next,
                                       LocalDate trainDate,
                                       int minTransferMinutes,
                                       List<TrainRouteStop> stops) {
        // 如果是同车，则不能继续 这里不可能出现，因为在canExtend方法中已经判断了
        if (Objects.equals(prev.getTrainNo(), next.getTrainNo())) {
            return false;
        }
        // 换乘站必须是同一站
        if (!StationNameUtil.normalize(prev.getEndStation()).equals(StationNameUtil.normalize(next.getStartStation()))) {
            return false;
        }
        LocalTime arriveT = resolveArrivalAtEndStation(prev, stops);
        LocalTime departT = resolveDepartFromStartStation(next, stops);
        if (arriveT == null || departT == null) {
            return false;
        }

        long arriveMs = toEpochMilli(trainDate, arriveT);
        long departMs = toEpochMilli(trainDate, departT);
        long minGapMs = (long) minTransferMinutes * 60_000L;

        // 同一天内满足换成条件
        return departMs - arriveMs >= minGapMs;

        /**
         * 不足跨天处理，对于跨天的车票管理复杂
         * 这里仅仅制作一个当天车票的处理
         */

    }

    private static long toEpochMilli(LocalDate date, LocalTime time) {
        return date.atTime(time)  // 1. 合并日期和时间
                .atZone(CHINA_ZONE)   // 2. 添加时区信息（中国时区，东八区）
                .toInstant()  // 3. 转换为 UTC 时间线上的 Instant
                .toEpochMilli(); // 4. 获取毫秒时间戳
    }

    /**
     * 获取停靠表某车次某站信息
     * @param stops 停靠表
     * @param trainNo 车次
     * @param stationNorm 站名
     * @return 停靠表某车次某站信息
     */
    private static TrainRouteStop findStop(List<TrainRouteStop> stops, String trainNo, String stationNorm) {
        if (stops == null || trainNo == null || stationNorm == null) {
            return null;
        }
        for (TrainRouteStop s : stops) {
            if (s == null || s.getTrainNo() == null || s.getStationName() == null) {
                continue;
            }
            if (Objects.equals(trainNo.trim(), s.getTrainNo().trim())
                    && StationNameUtil.normalize(s.getStationName()).equals(stationNorm)) {
                return s;
            }
        }
        return null;
    }

    // 通过车辆停靠站序表获得当前车的到达时间
    private static LocalTime resolveArrivalAtEndStation(Train segment, List<TrainRouteStop> stops) {
        String endStation = StationNameUtil.normalize(segment.getEndStation());
        TrainRouteStop row = findStop(stops, segment.getTrainNo(), endStation);
        if (row != null) {
            if (row.getArriveTime() != null) {
                return row.getArriveTime();
            }
            if (row.getDepartTime() != null) {
                return row.getDepartTime();
            }
        }
        return segment.getEndTime();
    }

    // 同车：未传停靠表时用车次线段表首时刻
    private static LocalTime resolveDepartFromStartStation(Train segment, List<TrainRouteStop> stops) {
        String startNorm = StationNameUtil.normalize(segment.getStartStation());
        TrainRouteStop row = findStop(stops, segment.getTrainNo(), startNorm);
        if (row != null && row.getDepartTime() != null) {
            return row.getDepartTime();
        }
        return segment.getStartTime();
    }
}
