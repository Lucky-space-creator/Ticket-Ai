package com.ticket.order.integration;

import com.ticket.dto.ValidateRouteSkuRequest;
import com.ticket.dto.ValidatedRouteSkuResponse;
import com.ticket.dto.internal.TrainStockCommand;
import com.ticket.entity.Train;
import com.ticket.order.client.TrainOrderFeignClient;
import com.ticket.order.config.TrainServiceCircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * 订单业务访问「车次/库存」能力的唯一入口，内部通过 Feign 调 train-service（对齐 backend 行为，降低与车次实现类的耦合）
 *
 * 熔断降级策略：对入队路径上的同步 Feign 调用（查价 getSeatPrice、预扣 deductStock(sBatch)）
 * 用 CircuitBreaker + TimeLimiter 装饰。熔断打开 / 超时时直接抛出 TrainServiceUnavailableException，
 * 由 OrderController 统一转成友好提示；绝不降级为「绕过 MQ 的同步写单」，以保住异步削峰架构。
 * 补偿方法（rollback / reservationRollback / confirm）不装饰，需保证执行（靠 MQ 重试保证最终一致）。
 */
@Component
public class TrainOrderGateway {

    private static final Logger logger = LoggerFactory.getLogger(TrainOrderGateway.class);

    private static final String TRAIN_SERVICE_UNAVAILABLE = "车次服务暂不可用，请稍后重试";

    @Resource
    private TrainOrderFeignClient trainOrderFeignClient;

    @Resource
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Resource
    private TimeLimiterRegistry timeLimiterRegistry;

    @Resource(name = "trainServiceCbExecutor")
    private Executor trainServiceCbExecutor;

    public ValidatedRouteSkuResponse validateRoute(ValidateRouteSkuRequest request) {
        return trainOrderFeignClient.validateRoute(request);
    }

    public Train getTrainById(Long trainId) {
        return trainOrderFeignClient.getById(trainId);
    }

    public BigDecimal getSeatPrice(Long trainId, String trainDate, String startStation, String endStation, Integer seatType) {
        Supplier<BigDecimal> supplier = () -> trainOrderFeignClient.getSeatPrice(
                trainId, trainDate, startStation, endStation, seatType);
        return decorateTrainCall(supplier, "getSeatPrice").get();
    }

    public void deductStock(Long trainId, String trainDate, String startStation, String endStation, Integer seatType, int count) {
        Runnable task = () -> trainOrderFeignClient.deductStock(
                new TrainStockCommand(trainId, trainDate, startStation, endStation, seatType, count));
        decorateTrainCall(task, "deductStock").run();
    }

    public void deductStocksBatch(List<TrainStockCommand> segments) {
        Runnable task = () -> trainOrderFeignClient.deductStocksBatch(segments);
        decorateTrainCall(task, "deductStocksBatch").run();
    }

    public void rollbackStocksBatch(List<TrainStockCommand> segments) {
        trainOrderFeignClient.rollbackStocksBatch(segments);
    }

    public void confirmStocksBatch(List<TrainStockCommand> segments) {
        trainOrderFeignClient.confirmStocksBatch(segments);
    }

    public void reservationRollbackBatch(List<TrainStockCommand> segments) {
        trainOrderFeignClient.reservationRollbackBatch(segments);
    }

    public void rollbackStock(Long trainId, String trainDate, String startStation, String endStation, Integer seatType, int count) {
        trainOrderFeignClient.rollbackStock(new TrainStockCommand(trainId, trainDate, startStation, endStation, seatType, count));
    }

    public void rollbackReservation(Long trainId, String trainDate, String startStation, String endStation, Integer seatType, int count) {
        trainOrderFeignClient.reservationRollback(new TrainStockCommand(trainId, trainDate, startStation, endStation, seatType, count));
    }

    /**
     * 用 CircuitBreaker + TimeLimiter 装饰对 train-service 的同步调用。
     * 熔断打开 / 超时 / 调用异常统一转换为 TrainServiceUnavailableException。
     */
    private <T> Supplier<T> decorateTrainCall(Supplier<T> supplier, String methodName) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(
                TrainServiceCircuitBreakerConfig.TRAIN_SERVICE_INSTANCE);
        TimeLimiter timeLimiter = timeLimiterRegistry.timeLimiter(
                TrainServiceCircuitBreakerConfig.TRAIN_SERVICE_INSTANCE);

        // 先包 CircuitBreaker，再包 TimeLimiter（在隔离线程池异步执行，超时即取消）
        Supplier<T> cbDecorated = CircuitBreaker.decorateSupplier(circuitBreaker, supplier);
        Supplier<T> decorated = () -> {
            try {
                return timeLimiter.executeFutureSupplier(
                        () -> CompletableFuture.supplyAsync(cbDecorated, trainServiceCbExecutor));
            } catch (Exception e) {
                throw new TrainServiceUnavailableException(TRAIN_SERVICE_UNAVAILABLE, e);
            }
        };

        return () -> {
            try {
                return decorated.get();
            } catch (TrainServiceUnavailableException e) {
                logger.warn("调用 train-service 失败（熔断/超时/异常）: method={}, error={}",
                        methodName, e.getMessage());
                throw e;
            } catch (Exception e) {
                logger.warn("调用 train-service 失败（熔断/超时/异常）: method={}, error={}",
                        methodName, e.getMessage());
                throw new TrainServiceUnavailableException(TRAIN_SERVICE_UNAVAILABLE, e);
            }
        };
    }

    /**
     * 用 CircuitBreaker + TimeLimiter 装饰对 train-service 的无返回值同步调用。
     */
    private Runnable decorateTrainCall(Runnable runnable, String methodName) {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(
                TrainServiceCircuitBreakerConfig.TRAIN_SERVICE_INSTANCE);
        TimeLimiter timeLimiter = timeLimiterRegistry.timeLimiter(
                TrainServiceCircuitBreakerConfig.TRAIN_SERVICE_INSTANCE);

        Runnable cbDecorated = CircuitBreaker.decorateRunnable(circuitBreaker, runnable);
        Supplier<Void> futureSupplier = () -> {
            cbDecorated.run();
            return null;
        };
        Runnable decorated = () -> {
            try {
                timeLimiter.executeFutureSupplier(
                        () -> CompletableFuture.supplyAsync(futureSupplier, trainServiceCbExecutor));
            } catch (Exception e) {
                throw new TrainServiceUnavailableException(TRAIN_SERVICE_UNAVAILABLE, e);
            }
        };

        return () -> {
            try {
                decorated.run();
            } catch (TrainServiceUnavailableException e) {
                logger.warn("调用 train-service 失败（熔断/超时/异常）: method={}, error={}",
                        methodName, e.getMessage());
                throw e;
            } catch (Exception e) {
                logger.warn("调用 train-service 失败（熔断/超时/异常）: method={}, error={}",
                        methodName, e.getMessage());
                throw new TrainServiceUnavailableException(TRAIN_SERVICE_UNAVAILABLE, e);
            }
        };
    }
}
