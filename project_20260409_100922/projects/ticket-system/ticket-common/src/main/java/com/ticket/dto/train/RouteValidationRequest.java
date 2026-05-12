package com.ticket.dto.train;

import com.ticket.dto.RouteLeg;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 路径校验请求
 */
@Data
public class RouteValidationRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String trainDate;

    private Integer seatType;

    private String startStation;

    private String endStation;

    private String routeSku;

    /** 与 ValidateRouteSkuResponse / 前端一致：DIRECT | TRANSFER | SINGLE */
    private String routeType;

    private List<RouteLeg> legs;
}
