package com.ticket.train.controller;

import com.ticket.dto.train.RouteValidationRequest;
import com.ticket.dto.train.RouteValidationResult;
import com.ticket.train.service.RouteBookingValidationService;
import com.ticket.util.ResponseUtil;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 行程校验（供 order 下单前调用）
 */
@RestController
@RequestMapping("/api/trains/internal/routes")
public class TrainInternalRouteController {

    @Resource
    private RouteBookingValidationService routeBookingValidationService;

    /**
     * 校验行程
     * @param body 行程校验请求
     * @return  行程校验响应
     */
    @PostMapping("/validate")
    public ResponseUtil.Result<RouteValidationResult> validate(@RequestBody RouteValidationRequest body) {
        try {
            return ResponseUtil.success(routeBookingValidationService.validate(body));
        } catch (IllegalArgumentException e) {
            return ResponseUtil.error(e.getMessage());
        }
    }
}
