package com.ticket.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ticket.entity.Passenger;
import com.ticket.entity.User;

import java.util.List;

/**
 * 常用联系人服务接口
 */
public interface PassengerService extends IService<Passenger> {

    /**
     * 添加联系人
     */
    Passenger addPassenger(Long userId, String name, String idCard, String phone);

    /**
     * 删除联系人
     */
    boolean deletePassenger(Long passengerId, Long userId);

    /**
     * 获取用户的所有联系人
     */
    List<Passenger> getByUserId(Long userId);
}