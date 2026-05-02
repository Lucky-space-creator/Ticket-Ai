package com.ticket.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ticket.admin.infrastructure.persistence.RoleMapper;
import com.ticket.admin.infrastructure.persistence.RolePermissionMapper;
import com.ticket.admin.mapper.UserMapper;
import com.ticket.admin.service.RoleService;
import com.ticket.entity.Role;
import com.ticket.entity.RolePermission;
import com.ticket.entity.User;
import com.ticket.service.PermissionService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class RoleServiceImpl extends ServiceImpl<RoleMapper, Role> implements RoleService {

    @Resource
    private RolePermissionMapper rolePermissionMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private PermissionService permissionService;

    @Override
    public Role createRole(String roleName, String roleDisplayName, String description) {
        Role role = new Role();
        role.setRoleName(roleName);
        role.setRoleDisplayName(roleDisplayName);
        role.setDescription(description);
        role.setStatus(1);
        role.setCreatedAt(LocalDateTime.now());
        role.setUpdatedAt(LocalDateTime.now());
        this.save(role);
        return role;
    }

    @Override
    public boolean updateRoleStatus(Long roleId, Integer status) {
        Role role = this.getById(roleId);
        if (role == null) {
            return false;
        }
        role.setStatus(status);
        role.setUpdatedAt(LocalDateTime.now());
        return this.updateById(role);
    }

    @Override
    @Transactional
    public boolean assignPermissions(Long roleId, Long[] permissionIds) {
        LambdaQueryWrapper<RolePermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RolePermission::getRoleId, roleId);
        rolePermissionMapper.delete(wrapper);
        if (permissionIds != null && permissionIds.length > 0) {
            for (Long permissionId : permissionIds) {
                RolePermission rp = new RolePermission();
                rp.setRoleId(roleId);
                rp.setPermissionId(permissionId);
                rp.setCreatedAt(LocalDateTime.now());
                rolePermissionMapper.insert(rp);
            }
        }
        clearUserPermissionCacheByRole(roleId);
        return true;
    }

    @Override
    public Long[] getRolePermissionIds(Long roleId) {
        LambdaQueryWrapper<RolePermission> w = new LambdaQueryWrapper<>();
        w.eq(RolePermission::getRoleId, roleId);
        return rolePermissionMapper.selectList(w).stream()
                .map(RolePermission::getPermissionId)
                .toArray(Long[]::new);
    }

    private void clearUserPermissionCacheByRole(Long roleId) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getRoleId, roleId);
        List<User> users = userMapper.selectList(wrapper);
        for (User user : users) {
            permissionService.clearUserPermissionCache(user.getId());
        }
    }
}
