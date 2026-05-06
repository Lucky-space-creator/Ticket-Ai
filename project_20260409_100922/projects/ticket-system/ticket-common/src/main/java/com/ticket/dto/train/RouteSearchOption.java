package com.ticket.dto.train;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 联程/直达搜索返回的一条方案。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RouteSearchOption implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** DIRECT / TRANSFER / SINGLE */
    private String routeType;

    /** 线段 id 以 '-' 拼接 */
    private String routeSku;

    private List<RouteSearchLeg> legs = new ArrayList<>();

    /** 各段可用余量最小值 */
    private Integer minAvailableSeats;

    private BigDecimal totalPrice;

    /** 跨站总时长（分钟），同车多段或含换乘的估计值 */
    private Long totalDurationMinutes;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RouteSearchLeg implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private Long segmentId;
        private String trainNo;
        private String fromStation;
        private String toStation;
        private String startTime;
        private String endTime;
        private BigDecimal price;
        private Integer availableSeats;
    }
}
