package com.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 验证车次线路库存结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidatedRouteSkuResponse {

    /**
     * 车次线路
     */
    private String routeSku;

    /**
     * 线路类型
     */
    private String routeType;

    /**
     * 线路片段
     */
    private List<RouteLeg> legs;

    /**
     * 线路总价
     */
    private BigDecimal totalPrice;

    /**
     * 是否有效
     */
    private Boolean valid;

    /**
     * 失败原因
     */
    private String reason;
}
