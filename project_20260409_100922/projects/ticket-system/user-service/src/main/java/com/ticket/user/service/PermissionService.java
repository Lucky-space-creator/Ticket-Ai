package com.ticket.user.service;

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
     * 根据员工ID获取权限列表
     */
    List<Permission> getPermissionsByEmployeeId(Long employeeId);

    /**
     * 清除用户权限缓存
     */
    void clearUserPermissionCache(Long userId);

    /**
     * 根据角色ID获取权限列表
     */
    List<Permission> getPermissionsByRoleId(Long roleId);

    /**
     * 获取当前登录主体的权限列表
     * 根据UserContext自动识别身份类型（员工或用户）
     */
    List<Permission> getCurrentPermissions();
}