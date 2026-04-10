package com.ticket.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ticket.enums.CacheKey;
import com.ticket.entity.User;
import com.ticket.mapper.UserMapper;
import com.ticket.service.UserService;
import com.ticket.util.RedisUtil;
import com.ticket.util.CryptoUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * 用户服务实现
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Resource
    private RedisUtil redisUtil;

    @Override
    public User register(String phone, String password, String realName, String idCard) {
        // 检查手机号是否已存在
        User existUser = getByPhone(phone);
        if (existUser != null) {
            throw new RuntimeException("手机号已存在");
        }

        // 调试日志：打印密码相关信息（生产环境应删除）
        System.out.println("=== 注册调试信息 ===");
        System.out.println("用户手机号: " + phone);
        System.out.println("前端传来的密码（明文）: " + password);

        // 加密密码
        String encryptedPassword = CryptoUtil.encryptPassword(password);
        System.out.println("加密后的密码: " + encryptedPassword);

        // 创建用户
        User user = new User();
        user.setPhone(phone);
        user.setPassword(encryptedPassword);
        user.setRealName(realName);
        user.setIdCard(idCard != null ? CryptoUtil.encrypt(idCard) : null);
        user.setStatus(1); // 正常状态

        save(user);
        return user;
    }

    @Override
    public User login(String phone, String password) {
        // 查询用户
        User user = getByPhone(phone);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        // 调试日志：打印密码相关信息（生产环境应删除）
        System.out.println("=== 登录调试信息 ===");
        System.out.println("用户手机号: " + phone);
        System.out.println("前端传来的密码（明文）: " + password);
        System.out.println("数据库存储的密码: " + user.getPassword());
        System.out.println("是否为 BCrypt 格式: " + CryptoUtil.isBcryptPassword(user.getPassword()));

        // 验证密码
        boolean passwordMatch = CryptoUtil.verifyPassword(password, user.getPassword());
        System.out.println("密码验证结果: " + passwordMatch);

        if (!passwordMatch) {
            throw new RuntimeException("密码错误");
        }

        // 检查状态
        if (user.getStatus() == 0) {
            throw new RuntimeException("账号已被禁用");
        }

        // 如果密码不是 BCrypt 格式（旧版明文密码），自动升级为 BCrypt 格式
        if (!CryptoUtil.isBcryptPassword(user.getPassword())) {
            System.out.println("检测到旧版明文密码，自动升级为 BCrypt 格式...");
            String newEncryptedPassword = CryptoUtil.encryptPassword(password);
            user.setPassword(newEncryptedPassword);
            updateById(user);
            System.out.println("密码升级完成: " + newEncryptedPassword);
        }

        return user;
    }

    @Override
    public User getByPhone(String phone) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getPhone, phone);
        return getOne(wrapper);
    }

    @Override
    public boolean updateProfile(Long userId, String realName, String idCard) {
        User user = new User();
        user.setId(userId);
        user.setRealName(realName);
        user.setIdCard(CryptoUtil.encrypt(idCard));

        boolean result = updateById(user);

        // 清除缓存
        redisUtil.delete(String.format(CacheKey.USER_INFO, userId));

        return result;
    }
}
