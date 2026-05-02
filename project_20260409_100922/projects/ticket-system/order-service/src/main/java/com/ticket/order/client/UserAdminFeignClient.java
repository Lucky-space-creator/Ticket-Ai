package com.ticket.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 订单管理分页按手机号查用户时，调用用户域（与 backend 中按 User 表模糊查询逻辑一致）
 */
@FeignClient(name = "user-service", contextId = "userAdminFeignClient", path = "/api/internal/users")
public interface UserAdminFeignClient {

    @GetMapping("/ids-by-phone")
    List<Long> listUserIdsByPhone(@RequestParam("phone") String phone);
}
