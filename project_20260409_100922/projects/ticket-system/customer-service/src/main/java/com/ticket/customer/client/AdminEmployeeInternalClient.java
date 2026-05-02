package com.ticket.customer.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "admin-service", contextId = "adminEmployeeInternalClient")
public interface AdminEmployeeInternalClient {

    @GetMapping("/api/internal/employees/available-id")
    Long availableEmployeeId();
}
