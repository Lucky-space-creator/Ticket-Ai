package com.ticket.train.service;

import com.ticket.dto.ValidateRouteSkuRequest;
import com.ticket.dto.ValidatedRouteSkuResponse;
import com.ticket.entity.Train;

import java.time.LocalDate;
import java.util.List;

public interface TrainRouteSkuService {

    ValidatedRouteSkuResponse validateSku(ValidateRouteSkuRequest request);

    /**
     * SKU 或服务端已定序的线段，与外层 OD / 停靠规则 / 库存行校验一致。
     */
    ValidatedRouteSkuResponse validateResolvedPath(List<Train> segmentsOrdered,
                                                   String outerStart,
                                                   String outerEnd,
                                                   LocalDate trainDate,
                                                   Integer seatType);
}
