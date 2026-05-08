package com.ticket.annotation;

import com.ticket.aspect.OperationLogAuditAspect;
import com.ticket.operationlog.Module;
import com.ticket.operationlog.Operation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明「需要记录到 {@code operation_log} 表」的接口方法。
 * <p>配合 {@link OperationLogAuditAspect}：在方法执行前后织入，由
 * {@link com.ticket.service.OperationLogEventPublisher} 组装 {@link com.ticket.dto.mq.OperationLogEvent}
 * 并调用 {@link com.ticket.service.RocketMQProducerService#sendOperationLogEvent} 异步投递；
 * 最终由 admin-service 的 {@code OperationLogMqConsumer} 消费并落库。</p>
 * <p>仅标注在需要审计的<strong>关键写操作或登录注册</strong>上即可，避免全量接口打爆 MQ 与库。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLog {

    /**
     * 业务模块，持久化使用 {@link Module#getDisplayName()}。
     */
    Module module();

    /**
     * 操作类型，持久化使用 {@link Operation#getDisplayName()}。
     */
    Operation operation();

    /**
     * 可选的补充说明；为空时发布器会用 {@link Operation#getDisplayName()} 作为描述兜底。
     */
    String description() default "";
}
