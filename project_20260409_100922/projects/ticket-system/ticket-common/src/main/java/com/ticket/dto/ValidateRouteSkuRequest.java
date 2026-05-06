package com.ticket.dto;

import lombok.Data;

@Data
public class ValidateRouteSkuRequest {

    private String routeSku;

    /** 外层出发站（用户票面） */
    private String startStation;

    /** 外层到达站 */
    private String endStation;

    private String trainDate;

    private Integer seatType;
}
