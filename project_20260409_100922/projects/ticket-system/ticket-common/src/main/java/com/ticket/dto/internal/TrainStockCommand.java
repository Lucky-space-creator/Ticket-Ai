package com.ticket.dto.internal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 订单服务调用车次服务时的库存/车次操作参数（跨服务边界 DTO）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrainStockCommand {

    private Long trainId;
    private String trainDate;
    private String startStation;
    private String endStation;
    private Integer seatType;
    private Integer count;
}
