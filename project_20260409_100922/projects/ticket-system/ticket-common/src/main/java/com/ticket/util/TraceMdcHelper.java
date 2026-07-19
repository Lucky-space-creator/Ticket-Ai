package com.ticket.util;

import cn.hutool.jwt.JWTUtil;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * Trace MDC传播工具类
 * 解决WebSocket(Netty线程)和Reactor(Flux线程)中MDC上下文丢失问题
 * 在子线程中手动恢复父线程的MDC信息
 */
public class TraceMdcHelper {

    private TraceMdcHelper() {}
    /**
     * 在子线程中执行Runnable，自动恢复MDC上下文
     * @param traceId 父线程的TraceID
     * @param task 要执行的任务
     */
    public static void runWithTraceId(String traceId, Runnable task) {
        try {
            Map<String, String> contextMap = MDC.getCopyOfContextMap();
            if (contextMap == null) {
                contextMap = new HashMap<>(2);
            }
            contextMap.put(TraceContext.TRACE_ID_KEY, traceId);
            MDC.setContextMap(contextMap);
            task.run();
        } finally {
            MDC.clear();
        }
    }

    /**
     * 在子线程中执行Supplier，自动恢复MDC上下文
     * @param traceId 父线程的TraceID
     * @param supplier 带返回值的任务
     * @param <T> 返回值类型
     * @return 任务执行结果
     */
    public static <T> T supplyWithTraceId(String traceId, Supplier<T> supplier) {
        try {
            Map<String, String> contextMap = MDC.getCopyOfContextMap();
            if (contextMap == null) {
                contextMap = new HashMap<>(2);
            }
            contextMap.put(TraceContext.TRACE_ID_KEY, traceId);
            MDC.setContextMap(contextMap);
            return supplier.get();
        } finally {
            MDC.clear();
        }
    }

    /**
     * 在子线程中执行Callable，自动恢复MDC上下文
     * @param traceId 父线程的TraceID
     * @param task 带返回值的任务
     * @param <T> 返回值类型
     * @return 任务执行结果
     * @throws Exception 任务抛出的异常
     */
    public static <T> T callWithTraceId(String traceId, Callable<T> task) throws Exception {
        try {
            Map<String, String> contextMap = MDC.getCopyOfContextMap();
            if (contextMap == null) {
                contextMap = new java.util.HashMap<>(2);
            }
            contextMap.put(TraceContext.TRACE_ID_KEY, traceId);
            MDC.setContextMap(contextMap);
            return task.call();
        } finally {
            MDC.clear();
        }
    }

    /**
     * 将当前MDC快照设置到MDC中（用于跨线程传递）
     * @return 当前MDC的副本快照
     */
    public static Map<String, String> snapshotMdc() {
        Map<String, String> map = MDC.getCopyOfContextMap();
        if (map == null) {
            map = new java.util.HashMap<>(2);
        }
        return map;
    }

    /**
     * 恢复MDC快照到当前线程
     * @param snapshot 之前保存的MDC快照
     */
    public static void restoreMdc(Map<String, String> snapshot) {
        MDC.setContextMap(snapshot);
    }
}