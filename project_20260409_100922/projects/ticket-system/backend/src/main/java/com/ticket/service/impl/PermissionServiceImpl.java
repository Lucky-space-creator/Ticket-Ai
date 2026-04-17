package com.ticket.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ticket.entity.Permission;
import com.ticket.entity.RolePermission;
import com.ticket.entity.User;
import com.ticket.enums.CacheKey;
import com.ticket.mapper.PermissionMapper;
import com.ticket.mapper.RolePermissionMapper;
import com.ticket.mapper.UserMapper;
import com.ticket.service.PermissionService;
import com.ticket.util.RedisUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 权限服务实现
 */
@Service
public class PermissionServiceImpl extends ServiceImpl<PermissionMapper, Permission> implements PermissionService {

    @Resource
    private RolePermissionMapper rolePermissionMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private RedisUtil redisUtil;

    @Override
    public List<Permission> getPermissionTree() {
        List<Permission> allPermissions = this.list();
        return buildTree(allPermissions, 0L);
    }

    @Override
    public List<Permission> getMenuPermissions() {
        LambdaQueryWrapper<Permission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Permission::getPermissionType, 1) // 菜单权限
               .eq(Permission::getStatus, 1)
               .orderByAsc(Permission::getSort);
        return this.list(wrapper);
    }

    @Override
    public List<Permission> getApiPermissions() {
        LambdaQueryWrapper<Permission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Permission::getPermissionType, 3) // API权限
               .eq(Permission::getStatus, 1);
        return this.list(wrapper);
    }

    @Override
    public List<Permission> getPermissionsByUserId(Long userId) {
        // 从缓存获取
        String cacheKey = String.format(CacheKey.USER_PERMISSIONS, userId);
        List<Permission> cached = redisUtil.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        User user = userMapper.selectById(userId);
        if (user == null || user.getRoleId() == null) {
            return new ArrayList<>();
        }
        
        LambdaQueryWrapper<RolePermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RolePermission::getRoleId, user.getRoleId());
        List<RolePermission> rolePermissions = rolePermissionMapper.selectList(wrapper);
        
        List<Long> permissionIds = rolePermissions.stream()
                .map(RolePermission::getPermissionId)
                .collect(Collectors.toList());
        
        if (permissionIds.isEmpty()) {
            return new ArrayList<>();
        }
        
        LambdaQueryWrapper<Permission> permissionWrapper = new LambdaQueryWrapper<>();
        permissionWrapper.in(Permission::getId, permissionIds)
                         .eq(Permission::getStatus, 1);
        List<Permission> permissions = this.list(permissionWrapper);
        
        // 存入缓存，有效期10分钟
        redisUtil.set(cacheKey, permissions, 10, TimeUnit.MINUTES);
        
        return permissions;
    }

    /**
     * 清除用户权限缓存
     */
    public void clearUserPermissionCache(Long userId) {
        String cacheKey = String.format(CacheKey.USER_PERMISSIONS, userId);
        redisUtil.delete(cacheKey);
    }

    private List<Permission> buildTree(List<Permission> permissions, Long parentId) {
        List<Permission> tree = new ArrayList<>();
        for (Permission permission : permissions) {
            if (parentId.equals(permission.getParentId())) {
                List<Permission> children = buildTree(permissions, permission.getId());
                permission.setChildren(children);
                tree.add(permission);
            }
        }
        return tree;
    }
}