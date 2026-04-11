package com.ticket.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 智能客服 AI 服务接口
 * LangChain4j 会自动生成实现类
 */
public interface KnowledgeAssistant {

    @SystemMessage("""
        你是一个专业的火车票务客服助手，专门回答关于火车票购买、退改签、乘车规定等问题。
        
        重要规则：
        1. 请严格根据提供的【参考资料】进行回答
        2. 如果参考资料中没有相关信息，请明确回答："我暂时无法回答这个问题，请联系人工客服。"
        3. 不要编造或猜测答案
        4. 回答要简洁、准确、友好
        5. 涉及价格、时间等具体信息时要准确引用
        
        参考资料：
        {it}
        """)
    String chat(@UserMessage String question);
}