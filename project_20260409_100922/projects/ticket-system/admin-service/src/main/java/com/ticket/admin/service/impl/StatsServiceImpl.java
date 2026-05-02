package com.ticket.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.admin.mapper.*;
import com.ticket.admin.service.StatsService;
import com.ticket.entity.ChatRecord;
import com.ticket.entity.Order;
import com.ticket.entity.Train;
import com.ticket.entity.User;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 统计服务实现
 */
@Slf4j
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
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        // 用户总数
        long userCount = userMapper.selectCount(null);
        stats.put("userCount", userCount);
        log.debug("userCount: {}", userCount);

        // 今日新增用户
        LambdaQueryWrapper<User> todayUserWrapper = new LambdaQueryWrapper<>();
        todayUserWrapper.ge(User::getCreatedAt, todayStart);
        long todayUserCount = userMapper.selectCount(todayUserWrapper);
        stats.put("todayUserCount", todayUserCount);
        log.debug("todayUserCount: {}", todayUserCount);

        // 订单总数
        long orderCount = orderMapper.selectCount(null);
        stats.put("orderCount", orderCount);
        log.debug("orderCount: {}", orderCount);

        // 今日订单
        LambdaQueryWrapper<Order> todayOrderWrapper = new LambdaQueryWrapper<>();
        todayOrderWrapper.ge(Order::getCreatedAt, todayStart);
        long todayOrderCount = orderMapper.selectCount(todayOrderWrapper);
        stats.put("todayOrderCount", todayOrderCount);
        log.debug("todayOrderCount: {}", todayOrderCount);

        // 今日销售额
        LambdaQueryWrapper<Order> todaySalesWrapper = new LambdaQueryWrapper<>();
        todaySalesWrapper.ge(Order::getCreatedAt, todayStart)
                        .eq(Order::getStatus, 1); // 已支付
        // 计算已支付订单的总金额
        List<Order> paidOrders = orderMapper.selectList(todaySalesWrapper);
        BigDecimal todaySales = paidOrders.stream()
                .map(Order::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        stats.put("todaySales", todaySales);
        log.debug("todaySales: {}", todaySales);

        // 车次总数
        long trainCount = trainMapper.selectCount(null);
        stats.put("trainCount", trainCount);
        log.debug("trainCount: {}", trainCount);

        // 运行中车次
        LambdaQueryWrapper<Train> runningTrainWrapper = new LambdaQueryWrapper<>();
        runningTrainWrapper.eq(Train::getStatus, 1);
        long runningTrainCount = trainMapper.selectCount(runningTrainWrapper);
        stats.put("runningTrainCount", runningTrainCount);
        log.debug("runningTrainCount: {}", runningTrainCount);

        // 知识库条目数
        long knowledgeCount = knowledgeBaseMapper.selectCount(null);
        stats.put("knowledgeCount", knowledgeCount);
        log.debug("knowledgeCount: {}", knowledgeCount);

        // 今日对话数
        LambdaQueryWrapper<ChatRecord> todayChatWrapper = new LambdaQueryWrapper<>();
        todayChatWrapper.ge(ChatRecord::getCreatedAt, todayStart);
        long todayChatCount = chatRecordMapper.selectCount(todayChatWrapper);
        stats.put("todayChatCount", todayChatCount);
        log.debug("todayChatCount: {}", todayChatCount);

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