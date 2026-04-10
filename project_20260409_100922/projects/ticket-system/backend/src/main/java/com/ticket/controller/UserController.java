package com.ticket.controller;

import com.ticket.entity.User;
import com.ticket.service.UserService;
import com.ticket.util.CryptoUtil;
import com.ticket.util.JwtUtil;
import com.ticket.util.ResponseUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

/**
 * 用户控制器
 */
@RestController
@RequestMapping("/api/user")
@CrossOrigin(origins = "*")
public class UserController {

    @Resource
    private UserService userService;

    @Resource
    private JwtUtil jwtUtil;

    /**
     * 获取当前用户ID
     */
    private Long getCurrentUserId(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
            return jwtUtil.getUserIdFromToken(token);
        }
        return null;
    }

    /**
     * 获取个人信息
     */
    @GetMapping("/profile")
    public ResponseUtil.Result<?> getProfile(HttpServletRequest request) {
        try {
            Long userId = getCurrentUserId(request);
            if (userId == null) {
                return ResponseUtil.error(com.ticket.enums.ResponseCode.UNAUTHORIZED);
            }

            User user = userService.getById(userId);

            if (user == null) {
                return ResponseUtil.error(com.ticket.enums.ResponseCode.USER_NOT_FOUND);
            }

            // 返回用户信息
            java.util.Map<String, Object> userInfo = new java.util.HashMap<>();
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
    public ResponseUtil.Result<?> updateProfile(
            @RequestBody UpdateProfileRequest updateRequest,
            HttpServletRequest request
    ) {
        try {
            Long userId = getCurrentUserId(request);
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
