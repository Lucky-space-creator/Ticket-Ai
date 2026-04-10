package com.ticket.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ticket.entity.Passenger;
import com.ticket.entity.User;
import com.ticket.enums.CacheKey;
import com.ticket.mapper.PassengerMapper;
import com.ticket.service.PassengerService;
import com.ticket.service.UserService;
import com.ticket.util.RedisUtil;
import com.ticket.util.CryptoUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 常用联系人服务实现
 */
@Service
public class PassengerServiceImpl extends ServiceImpl<PassengerMapper, Passenger> implements PassengerService {

    @Resource
    private RedisUtil redisUtil;
    @Resource
    private UserService userService;

    @Override
    public Passenger addPassenger(Long userId, String name, String idCard, String phone) {
        Passenger passenger = new Passenger();
        passenger.setUserId(userId);
        passenger.setName(name);
        passenger.setIdCard(CryptoUtil.encrypt(idCard));
        passenger.setPhone(phone);

        save(passenger);

        // 清除缓存
        redisUtil.delete(String.format(CacheKey.USER_PASSENGERS, userId));

        return passenger;
    }

    @Override
    public boolean deletePassenger(Long passengerId, Long userId) {
        LambdaQueryWrapper<Passenger> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Passenger::getId, passengerId)
                .eq(Passenger::getUserId, userId);

        boolean result = remove(wrapper);

        // 清除缓存
        redisUtil.delete(String.format(CacheKey.USER_PASSENGERS, userId));

        return result;
    }

    @Override
    public List<Passenger> getByUserId(Long userId) {

        User user = userService.getById(userId);

        // 先查缓存
        String cacheKey = String.format(CacheKey.USER_PASSENGERS, userId);
        List<Passenger> cached = redisUtil.get(cacheKey);

        if (cached != null) {
            // 缓存命中，去除重复的身份证返回
            return cached.stream()
                    .filter(p -> !p.getIdCard().equals(user.getIdCard()))
                    .collect(Collectors.toList());
        }

        // 查数据库
        LambdaQueryWrapper<Passenger> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Passenger::getUserId, userId);
        List<Passenger> list = list(wrapper);

        // 存缓存（1小时）
        redisUtil.set(cacheKey, list, 60, TimeUnit.MINUTES);

        // 去除重复的身份证
        return list.stream()
                .filter(p -> !p.getIdCard().equals(user.getIdCard()))
                .collect(Collectors.toList());
    }
}
