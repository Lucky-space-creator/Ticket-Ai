package com.ticket.customer.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 内部调用员工服务接口
 * 作用：员工服务调用员工服务接口，获取员工ID,给客户端返回
 */
@FeignClient(name = "admin-service", contextId = "adminEmployeeInternalClient")
public interface AdminEmployeeInternalClient {

    @GetMapping("/api/internal/employees/available-id")
    Long availableEmployeeId();
}
