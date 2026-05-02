package com.ticket.admin.controller;

import com.ticket.admin.service.EmployeeService;
import com.ticket.admin.service.RoleService;
import com.ticket.dto.UserLoginRequest;
import com.ticket.entity.Employee;
import com.ticket.entity.Role;
import com.ticket.util.JwtUtil;
import com.ticket.util.ResponseUtil;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/auth")
@CrossOrigin(origins = "*")
public class AdminAuthController {

    @Resource
    private EmployeeService employeeService;

    @Resource
    private RoleService roleService;

    @Resource
    private JwtUtil jwtUtil;

    @PostMapping("/login")
    public ResponseUtil.Result<?> login(@Valid @RequestBody UserLoginRequest request) {
        try {
            Employee employee = employeeService.login(request.getPhone(), request.getPassword());
            Long roleId = employee.getRoleId();
            String roleName = "";
            if (roleId != null) {
                Role role = roleService.getById(roleId);
                if (role != null) {
                    roleName = role.getRoleDisplayName();
                }
            }
            String token = jwtUtil.generateEmployeeTokenWithRole(
                    employee.getId(),
                    employee.getPhone(),
                    employee.getEmployeeNo(),
                    employee.getName(),
                    roleId,
                    roleName
            );
            Map<String, Object> data = new HashMap<>();
            data.put("token", token);
            data.put("employee", getEmployeeInfo(employee));
            return ResponseUtil.success("登录成功", data);
        } catch (RuntimeException e) {
            return ResponseUtil.error(401, e.getMessage());
        }
    }

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
