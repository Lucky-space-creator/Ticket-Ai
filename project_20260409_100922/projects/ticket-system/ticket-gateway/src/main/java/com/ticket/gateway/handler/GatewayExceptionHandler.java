package com.ticket.gateway.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 网关全局异常处理，返回统一 JSON 格式。
 * <p>
 * 格式：{"code": xxx, "message": "...", "data": null, "timestamp": xxx}
 */
@Slf4j
@Order(-1)  //确保在SpringBootErrorWebExceptionHandler之前执行
@Component
@RequiredArgsConstructor
public class GatewayExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        int code;
        String message;

        if (ex instanceof ResponseStatusException rse) {
            code = rse.getStatusCode().value();
            message = switch (code) {
                case 404 -> "接口不存在";
                case 503 -> "服务暂时不可用，请稍后重试";
                case 429 -> "请求过于频繁，请稍后重试";
                default -> rse.getReason() != null ? rse.getReason() : "网关处理异常";
            };
        } else {
            code = HttpStatus.INTERNAL_SERVER_ERROR.value();
            message = "网关内部错误";
            log.error("[GATEWAY] 未处理异常: {}", ex.getMessage(), ex);
        }

        response.setStatusCode(HttpStatus.valueOf(code));
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        body.put("data", null);
        body.put("timestamp", System.currentTimeMillis());

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            bytes = "{\"code\":500,\"message\":\"序列化异常\",\"data\":null,\"timestamp\":0}".getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }
}
