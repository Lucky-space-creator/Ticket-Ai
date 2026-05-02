package com.ticket.admin.controller;

import com.ticket.admin.service.AdminUserService;
import com.ticket.entity.User;
import com.ticket.util.ResponseUtil;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@CrossOrigin(origins = "*")
public class AdminUserController {

    @Resource
    private AdminUserService adminUserService;

    @GetMapping
    public ResponseUtil.Result<List<User>> list() {
        List<User> users = adminUserService.list();
        users.forEach(user -> user.setPassword(null));
        return ResponseUtil.success(users);
    }

    @PutMapping("/{id}/status")
    public ResponseUtil.Result<?> updateStatus(@PathVariable Long id, @RequestBody UpdateStatusRequest request) {
        User user = adminUserService.getById(id);
        if (user == null) {
            return ResponseUtil.error("用户不存在");
        }
        user.setStatus(request.getStatus());
        adminUserService.updateById(user);
        return ResponseUtil.success("状态更新成功");
    }

    @PutMapping("/{id}/role")
    public ResponseUtil.Result<?> updateRole(@PathVariable Long id, @RequestBody UpdateRoleRequest request) {
        User user = adminUserService.getById(id);
        if (user == null) {
            return ResponseUtil.error("用户不存在");
        }
        boolean success = adminUserService.updateUserRole(id, request.getRoleId());
        if (!success) {
            return ResponseUtil.error("更新失败");
        }
        return ResponseUtil.success("角色更新成功");
    }

    @lombok.Data
    public static class UpdateStatusRequest {
        private Integer status;
    }

    @lombok.Data
    public static class UpdateRoleRequest {
        private Long roleId;
    }
}
