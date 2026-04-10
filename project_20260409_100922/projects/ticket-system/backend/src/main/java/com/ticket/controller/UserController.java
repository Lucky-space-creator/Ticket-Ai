package com.ticket.controller;

import com.ticket.entity.User;
import com.ticket.service.UserService;
import com.ticket.util.CryptoUtil;
import com.ticket.util.ResponseUtil;
import com.ticket.util.UserContext;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户控制器
 */
@RestController
@RequestMapping("/api/user")
@CrossOrigin(origins = "*")
public class UserController {

    @Resource
    private UserService userService;

    /**
     * 获取个人信息
     */
    @GetMapping("/profile")
    public ResponseUtil.Result<?> getProfile() {
        try {
            Long userId = UserContext.getCurrentUserId();
            if (userId == null) {
                return ResponseUtil.error(com.ticket.enums.ResponseCode.UNAUTHORIZED);
            }

            User user = userService.getById(userId);

            if (user == null) {
                return ResponseUtil.error(com.ticket.enums.ResponseCode.USER_NOT_FOUND);
            }

            // 返回用户信息
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("id", user.getId());
            userInfo.put("phone", user.getPhone());
            userInfo.put("realName", user.getRealName());
            // 返回解密后的身份证（用于购票）
            userInfo.put("idCard", user.getIdCard() != null ? CryptoUtil.decrypt(user.getIdCard()) : null);
            userInfo.put("status", user.getStatus());

            return ResponseUtil.success(userInfo);
        } catch (Exception e) {
            return ResponseUtil.error(e.getMessage());
        }
    }

    /**
     * 更新个人信息
     */
    @PutMapping("/profile")
    public ResponseUtil.Result<?> updateProfile(@RequestBody UpdateProfileRequest updateRequest) {
        try {
            Long userId = UserContext.getCurrentUserId();
            if (userId == null) {
                return ResponseUtil.error(com.ticket.enums.ResponseCode.UNAUTHORIZED);
            }

            boolean result = userService.updateProfile(
                    userId,
                    updateRequest.getRealName(),
                    updateRequest.getIdCard()
            );

            if (result) {
                return ResponseUtil.success("更新成功");
            } else {
                return ResponseUtil.error("更新失败");
            }
        } catch (Exception e) {
            return ResponseUtil.error(e.getMessage());
        }
    }

    /**
     * 更新个人信息请求DTO
     */
    @lombok.Data
    public static class UpdateProfileRequest {
        private String realName;
        private String idCard;
    }
}
