package com.ticket.train.route;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.dto.train.RouteSearchOption;
import com.ticket.entity.TicketStock;
import com.ticket.entity.Train;
import com.ticket.entity.TrainRouteStop;
import com.ticket.enums.RouteType;
import com.ticket.train.mapper.TicketStockMapper;
import com.ticket.train.mapper.TrainMapper;
import com.ticket.train.mapper.TrainRouteStopMapper;
import com.ticket.util.StationNameUtil;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 线段图上的联程搜索（BFS + 最大深度/候选数/耗时限制）。
 */
@Component
public class RouteSearchPlanner {

    @Resource
    private TrainMapper trainMapper;

    @Resource
    private TrainRouteStopMapper trainRouteStopMapper;

    @Resource
    private TicketStockMapper ticketStockMapper;

    @Value("${train.route-search.min-transfer-minutes:20}")
    private int minTransferMinutes;

    @Value("${train.route-search.max-legs:4}")
    private int maxLegs;

    @Value("${train.route-search.max-results:25}")
    private int maxResults;

    @Value("${train.route-search.max-bfs-expand:8000}")
    private int maxBfsExpand;

    @Value("${train.route-search.timeout-ms:800}")
    private long timeoutMs;

    /**
     * 搜索联乘/直达的坐车方案，使用bfs算法，不用dfs，避免死循环报栈溢出
     * @param rawFrom 出发站
     * @param rawTo 终点站
     * @param trainDate  日期
     * @param seatType 座位类型
     * @return 搜索结果
     */
    public List<RouteSearchOption> search(String rawFrom, String rawTo, LocalDate trainDate, Integer seatType) {
        String from = StationNameUtil.normalize(rawFrom);
        String to = StationNameUtil.normalize(rawTo);
        if (from.isEmpty() || to.isEmpty() || from.equals(to)) {
            return List.of();
        }

        List<Train> all = trainMapper.selectList(new LambdaQueryWrapper<Train>().eq(Train::getStatus, 1));
        //获取所有站点的站序索引
        List<TrainRouteStop> routeStops = trainRouteStopMapper.selectList(null);
        Map<String, Integer> stopOrderIndex = TrainSegmentRules.buildStopOrderIndex(routeStops);
        //维护搜索的所有列车
        Map<String, List<Train>> byStart = new HashMap<>();
        for (Train t : all) {
            String k = StationNameUtil.normalize(t.getStartStation());
            byStart.computeIfAbsent(k, x -> new ArrayList<>()).add(t);
        }

        //搜索结果
        List<List<Train>> found = new ArrayList<>();
        //维护搜索路径的队列
        ArrayDeque<List<Train>> q = new ArrayDeque<>();
        // 超时时间
        long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
        int expand = 0;

        //通过byStart获取起点出发的所有列车，对每个列车构建一条路径，添加到队列中
        for (Train first : byStart.getOrDefault(from, List.of())) {
            ArrayList<Train> p0 = new ArrayList<>();
            p0.add(first);
            q.add(p0);
        }

        while (!q.isEmpty() && found.size() < maxResults && expand < maxBfsExpand) {
            if (System.nanoTime() > deadline) {
                break;
            }
            expand++;
            // 当前路径
            List<Train> path = q.poll();
            if (path == null) {
                break;
            }
            Train last = path.get(path.size() - 1);
            String at = StationNameUtil.normalize(last.getEndStation());
            // 到达终点，添加结果，继续搜索
            if (at.equals(to)) {
                found.add(new ArrayList<>(path));
                continue;
            }
            //超出最大深度限制，跳过
            if (path.size() >= maxLegs) {
                continue;
            }
            // 循环byStart的所有列车，判断是否可以继续扩展到当前路径中，满足条件则加入队列
            for (Train next : byStart.getOrDefault(at, List.of())) {
                if (!canExtend(path, next, trainDate, stopOrderIndex)) {
                    continue;
                }
                List<Train> np = new ArrayList<>(path);
                np.add(next);
                q.add(np);
            }
        }

        List<RouteSearchOption> options = new ArrayList<>();
        for (List<Train> path : found) {
            RouteSearchOption opt = buildOption(path, trainDate, seatType);
            if (opt != null && opt.getMinAvailableSeats() != null && opt.getMinAvailableSeats() > 0) {
                options.add(opt);
            }
        }
        options.sort(Comparator
                .comparing((RouteSearchOption o) -> typeRank(o.getRouteType()))
                .thenComparing(o -> o.getTotalDurationMinutes() == null ? Long.MAX_VALUE : o.getTotalDurationMinutes())
                .thenComparing(o -> o.getTotalPrice() == null ? BigDecimal.ZERO : o.getTotalPrice()));
        return options.stream().limit(maxResults).collect(Collectors.toList());
    }

