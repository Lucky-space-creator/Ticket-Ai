package com.ticket.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ticket.entity.Passenger;
import com.ticket.enums.CacheKey;
import com.ticket.mapper.PassengerMapper;
import com.ticket.service.PassengerService;
import com.ticket.util.RedisUtil;
import com.ticket.util.CryptoUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 常用联系人服务实现
 */
@Service
public class PassengerServiceImpl extends ServiceImpl<PassengerMapper, Passenger> implements PassengerService {

    @Resource
    private RedisUtil redisUtil;

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
    public java.util.List<Passenger> getByUserId(Long userId) {
        // 先查缓存
        String cacheKey = String.format(CacheKey.USER_PASSENGERS, userId);
        java.util.List<Passenger> cached = redisUtil.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        // 查数据库
        LambdaQueryWrapper<Passenger> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Passenger::getUserId, userId);
        java.util.List<Passenger> list = list(wrapper);

        // 存缓存（1小时）
        redisUtil.set(cacheKey, list, 60, TimeUnit.MINUTES);

        return list;
    }
}
