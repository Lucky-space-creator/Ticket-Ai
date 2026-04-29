package com.ticket.order.service;

import com.ticket.dto.mq.OrderQueueRequest;

/**
 * 订单排队服务接口
 * 负责将下单请求快速入队到MQ，实现削峰填谷
 */
public interface OrderQueueService {

    /**
     * 将下单请求入队（核心方法：Redis预扣+MQ入队）
     * @param request 排队请求
     * @return requestId 用于前端轮询查询结果
     */
    String enqueue(OrderQueueRequest request);

    /**
     * 查询排队结果
     * @param requestId 请求ID
     * @return 排队结果状态码：PROCESSING=0, SUCCESS=1, FAILED=2, EXPIRED=3
     */
    int queryStatus(String requestId);

    /**
     * 获取排队成功后的订单号
     * @param requestId 请求ID
     * @return 订单号，如果未完成则返回null
     */
    String getOrderNo(String requestId);

    /**
     * 获取失败原因
     * @param requestId 请求ID
     * @return 错误消息，如果没有失败则返回null
     */
    String getErrorMessage(String requestId);

    /**
     * 更新Redis状态
     * @param requestId 请求ID
     * @param targetCode 修改成目标状态码
     * @param orderNo 订单号
     * @param errorMsg 错误消息
     */
    void updateResult(String requestId, int targetCode, String orderNo, String errorMsg);
}