package com.ticket.admin.controller;

import com.ticket.admin.service.RoleService;
import com.ticket.annotation.OperationLog;
import com.ticket.entity.Role;
import com.ticket.operationlog.Module;
import com.ticket.operationlog.Operation;
import com.ticket.util.ResponseUtil;
import jakarta.annotation.Resource;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/roles")
@CrossOrigin(origins = "*")
public class AdminRoleController {

    @Resource
    private RoleService roleService;

    @GetMapping
    public ResponseUtil.Result<List<Role>> list() {
        return ResponseUtil.success(roleService.list());
    }

    @GetMapping("/{id}")
    public ResponseUtil.Result<Role> detail(@PathVariable("id") Long id) {
        Role role = roleService.getById(id);
        if (role == null) {
            return ResponseUtil.error("角色不存在");
        }
        return ResponseUtil.success(role);
    }

    @OperationLog(module = Module.ROLE_PERMISSION, operation = Operation.CREATE_ROLE)
    @PostMapping
    public ResponseUtil.Result<Role> create(@RequestBody CreateRoleRequest request) {
        Role role = roleService.createRole(
                request.getRoleName(),
                request.getRoleDisplayName(),
                request.getDescription()
        );
        return ResponseUtil.success("创建成功", role);
    }

    @OperationLog(module = Module.ROLE_PERMISSION, operation = Operation.UPDATE_ROLE)
    @PutMapping("/{id}")
    public ResponseUtil.Result<Role> update(@PathVariable("id") Long id, @RequestBody UpdateRoleRequest request) {
        Role role = roleService.getById(id);
        if (role == null) {
            return ResponseUtil.error("角色不存在");
        }
        role.setRoleDisplayName(request.getRoleDisplayName());
        role.setDescription(request.getDescription());
        roleService.updateById(role);
        return ResponseUtil.success("更新成功", role);
    }

    @OperationLog(module = Module.ROLE_PERMISSION, operation = Operation.UPDATE_ROLE_STATUS)
    @PutMapping("/{id}/status")
    public ResponseUtil.Result<?> updateStatus(@PathVariable("id") Long id, @RequestBody UpdateStatusRequest request) {
        boolean success = roleService.updateRoleStatus(id, request.getStatus());
        if (!success) {
            return ResponseUtil.error("更新失败");
        }
        return ResponseUtil.success("状态更新成功");
    }

    @OperationLog(module = Module.ROLE_PERMISSION, operation = Operation.DELETE_ROLE)
    @DeleteMapping("/{id}")
    public ResponseUtil.Result<?> delete(@PathVariable("id") Long id) {
        Role role = roleService.getById(id);
        if (role == null) {
            return ResponseUtil.error("角色不存在");
        }
        roleService.removeById(id);
        return ResponseUtil.success("删除成功");
    }

    @OperationLog(module = Module.ROLE_PERMISSION, operation = Operation.ASSIGN_PERMISSIONS, description = "为角色绑定权限点")
    @PostMapping("/{id}/permissions")
    public ResponseUtil.Result<?> assignPermissions(@PathVariable("id") Long id, @RequestBody AssignPermissionsRequest request) {
        boolean success = roleService.assignPermissions(id, request.getPermissionIds());
        if (!success) {
            return ResponseUtil.error("分配失败");
        }
        return ResponseUtil.success("权限分配成功");
    }

    @GetMapping("/{id}/permissions")
    public ResponseUtil.Result<Long[]> getRolePermissions(@PathVariable("id") Long id) {
        return ResponseUtil.success(roleService.getRolePermissionIds(id));
    }

    @Data
    public static class CreateRoleRequest {
        private String roleName;
        private String roleDisplayName;
        private String description;
    }

    @Data
    public static class UpdateRoleRequest {
        private String roleDisplayName;
        private String description;
    }

    @Data
    public static class UpdateStatusRequest {
        private Integer status;
    }

    @Data
    public static class AssignPermissionsRequest {
        private Long[] permissionIds;
    }
}
