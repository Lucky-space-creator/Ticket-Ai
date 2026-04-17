package com.ticket.controller.admin;

import com.ticket.entity.Role;
import com.ticket.service.RoleService;
import com.ticket.util.ResponseUtil;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端角色控制器
 */
@RestController
@RequestMapping("/api/admin/roles")
@CrossOrigin(origins = "*")
public class AdminRoleController {

    @Resource
    private RoleService roleService;

    /**
     * 获取角色列表
     */
    @GetMapping
    public ResponseUtil.Result<List<Role>> list() {
        List<Role> roles = roleService.list();
        return ResponseUtil.success(roles);
    }

    /**
     * 获取角色详情
     */
    @GetMapping("/{id}")
    public ResponseUtil.Result<Role> detail(@PathVariable Long id) {
        Role role = roleService.getById(id);
        if (role == null) {
            return ResponseUtil.error("角色不存在");
        }
        return ResponseUtil.success(role);
    }

    /**
     * 创建角色
     */
    @PostMapping
    public ResponseUtil.Result<Role> create(@RequestBody CreateRoleRequest request) {
        Role role = roleService.createRole(
            request.getRoleName(),
            request.getRoleDisplayName(),
            request.getDescription()
        );
        return ResponseUtil.success("创建成功", role);
    }

    /**
     * 更新角色
     */
    @PutMapping("/{id}")
    public ResponseUtil.Result<Role> update(@PathVariable Long id, @RequestBody UpdateRoleRequest request) {
        Role role = roleService.getById(id);
        if (role == null) {
            return ResponseUtil.error("角色不存在");
        }
        role.setRoleDisplayName(request.getRoleDisplayName());
        role.setDescription(request.getDescription());
        roleService.updateById(role);
        return ResponseUtil.success("更新成功", role);
    }

    /**
     * 更新角色状态
     */
    @PutMapping("/{id}/status")
    public ResponseUtil.Result<?> updateStatus(@PathVariable Long id, @RequestBody UpdateStatusRequest request) {
        boolean success = roleService.updateRoleStatus(id, request.getStatus());
        if (!success) {
            return ResponseUtil.error("更新失败");
        }
        return ResponseUtil.success("状态更新成功");
    }

    /**
     * 删除角色
     */
    @DeleteMapping("/{id}")
    public ResponseUtil.Result<?> delete(@PathVariable Long id) {
        Role role = roleService.getById(id);
        if (role == null) {
            return ResponseUtil.error("角色不存在");
        }
        roleService.removeById(id);
        return ResponseUtil.success("删除成功");
    }

    /**
     * 为角色分配权限
     */
    @PostMapping("/{id}/permissions")
    public ResponseUtil.Result<?> assignPermissions(@PathVariable Long id, @RequestBody AssignPermissionsRequest request) {
        boolean success = roleService.assignPermissions(id, request.getPermissionIds());
        if (!success) {
            return ResponseUtil.error("分配失败");
        }
        return ResponseUtil.success("权限分配成功");
    }

    /**
     * 获取角色权限ID列表
     */
    @GetMapping("/{id}/permissions")
    public ResponseUtil.Result<Long[]> getRolePermissions(@PathVariable Long id) {
        Long[] permissionIds = roleService.getRolePermissionIds(id);
        return ResponseUtil.success(permissionIds);
    }

    @lombok.Data
    public static class CreateRoleRequest {
        private String roleName;
        private String roleDisplayName;
        private String description;
    }

    @lombok.Data
    public static class UpdateRoleRequest {
        private String roleDisplayName;
        private String description;
    }

    @lombok.Data
    public static class UpdateStatusRequest {
        private Integer status;
    }

    @lombok.Data
    public static class AssignPermissionsRequest {
        private Long[] permissionIds;
    }
}