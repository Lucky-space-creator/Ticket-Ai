package com.ticket.aichat.client;

import com.ticket.util.ResponseUtil;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 用户域只读 Feign（依赖网关/客户端传入的 JWT，与 user-service 控制器一致）
 */
@FeignClient(name = "user-service", contextId = "aichatUserReadClient")
public interface UserReadFeignClient {

    @GetMapping("/api/user/profile")
    ResponseUtil.Result<?> getProfile();

    @GetMapping("/api/passengers")
    ResponseUtil.Result<?> listPassengers();
}
