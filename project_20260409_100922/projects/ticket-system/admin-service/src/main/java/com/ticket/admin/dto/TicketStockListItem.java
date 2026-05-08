package com.ticket.admin.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class TicketStockListItem {
    private Long id;
    private Long trainId;
    private String trainNo;
    private LocalDate trainDate;
    private String startStation;
    private String endStation;
    private Integer seatType;
    private BigDecimal price;
    private Integer totalSeats;
    private Integer availableSeats;
    private Integer saleEnabled;
    private Integer version;
    private LocalDateTime updatedAt;
}
