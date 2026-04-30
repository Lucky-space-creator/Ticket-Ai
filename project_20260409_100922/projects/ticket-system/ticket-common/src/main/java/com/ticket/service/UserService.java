package com.ticket.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ticket.entity.User;

/**
 * 用户服务接口
 */
public interface UserService extends IService<User> {

    /**
     * 用户注册
     */
    User register(String phone, String password, String realName, String idCard);

    /**
     * 用户登录
     */
    User login(String phone, String password);

    /**
     * 根据手机号查询用户
     */
    User getByPhone(String phone);

    /**
     * 更新用户信息
     */
    boolean updateProfile(Long userId, String realName, String idCard);

    /**
     * 更新用户角色
     */
    boolean updateUserRole(Long userId, Long roleId);
}