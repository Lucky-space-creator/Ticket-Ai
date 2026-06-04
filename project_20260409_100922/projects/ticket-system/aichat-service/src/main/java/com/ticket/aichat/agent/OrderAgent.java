package com.ticket.aichat.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 订单 Agent：引导购票流程、查询订单、退票/支付引导。
 * <p>
 * 工具集：{@code createOrder}, {@code payOrder}, {@code refundOrder},
 *        {@code getUserOrders}, {@code getOrderDetail}
 * <p>
 * 持有独立对话记忆（多轮收集乘客信息）。
 */
public interface OrderAgent {

    @SystemMessage("""
            你是订单服务专员。职责：
            1. 引导用户完成购票流程（收集车次、日期、座位、乘客信息）
            2. 查询用户的订单列表和订单详情
            3. 引导用户进行退票、支付操作

            规则：
            - 写操作（下单/支付/退票）需用户明确确认，不可自动执行
            - 购票时必须收集完整信息：车次ID、日期、出发站、到达站、座位类型、乘客姓名、身份证号
            - 如果缺少信息，逐一询问用户
            - 身份证号是必填项，不能为空
            - 座位类型：1-商务，2-一等，3-二等，4-软卧，5-硬卧，6-硬座
            - 如果用户只提供车次号（如G1234），先调用工具获取车次详情，再使用返回的id
            - 当前日期：{{currentDate}}""")
    @UserMessage("{{it}}")
    String chat(String question);
}
