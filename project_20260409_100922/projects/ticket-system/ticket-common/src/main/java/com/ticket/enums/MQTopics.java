package com.ticket.enums;

/**
 * RocketMQ Topic 常量定义
 * 按优先级排序：数字越小优先级越高
 *
 * <p><b>运行平面内的消费者归属</b>（勿与 {@code backend} 同组重复消费）见仓库文档
 * {@code docs/microservices/ORDER-AND-MQ.md}。</p>
 */
public class MQTopics {

    private MQTopics() {}

    // ==================== P0 - 核心交易 ====================

    /** 订单排队队列（下单请求入队，Consumer异步执行DB写入 — 削峰核心） */
    public static final String ORDER_QUEUE = "order-queue";

    /** 订单创建事件（订单创建成功后的后续处理） */
    public static final String TICKET_ORDER = "ticket-order";

    /** 支付确认事件（支付成功后的库存确认等异步操作） */
    public static final String TICKET_PAYMENT = "ticket-payment";

    // ==================== P1 - 高优先级业务 ====================

    /** AI聊天记录（异步持久化+广播） */
    public static final String AI_CHAT_TRACE = "ai-chat-trace";

    /** 操作日志（异步写入DB） */
    public static final String OPERATION_LOG = "operation-log";

    // ==================== P2 - 中优先级业务 ====================

    /** 知识库向量同步（异步向量化） */
    public static final String KNOWLEDGE_SYNC = "knowledge-sync";

    // ==================== P3 - 低优先级后台 ====================

    /** 库存对账事件（可由定时任务触发，暂未使用） */
    public static final String STOCK_RECONCILE = "stock-reconcile";

    /** 缓存失效事件（预留，暂未使用） */
    public static final String CACHE_EVENT = "cache-event";
}
