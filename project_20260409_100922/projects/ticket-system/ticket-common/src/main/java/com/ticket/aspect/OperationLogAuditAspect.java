package com.ticket.aspect;

import com.ticket.annotation.OperationLog;
import com.ticket.service.OperationLogEventPublisher;
import jakarta.annotation.Resource;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 操作日志切面：拦截所有带 {@link OperationLog} 注解的 Controller（或其它 Bean）方法，
 * 在业务逻辑执行完毕后触发异步日志投递，不阻塞主流程返回。
 * <p><b>执行顺序 {@link Order}(50)</b>：数值越小越先执行。放在认证、权限等拦截器之后即可，
 * 以便 {@link com.ticket.util.UserContext} / JWT 已解析完成（若需要）；又早于部分后置清理亦可接受，
 * 因发布器内通过 {@link org.springframework.web.context.request.RequestContextHolder} 取当前请求。</p>
 * <p><b>成功/失败判定</b>：若返回值是 {@link com.ticket.util.ResponseUtil.Result}，则以 {@code code == 200}
 * 为成功；若方法抛出异常，则记录为失败并携带异常信息，再原样抛出，不改变业务语义。</p>
 */
@Aspect
@Component
@Order(50)
public class OperationLogAuditAspect {

    @Resource
    private OperationLogEventPublisher operationLogEventPublisher;

    /**
     * 环绕通知：先执行目标方法，再发日志；异常路径同样发日志（失败态）后重新抛出。
     *
     * @param pjp            连接点，可取得方法参数（用于脱敏序列化入 request_params）
     * @param operationLog   方法上的注解元数据（module / operation 枚举及其 description）
     * @return 原方法返回值
     */
    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint pjp, OperationLog operationLog) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            Object ret = pjp.proceed();
            operationLogEventPublisher.publishAfterOperation(operationLog, pjp, ret, null,
                    System.currentTimeMillis() - start);
            return ret;
        } catch (Throwable ex) {
            operationLogEventPublisher.publishAfterOperation(operationLog, pjp, null, ex,
                    System.currentTimeMillis() - start);
            throw ex;
        }
    }
}
