package com.ticket.user.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.entity.User;
import com.ticket.user.service.UserService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 微服务间 B2B 调用：供订单等服务按条件查询用户，不暴露给网关前端路径以外的滥用场景时可再加鉴权
 */
@RestController
@RequestMapping("/api/internal/users")
@CrossOrigin(origins = "*")
public class InternalUserController {

    @Resource
    private UserService userService;

    @GetMapping("/ids-by-phone")
    public List<Long> listUserIdsByPhone(@RequestParam String phone) {
        LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.like(User::getPhone, "%" + phone.trim() + "%");
        return userService.list(userWrapper).stream()
                .map(User::getId)
                .collect(Collectors.toList());
    }
}
