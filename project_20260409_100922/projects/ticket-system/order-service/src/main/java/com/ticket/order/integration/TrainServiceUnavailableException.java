package com.ticket.order.integration;

/**
 * train-service 不可用异常（熔断打开 / 超时 / 调用失败）。
 * 由 OrderController 的 catch(RuntimeException) 统一转成「服务暂不可用，请稍后重试」提示。
 */
public class TrainServiceUnavailableException extends RuntimeException {

    public TrainServiceUnavailableException(String message) {
        super(message);
    }

    public TrainServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
