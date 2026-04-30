package com.ticket.user.controller;

import com.ticket.entity.User;
import com.ticket.entity.Role;
import com.ticket.enums.ResponseCode;
import com.ticket.user.service.UserService;
import com.ticket.user.service.RoleService;
import com.ticket.util.CryptoUtil;
import com.ticket.util.ResponseUtil;
import com.ticket.util.UserContext;
import jakarta.annotation.Resource;
import org.slf4j.LoggerFactory;
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

    @Resource
    private RoleService roleService;

    /**
     * 获取个人信息
     */
    @GetMapping("/profile")
    public ResponseUtil.Result<?> getProfile() {
        try {
            Long userId = UserContext.getCurrentUserId();
            if (userId == null) {
                return ResponseUtil.error(ResponseCode.UNAUTHORIZED);
            }

            User user = userService.getById(userId);

            if (user == null) {
                return ResponseUtil.error(ResponseCode.USER_NOT_FOUND);
            }

            // 返回用户信息
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("id", user.getId());
            userInfo.put("phone", user.getPhone());
            userInfo.put("realName", user.getRealName());
            // 返回解密后的身份证（用于购票）
            String rawIdCard = user.getIdCard();
            String decryptedIdCard = null;
            if (rawIdCard != null && !rawIdCard.isEmpty()) {
                decryptedIdCard = CryptoUtil.decrypt(rawIdCard);
                // 解密失败时记录警告
                if (decryptedIdCard == null) {
                    LoggerFactory.getLogger(UserController.class)
                            .warn("身份证解密返回null: userId={}, idCard(前10位)={}",
                                    userId, rawIdCard.length() > 10 ? rawIdCard.substring(0, 10) : rawIdCard);
                }
            }
            userInfo.put("idCard", decryptedIdCard);
            userInfo.put("status", user.getStatus());
            userInfo.put("roleId", user.getRoleId());
            // 获取角色显示名称
            if (user.getRoleId() != null) {
                Role role = roleService.getById(user.getRoleId());
                if (role != null) {
                    userInfo.put("roleName", role.getRoleDisplayName());
                }
            }

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
                return ResponseUtil.error(ResponseCode.UNAUTHORIZED);
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
    public static class UpdateProfileRequest {
        private String realName;
        private String idCard;
        
        public String getRealName() {
            return realName;
        }
        
        public void setRealName(String realName) {
            this.realName = realName;
        }
        
        public String getIdCard() {
            return idCard;
        }
        
        public void setIdCard(String idCard) {
            this.idCard = idCard;
        }
    }
}