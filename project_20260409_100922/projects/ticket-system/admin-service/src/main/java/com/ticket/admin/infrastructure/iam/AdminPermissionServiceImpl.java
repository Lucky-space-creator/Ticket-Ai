package com.ticket.admin.infrastructure.iam;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ticket.admin.infrastructure.persistence.PermissionMapper;
import com.ticket.admin.infrastructure.persistence.RolePermissionMapper;
import com.ticket.admin.mapper.EmployeeMapper;
import com.ticket.admin.mapper.UserMapper;
import com.ticket.entity.Employee;
import com.ticket.entity.Permission;
import com.ticket.entity.RolePermission;
import com.ticket.entity.User;
import com.ticket.enums.CacheKey;
import com.ticket.service.PermissionService;
import com.ticket.util.RedisUtil;
import com.ticket.util.UserContext;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 管理端所在限界上下文内的 RBAC 读模型与缓存（基础设施层），与 user-service 共享库表时的对等实现。
 */
@Service
public class AdminPermissionServiceImpl extends ServiceImpl<PermissionMapper, Permission> implements PermissionService {

    @Resource
    private RolePermissionMapper rolePermissionMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private EmployeeMapper employeeMapper;

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
        wrapper.eq(Permission::getPermissionType, 1)
                .eq(Permission::getStatus, 1)
                .orderByAsc(Permission::getSort);
        return this.list(wrapper);
    }

    @Override
    public List<Permission> getApiPermissions() {
        LambdaQueryWrapper<Permission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Permission::getPermissionType, 3)
                .eq(Permission::getStatus, 1);
        return this.list(wrapper);
    }

    @Override
    public List<Permission> getPermissionsByUserId(Long userId) {
        String cacheKey = String.format(CacheKey.USER_PERMISSIONS, userId);
        List<Permission> cached = redisUtil.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        User user = userMapper.selectById(userId);
        if (user == null || user.getRoleId() == null) {
            return new ArrayList<>();
        }
        List<Permission> permissions = getPermissionsByRoleId(user.getRoleId());
        redisUtil.set(cacheKey, permissions, 10, TimeUnit.MINUTES);
        return permissions;
    }

    @Override
    public List<Permission> getPermissionsByEmployeeId(Long employeeId) {
        String cacheKey = String.format("employee:permissions:%s", employeeId);
        List<Permission> cached = redisUtil.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        Employee employee = employeeMapper.selectById(employeeId);
        if (employee == null || employee.getRoleId() == null) {
            return new ArrayList<>();
        }
        List<Permission> permissions = getPermissionsByRoleId(employee.getRoleId());
        redisUtil.set(cacheKey, permissions, 10, TimeUnit.MINUTES);
        return permissions;
    }

    @Override
    public void clearUserPermissionCache(Long userId) {
        String cacheKey = String.format(CacheKey.USER_PERMISSIONS, userId);
        redisUtil.delete(cacheKey);
    }

    @Override
    public List<Permission> getPermissionsByRoleId(Long roleId) {
        if (roleId == null) {
            return new ArrayList<>();
        }
        String cacheKey = String.format("role:permissions:%s", roleId);
        List<Permission> cached = redisUtil.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        LambdaQueryWrapper<RolePermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RolePermission::getRoleId, roleId);
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
        redisUtil.set(cacheKey, permissions, 10, TimeUnit.MINUTES);
        return permissions;
    }

    @Override
    public List<Permission> getCurrentPermissions() {
        Long roleId = UserContext.getCurrentRoleId();
        if (roleId != null) {
            return getPermissionsByRoleId(roleId);
        }
        if (UserContext.isEmployeeLogin()) {
            Long employeeId = UserContext.getCurrentEmployeeId();
            if (employeeId != null) {
                return getPermissionsByEmployeeId(employeeId);
            }
        } else if (UserContext.isUserLogin()) {
            Long userId = UserContext.getCurrentUserId();
            if (userId != null) {
                return getPermissionsByUserId(userId);
            }
        }
        return new ArrayList<>();
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
