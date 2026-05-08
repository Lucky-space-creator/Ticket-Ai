package com.ticket.admin.dto;

import lombok.Data;

@Data
public class TicketStockSeatsRequest {
    private Integer totalSeats;
    private Integer availableSeats;
}
