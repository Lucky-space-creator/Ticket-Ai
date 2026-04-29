package com.ticket.util;

import cn.hutool.core.util.IdUtil;

/**
 * TraceID上下文工具类
 * 用于在全链路追踪中传递唯一的请求标识
 * 复用UserContext的ThreadLocal设计模式
 */
public class TraceContext {

    /** MDC中的TraceID键名 */
    public static final String TRACE_ID_KEY = "traceId";

    /** 请求头中的TraceID键名（默认值，可被TraceConfig覆盖） */
    private static String traceHeader = "X-Request-ID";

    /**
     * 设置请求头中的TraceID键名（由TraceConfig初始化时调用）
     * @param headerName 请求头字段名
     */
    public static void setTraceHeader(String headerName) {
        if (headerName != null && !headerName.isEmpty()) {
            traceHeader = headerName;
        }
    }

    /**
     * 获取请求头中的TraceID键名
     * @return 当前配置的header名称
     */
    public static String getTraceHeader() {
        return traceHeader;
    }

    private static final ThreadLocal<String> CONTEXT_HOLDER = new ThreadLocal<>();

    /**
     * 设置当前线程的TraceID
     * @param traceId 追踪ID
     */
    public static void setTraceId(String traceId) {
        if (traceId != null && !traceId.isEmpty()) {
            CONTEXT_HOLDER.set(traceId);
        }
    }

    /**
     * 获取当前线程的TraceID
     * @return 当前TraceID，如果未设置则返回null
     */
    public static String getTraceId() {
        return CONTEXT_HOLDER.get();
    }

    /**
     * 获取当前TraceID，如果未设置则生成新的
     * @return 当前或新生成的TraceID
     */
    public static String getOrCreateTraceId() {
        String traceId = CONTEXT_HOLDER.get();
        if (traceId == null || traceId.isEmpty()) {
            traceId = IdUtil.fastUUID();
            CONTEXT_HOLDER.set(traceId);
        }
        return traceId;
    }

    /**
     * 清除当前线程的TraceID
     */
    public static void clear() {
        CONTEXT_HOLDER.remove();
    }

    /**
     * 判断当前线程是否已设置TraceID
     */
    public static boolean hasTraceId() {
        String traceId = CONTEXT_HOLDER.get();
        return traceId != null && !traceId.isEmpty();
    }
}