package com.ticket.admin.infrastructure.client;

import com.ticket.dto.internal.OrderRefundCommand;
import com.ticket.util.ResponseUtil;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 出站端口：退票编排归属订单限界上下文，管理端仅发起命令。
 */
@FeignClient(name = "order-service", contextId = "adminOrderRefundClient")
public interface OrderRefundFeignClient {

    @PostMapping("/api/internal/orders/refund")
    ResponseUtil.Result<Void> refund(@RequestBody OrderRefundCommand command);
}
