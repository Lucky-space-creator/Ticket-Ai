package com.ticket.admin.dto;

import lombok.Data;

import java.util.List;

@Data
public class TicketStockPageResult {
    private List<TicketStockListItem> records;
    private long total;
    private long current;
    private long size;
}
