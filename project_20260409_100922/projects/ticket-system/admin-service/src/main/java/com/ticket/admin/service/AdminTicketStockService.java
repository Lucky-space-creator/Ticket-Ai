package com.ticket.admin.service;

import com.ticket.admin.dto.TicketStockPageResult;
import com.ticket.admin.dto.TicketStockSaleEnabledRequest;
import com.ticket.admin.dto.TicketStockSeatsRequest;

import java.time.LocalDate;

/**
 * 管理端余票库存（ticket_stock）业务。
 */
public interface AdminTicketStockService {

    TicketStockPageResult page(long current, long size, Long trainId, LocalDate trainDate, String trainNo, Integer saleEnabled);

    void updateSeats(Long id, TicketStockSeatsRequest body);

    void updateSaleEnabled(Long id, TicketStockSaleEnabledRequest body);
}
