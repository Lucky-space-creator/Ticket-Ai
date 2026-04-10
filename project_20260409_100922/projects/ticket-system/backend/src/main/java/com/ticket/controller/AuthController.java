package com.ticket.controller;

import com.ticket.enums.CacheKey;
import com.ticket.enums.ResponseCode;
import com.ticket.dto.UserLoginRequest;
import com.ticket.dto.UserRegisterRequest;
import com.ticket.entity.User;
import com.ticket.service.UserService;
import com.ticket.util.RedisUtil;
import com.ticket.util.JwtUtil;
import com.ticket.util.ResponseUtil;
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

            // 生成 Token
            String token = jwtUtil.generateToken(user.getId(), user.getPhone());

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

            // 生成 Token
            String token = jwtUtil.generateToken(user.getId(), user.getPhone());

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
     * 获取用户基本信息（不含敏感信息）
     */
    private Map<String, Object> getUserInfo(User user) {
        Map<String, Object> info = new HashMap<>();
        info.put("id", user.getId());
        info.put("phone", user.getPhone());
        info.put("realName", user.getRealName());
        info.put("status", user.getStatus());
        return info;
    }
}
