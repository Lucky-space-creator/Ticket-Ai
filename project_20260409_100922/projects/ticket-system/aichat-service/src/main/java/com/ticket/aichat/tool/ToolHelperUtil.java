package com.ticket.aichat.tool;

import com.ticket.enums.ResponseCode;
import com.ticket.util.ResponseUtil;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.function.Supplier;

/**
 * 工具类共享的辅助方法。
 * <p>
 * 从原 AIBusinessTool 提取，供 4 个领域工具复用。
 */
final class ToolHelperUtil {

    private static final Logger log = LoggerFactory.getLogger(ToolHelperUtil.class);

    private ToolHelperUtil() {
    }

    /**
     * 生成写操作拒绝异常（AI 不得自动执行写操作）
     */
    static RuntimeException denyAutonomousWrite(String toolName) {
        return new IllegalStateException(
                "策略禁止：AI 工具「" + toolName + "」不得自动执行写操作，请通过官方购票/个人中心完成。");
    }

    /**
     * 将 Feign 调用结果封装为工具返回值
     */
    static Object unwrap(ResponseUtil.Result<?> result, String actionLabel) {
        if (result == null) {
            return Map.of("error", true, "message", actionLabel + "：无响应");
        }
        if (!ResponseCode.SUCCESS.getCode().equals(result.getCode())) {
            String msg = result.getMessage() != null ? result.getMessage() : "请求失败";
            return Map.of("error", true, "message", msg);
        }
        return result.getData() != null ? result.getData() : Map.of();
    }

    /**
     * Feign 调用包装器，统一异常处理
     */
    static Object feignCall(String op, Supplier<Object> supplier) {
        try {
            return supplier.get();
        } catch (FeignException e) {
            log.warn("Feign 调用失败 [{}]: status={} {}", op, e.status(), e.getMessage());
            return Map.of("error", true, "message",
                    "下游服务暂时不可用（" + op + "），请稍后重试");
        } catch (Exception e) {
            log.warn("调用异常 [{}]: {}", op, e.getMessage());
            return Map.of("error", true, "message", e.getMessage());
        }
    }
}
