package com.ticket.admin.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ticket.admin.service.AdminUserService;
import com.ticket.admin.mapper.UserMapper;
import com.ticket.entity.User;
import com.ticket.enums.CacheKey;
import com.ticket.service.PermissionService;
import com.ticket.util.RedisUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * 用户目录写操作与缓存失效（应用层实现，依赖基础设施 Mapper / Redis）。
 */
@Service
public class AdminUserServiceImpl extends ServiceImpl<UserMapper, User> implements AdminUserService {

    @Resource
    private RedisUtil redisUtil;

    @Resource
    private PermissionService permissionService;

    @Override
    public boolean updateUserRole(Long userId, Long roleId) {
        User user = new User();
        user.setId(userId);
        user.setRoleId(roleId);
        boolean result = updateById(user);
        redisUtil.delete(String.format(CacheKey.USER_INFO, userId));
        permissionService.clearUserPermissionCache(userId);
        return result;
    }
}