    private static int typeRank(String t) {
        if (RouteType.SINGLE.equals(t)) {
            return 0;
        }
        if (RouteType.DIRECT.equals(t)) {
            return 1;
        }
        return 2;
    }

    /**
     * 判断是否可继续扩展
     * @param path  当前存的线路
     * @param next 下一站
     * @param trainDate 日期
     * @param stopOrderIndex 站序索引
     * @return 是否可继续扩展
     */
    private boolean canExtend(List<Train> path, Train next, LocalDate trainDate, Map<String, Integer> stopOrderIndex) {
        if (path.isEmpty()) {
            return true;
        }
        Train prev = path.get(path.size() - 1);
        if (Objects.equals(prev.getTrainNo(), next.getTrainNo())) {
            return TrainSegmentRules.sameTrainOrderOk(prev, next, stopOrderIndex);
        }
        return TrainSegmentRules.transferOk(prev, next, trainDate, minTransferMinutes);
    }

    /**
     * 构建搜索结果成对象
     * @param path 线路
     * @param trainDate  日期
     * @param seatType 座位类型
     * @return 搜索结果对象
     */
    private RouteSearchOption buildOption(List<Train> path, LocalDate trainDate, Integer seatType) {
        if (path.isEmpty()) {
            return null;
        }
        String sku = path.stream().map(Train::getId).map(String::valueOf).collect(Collectors.joining("-"));
        String routeType;
        if (path.size() == 1) {
            routeType = RouteType.SINGLE;
        } else {
            String tn0 = path.get(0).getTrainNo();
            boolean same = path.stream().allMatch(t -> Objects.equals(tn0, t.getTrainNo()));
            routeType = same ? RouteType.DIRECT : RouteType.TRANSFER;
        }

        List<RouteSearchOption.RouteSearchLeg> legs = new ArrayList<>();
        int minAvail = Integer.MAX_VALUE;
        BigDecimal total = BigDecimal.ZERO;

        LocalDateTime firstDepart = null;
        LocalDateTime lastArrive = null;

        for (Train t : path) {
            LambdaQueryWrapper<TicketStock> w = new LambdaQueryWrapper<>();
            w.eq(TicketStock::getTrainId, t.getId())
                    .eq(TicketStock::getTrainDate, trainDate)
                    .eq(TicketStock::getStartStation, StationNameUtil.normalize(t.getStartStation()))
                    .eq(TicketStock::getEndStation, StationNameUtil.normalize(t.getEndStation()))
                    .eq(TicketStock::getSeatType, seatType);
            TicketStock stock = ticketStockMapper.selectOne(w);
            if (stock == null || stock.getAvailableSeats() == null) {
                return null;
            }
            if (stock.getSaleEnabled() != null && stock.getSaleEnabled() == 0) {
                return null;
            }
            minAvail = Math.min(minAvail, stock.getAvailableSeats());
            total = total.add(stock.getPrice());

            legs.add(new RouteSearchOption.RouteSearchLeg(
                    t.getId(), t.getTrainNo(), t.getStartStation(), t.getEndStation(),
                    formatTime(t.getStartTime()), formatTime(t.getEndTime()),
                    stock.getPrice(), stock.getAvailableSeats()));

            LocalDateTime dep = LocalDateTime.of(trainDate, t.getStartTime());
            LocalDateTime arr = LocalDateTime.of(trainDate, t.getEndTime());
            if (firstDepart == null) {
                firstDepart = dep;
            }
            lastArrive = arr;
        }

        Long durMin = null;
        if (firstDepart != null && lastArrive != null) {
            durMin = Duration.between(firstDepart, lastArrive).toMinutes();
            if (durMin < 0) {
                durMin = 0L;
            }
        }

        RouteSearchOption opt = new RouteSearchOption();
        opt.setRouteType(routeType);
        opt.setRouteSku(sku);
        opt.setLegs(legs);
        opt.setMinAvailableSeats(minAvail == Integer.MAX_VALUE ? 0 : minAvail);
        opt.setTotalPrice(total);
        opt.setTotalDurationMinutes(durMin);
        return opt;
    }

    private static String formatTime(LocalTime t) {
        return t == null ? null : t.toString();
    }
}
