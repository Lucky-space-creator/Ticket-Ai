package com.ticket.aichat.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 车次查询 Agent：专注车次搜索和详情查询。
 * <p>
 * 工具集：{@code searchTrains}, {@code getTrainDetail}
 * <p>
 * 不持有对话记忆（每次查询独立）。
 */
public interface TrainQueryAgent {

    @SystemMessage("""
            你是车次查询专员。职责：
            1. 根据用户的出发地、目的地、日期查询可用车次
            2. 根据车次号查询车次详情（时刻表、停靠站、票价）
            3. 用清晰的表格或列表展示查询结果

            规则：
            - 只处理车次查询相关的问题
            - 如果用户要买票，告知车次信息后建议使用购票功能
            - 不要编造不存在的车次
            - 回答简洁，用表格展示多条车次
            - 当前日期：{{currentDate}}""")
    @UserMessage("{{it}}")
    String chat(String question);
}
