package com.ticket.train.service.impl;

import com.ticket.entity.Permission;
import com.ticket.service.PermissionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 权限服务实现（管理服务版本）
 * 由于admin-service不直接管理权限数据，此实现返回空权限列表
 * 实际项目中应通过Feign调用user-service获取权限数据
 */
@Slf4j
@Service
public class TrainPermissionServiceImpl implements PermissionService {

    @Override
    public List<Permission> getPermissionTree() {
        log.debug("admin-service: 获取权限树（空实现）");
        return new ArrayList<>();
    }

    @Override
    public List<Permission> getMenuPermissions() {
        log.debug("admin-service: 获取菜单权限（空实现）");
        return new ArrayList<>();
    }

    @Override
    public List<Permission> getApiPermissions() {
        log.debug("admin-service: 获取API权限（空实现）");
        return new ArrayList<>();
    }

    @Override
    public List<Permission> getPermissionsByUserId(Long userId) {
        log.debug("admin-service: 根据用户ID获取权限（空实现），userId={}", userId);
        return new ArrayList<>();
    }

    @Override
    public List<Permission> getPermissionsByEmployeeId(Long employeeId) {
        log.debug("admin-service: 根据员工ID获取权限（空实现），employeeId={}", employeeId);
        return new ArrayList<>();
    }

    @Override
    public void clearUserPermissionCache(Long userId) {
        log.debug("admin-service: 清除用户权限缓存（空实现），userId={}", userId);
    }

    @Override
    public List<Permission> getPermissionsByRoleId(Long roleId) {
        log.debug("admin-service: 根据角色ID获取权限（空实现），roleId={}", roleId);
        return new ArrayList<>();
    }

    @Override
    public List<Permission> getCurrentPermissions() {
        log.debug("admin-service: 获取当前权限（空实现）");
        return new ArrayList<>();
    }
}