package com.ticket.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ticket.entity.Permission;

import java.util.List;

/**
 * 权限服务接口
 */
public interface PermissionService extends IService<Permission> {

    /**
     * 获取权限树
     */
    List<Permission> getPermissionTree();

    /**
     * 获取菜单权限列表
     */
    List<Permission> getMenuPermissions();

    /**
     * 获取API权限列表
     */
    List<Permission> getApiPermissions();

    /**
     * 根据用户ID获取权限列表
     */
    List<Permission> getPermissionsByUserId(Long userId);

    /**
     * 清除用户权限缓存
     */
    void clearUserPermissionCache(Long userId);
}