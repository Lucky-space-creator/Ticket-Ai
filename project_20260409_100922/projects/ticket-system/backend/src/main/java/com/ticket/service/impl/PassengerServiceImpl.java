package com.ticket.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ticket.entity.Passenger;
import com.ticket.entity.User;
import com.ticket.enums.CacheKey;
import com.ticket.mapper.PassengerMapper;
import com.ticket.service.PassengerService;
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
    public List<Passenger> getByUserId(User user) {
        Long userId = user.getId();
        //解码user的idCard
        String decryptedIdCard = CryptoUtil.decrypt(user.getIdCard());
        // 先查缓存
        String cacheKey = String.format(CacheKey.USER_PASSENGERS, userId);
        List<Passenger> cached = redisUtil.get(cacheKey);


        if (cached != null) {
            //如果cached中的idCard和user的idCard一致，则返回缓存中的数据

            //1.解码cached中的idCard
            cached = cached.stream().peek(passenger -> passenger.setIdCard(CryptoUtil.decrypt(passenger.getIdCard()))).collect(Collectors.toList());

            //2.过滤cached中的idCard和user的idCard一致的
            cached = cached.stream().filter(passenger -> passenger.getIdCard().equals(decryptedIdCard))
                    .collect(Collectors.toList());

            return cached;
        }

        // 查数据库
        LambdaQueryWrapper<Passenger> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Passenger::getUserId, userId);
        List<Passenger> list = list(wrapper);

        // 存缓存（1小时）
        redisUtil.set(cacheKey, list, 60, TimeUnit.MINUTES);

        return list;
    }
}
