package com.ticket.user.controller;

import com.ticket.common.dto.UserLoginRequest;
import com.ticket.common.dto.UserRegisterRequest;
import com.ticket.common.entity.Role;
import com.ticket.common.entity.User;
import com.ticket.common.enums.CacheKey;
import com.ticket.common.enums.ResponseCode;
import com.ticket.common.util.*;
import com.ticket.user.service.RoleService;
import com.ticket.user.service.UserService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 认证控制器
 */
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Resource
    private UserService userService;

    @Resource
    private RoleService roleService;

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    private RedisUtil redisUtil;

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public ResponseUtil.Result<?> register(@Valid @RequestBody UserRegisterRequest request) {
        try {
            User user = userService.register(
                    request.getPhone(),
                    request.getPassword(),
                    request.getRealName(),
                    request.getIdCard()
            );

            // 获取角色信息
            Long roleId = user.getRoleId();
            String roleName = null;
            if (roleId != null) {
                Role role = roleService.getById(roleId);
                if (role != null) {
                    roleName = role.getRoleDisplayName();
                }
            }

            // 生成 Token（包含角色信息）
            String token = jwtUtil.generateToken(user.getId(), user.getPhone(), roleId, roleName);

            // 缓存用户信息（Token 7天，用户信息 30分钟）
            redisUtil.set(String.format(CacheKey.USER_TOKEN, user.getId()), token, 7, TimeUnit.DAYS);
            redisUtil.set(String.format(CacheKey.USER_INFO, user.getId()), user, 30, TimeUnit.MINUTES);

            Map<String, Object> data = new HashMap<>();
            data.put("token", token);
            data.put("user", getUserInfo(user));

            return ResponseUtil.success("注册成功", data);
        } catch (RuntimeException e) {
            return ResponseUtil.error(ResponseCode.USER_PHONE_EXIST.getCode(), e.getMessage());
        }
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public ResponseUtil.Result<?> login(@Valid @RequestBody UserLoginRequest request) {
        try {
            User user = userService.login(request.getPhone(), request.getPassword());

            // 获取角色信息
            Long roleId = user.getRoleId();
            String roleName = null;
            if (roleId != null) {
                Role role = roleService.getById(roleId);
                if (role != null) {
                    roleName = role.getRoleDisplayName();
                }
            }

            // 生成 Token（包含角色信息）
            String token = jwtUtil.generateToken(user.getId(), user.getPhone(), roleId, roleName);

            // 缓存用户信息（Token 7天，用户信息 30分钟）
            redisUtil.set(String.format(CacheKey.USER_TOKEN, user.getId()), token, 7, TimeUnit.DAYS);
            redisUtil.set(String.format(CacheKey.USER_INFO, user.getId()), user, 30, TimeUnit.MINUTES);

            Map<String, Object> data = new HashMap<>();
            data.put("token", token);
            data.put("user", getUserInfo(user));

            return ResponseUtil.success("登录成功", data);
        } catch (RuntimeException e) {
            return ResponseUtil.error(ResponseCode.USER_PASSWORD_ERROR.getCode(), e.getMessage());
        }
    }

    /**
     * 获取用户基本信息
     */
    private Map<String, Object> getUserInfo(User user) {
        Map<String, Object> info = new HashMap<>();
        info.put("id", user.getId());
        info.put("phone", user.getPhone());
        info.put("realName", user.getRealName());
        // 返回解密后的身份证（用于购票）
        info.put("idCard", user.getIdCard() != null ? CryptoUtil.decrypt(user.getIdCard()) : null);
        info.put("status", user.getStatus());
        info.put("roleId", user.getRoleId());
        // 获取角色显示名称
        if (user.getRoleId() != null) {
            Role role = roleService.getById(user.getRoleId());
            if (role != null) {
                info.put("roleName", role.getRoleDisplayName());
            }
        }
        return info;
    }
}