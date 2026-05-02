package com.ticket.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ticket.entity.Role;

/**
 * 角色服务接口
 */
public interface RoleService extends IService<Role> {

    /**
     * 创建角色
     */
    Role createRole(String roleName, String roleDisplayName, String description);

    /**
     * 更新角色状态
     */
    boolean updateRoleStatus(Long roleId, Integer status);

    /**
     * 为角色分配权限
     */
    boolean assignPermissions(Long roleId, Long[] permissionIds);

    /**
     * 获取角色权限ID列表
     */
    Long[] getRolePermissionIds(Long roleId);
}