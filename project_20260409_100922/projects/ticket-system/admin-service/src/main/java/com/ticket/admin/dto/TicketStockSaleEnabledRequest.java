package com.ticket.admin.dto;

import lombok.Data;

@Data
public class TicketStockSaleEnabledRequest {
    /** 1 开售，0 停售 */
    private Integer saleEnabled;
}
