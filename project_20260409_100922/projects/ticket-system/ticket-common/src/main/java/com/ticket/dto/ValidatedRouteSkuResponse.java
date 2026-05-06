package com.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidatedRouteSkuResponse {

    private String routeSku;

    private String routeType;

    private List<RouteLeg> legs;

    private BigDecimal totalPrice;

    private Boolean valid;

    private String reason;
}
