package com.ticket.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.entity.*;
import com.ticket.mapper.*;
import com.ticket.service.StatsService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 统计服务实现
 */
@Service
public class StatsServiceImpl implements StatsService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private OrderMapper orderMapper;

    @Resource
    private TrainMapper trainMapper;

    @Resource
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Resource
    private ChatRecordMapper chatRecordMapper;

    @Override
    public Map<String, Object> getOverviewStats() {
        Map<String, Object> stats = new HashMap<>();

        // 用户总数
        long userCount = userMapper.selectCount(null);
        stats.put("userCount", userCount);

        // 今日新增用户
        LambdaQueryWrapper<User> todayUserWrapper = new LambdaQueryWrapper<>();
        todayUserWrapper.ge(User::getCreatedAt, LocalDateTime.of(LocalDate.now(), LocalTime.MIN));
        long todayUserCount = userMapper.selectCount(todayUserWrapper);
        stats.put("todayUserCount", todayUserCount);

        // 订单总数
        long orderCount = orderMapper.selectCount(null);
        stats.put("orderCount", orderCount);

        // 今日订单
        LambdaQueryWrapper<Order> todayOrderWrapper = new LambdaQueryWrapper<>();
        todayOrderWrapper.ge(Order::getCreatedAt, LocalDateTime.of(LocalDate.now(), LocalTime.MIN));
        long todayOrderCount = orderMapper.selectCount(todayOrderWrapper);
        stats.put("todayOrderCount", todayOrderCount);

        // 今日销售额
        LambdaQueryWrapper<Order> todaySalesWrapper = new LambdaQueryWrapper<>();
        todaySalesWrapper.ge(Order::getCreatedAt, LocalDateTime.of(LocalDate.now(), LocalTime.MIN))
                        .eq(Order::getStatus, 1); // 已支付
        // 这里简化处理，实际应计算总金额
        stats.put("todaySales", 0);

        // 车次总数
        long trainCount = trainMapper.selectCount(null);
        stats.put("trainCount", trainCount);

        // 运行中车次
        LambdaQueryWrapper<Train> runningTrainWrapper = new LambdaQueryWrapper<>();
        runningTrainWrapper.eq(Train::getStatus, 1);
        long runningTrainCount = trainMapper.selectCount(runningTrainWrapper);
        stats.put("runningTrainCount", runningTrainCount);

        // 知识库条目数
        long knowledgeCount = knowledgeBaseMapper.selectCount(null);
        stats.put("knowledgeCount", knowledgeCount);

        // 今日对话数
        LambdaQueryWrapper<ChatRecord> todayChatWrapper = new LambdaQueryWrapper<>();
        todayChatWrapper.ge(ChatRecord::getCreatedAt, LocalDateTime.of(LocalDate.now(), LocalTime.MIN));
        long todayChatCount = chatRecordMapper.selectCount(todayChatWrapper);
        stats.put("todayChatCount", todayChatCount);

        return stats;
    }

    @Override
    public Map<String, Object> getTrendStats(String type) {
        Map<String, Object> trend = new HashMap<>();
        // 简化实现，返回空数据
        trend.put("labels", new String[]{});
        trend.put("datasets", new Map[]{});
        return trend;
    }
}