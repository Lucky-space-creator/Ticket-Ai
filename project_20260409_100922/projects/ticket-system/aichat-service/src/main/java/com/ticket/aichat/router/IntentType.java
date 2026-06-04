package com.ticket.aichat.router;

/**
 * 用户意图分类枚举。
 * <p>
 * 由 {@link IntentRouter} 分类后，AIChatServiceImpl 据此路由到对应的 Specialist Agent。
 */
public enum IntentType {

    /** 车次查询（搜索车次、查时刻表、查停靠站） */
    TRAIN_QUERY,

    /** 订单操作（购票引导、订单查询、退票引导、支付引导） */
    ORDER,

    /** 知识问答（铁路政策、乘车规定、退改签规则，走 RAG） */
    KNOWLEDGE,

    /** 个人信息管理（查看/更新资料、管理常用联系人） */
    PROFILE,

    /** 转人工客服 */
    HUMAN_TRANSFER,

    /** 打招呼 / 自我介绍 */
    GREETING,

    /** 未知意图（需 LLM 兜底分类） */
    UNKNOWN
}
