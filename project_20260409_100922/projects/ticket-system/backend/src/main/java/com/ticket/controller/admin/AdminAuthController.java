package com.ticket.controller.admin;

import com.ticket.dto.UserLoginRequest;
import com.ticket.entity.Employee;
import com.ticket.service.EmployeeService;
import com.ticket.util.JwtUtil;
import com.ticket.util.ResponseUtil;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 管理员认证控制器
 * 处理员工（管理员）登录
 */
@RestController
@RequestMapping("/api/admin/auth")
@CrossOrigin(origins = "*")
public class AdminAuthController {

    @Resource
    private EmployeeService employeeService;

    @Resource
    private JwtUtil jwtUtil;

    /**
     * 员工登录
     */
    @PostMapping("/login")
    public ResponseUtil.Result<?> login(@Valid @RequestBody UserLoginRequest request) {
        try {
            // 调用员工服务登录
            Employee employee = employeeService.login(request.getPhone(), request.getPassword());

            // 生成员工令牌
            String token = jwtUtil.generateEmployeeToken(
                    employee.getId(),
                    employee.getPhone(),
                    employee.getEmployeeNo(),
                    employee.getName()
            );

            // 构造返回数据
            Map<String, Object> data = new HashMap<>();
            data.put("token", token);
            data.put("employee", getEmployeeInfo(employee));

            return ResponseUtil.success("登录成功", data);
        } catch (RuntimeException e) {
            return ResponseUtil.error(401, e.getMessage());
        }
    }

    /**
     * 获取员工基本信息（脱敏）
     */
    private Map<String, Object> getEmployeeInfo(Employee employee) {
        Map<String, Object> info = new HashMap<>();
        info.put("id", employee.getId());
        info.put("employeeNo", employee.getEmployeeNo());
        info.put("name", employee.getName());
        info.put("phone", employee.getPhone());
        info.put("email", employee.getEmail());
        info.put("departmentId", employee.getDepartmentId());
        info.put("position", employee.getPosition());
        info.put("status", employee.getStatus());
        return info;
    }
}