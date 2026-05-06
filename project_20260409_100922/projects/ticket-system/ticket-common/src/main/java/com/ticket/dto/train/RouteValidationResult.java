package com.ticket.dto.train;

import com.ticket.dto.RouteLeg;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
public class RouteValidationResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<RouteLeg> legs;

    private BigDecimal totalPricePerPassenger;
}
