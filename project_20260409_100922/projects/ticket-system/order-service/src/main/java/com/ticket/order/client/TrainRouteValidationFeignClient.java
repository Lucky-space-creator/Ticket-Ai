package com.ticket.order.client;

import com.ticket.dto.train.RouteValidationRequest;
import com.ticket.dto.train.RouteValidationResult;
import com.ticket.util.ResponseUtil;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "train-service", contextId = "trainRouteValidationFeignClient", path = "/api/trains/internal/routes")
public interface TrainRouteValidationFeignClient {

    @PostMapping("/validate")
    ResponseUtil.Result<RouteValidationResult> validate(@RequestBody RouteValidationRequest request);
}
