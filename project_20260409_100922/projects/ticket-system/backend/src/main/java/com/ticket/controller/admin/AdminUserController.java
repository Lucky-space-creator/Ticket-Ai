package com.ticket.controller.admin;

import com.ticket.entity.User;
import com.ticket.service.UserService;
import com.ticket.util.ResponseUtil;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端用户控制器
 */
@RestController
@RequestMapping("/api/admin/users")
@CrossOrigin(origins = "*")
public class AdminUserController {

    @Resource
    private UserService userService;

    /**
     * 获取用户列表
     */
    @GetMapping
    public ResponseUtil.Result<List<User>> list() {
        List<User> users = userService.list();
        // 过滤敏感信息，如密码
        users.forEach(user -> user.setPassword(null));
        return ResponseUtil.success(users);
    }

    /**
     * 更新用户状态
     */
    @PutMapping("/{id}/status")
    public ResponseUtil.Result<?> updateStatus(@PathVariable Long id, @RequestBody UpdateStatusRequest request) {
        User user = userService.getById(id);
        if (user == null) {
            return ResponseUtil.error("用户不存在");
        }
        user.setStatus(request.getStatus());
        userService.updateById(user);
        return ResponseUtil.success("状态更新成功");
    }

    /**
     * 更新用户角色
     */
    @PutMapping("/{id}/role")
    public ResponseUtil.Result<?> updateRole(@PathVariable Long id, @RequestBody UpdateRoleRequest request) {
        User user = userService.getById(id);
        if (user == null) {
            return ResponseUtil.error("用户不存在");
        }
        boolean success = userService.updateUserRole(id, request.getRoleId());
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