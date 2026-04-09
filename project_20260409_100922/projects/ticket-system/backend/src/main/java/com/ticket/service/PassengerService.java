package com.ticket.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ticket.entity.Passenger;

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
    java.util.List<Passenger> getByUserId(Long userId);
}
