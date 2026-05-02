package com.ticket.admin.interfaces.web;

import com.ticket.admin.service.EmployeeService;
import com.ticket.entity.Employee;
import com.ticket.util.ResponseUtil;
import com.ticket.util.UserContext;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/employees")
@CrossOrigin(origins = "*")
public class EmployeeController {

    @Resource
    private EmployeeService employeeService;

    @PostMapping("/login")
    public ResponseUtil.Result<Employee> login(@RequestParam String phone, @RequestParam String password) {
        try {
            Employee employee = employeeService.login(phone, password);
            return ResponseUtil.success(employee);
        } catch (RuntimeException e) {
            return ResponseUtil.error(e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseUtil.Result<Employee> getById(@PathVariable Long id) {
        Employee employee = employeeService.getById(id);
        if (employee == null) {
            return ResponseUtil.error("员工不存在");
        }
        return ResponseUtil.success(employee);
    }

    @GetMapping("/me")
    public ResponseUtil.Result<Employee> getCurrentEmployee() {
        Long employeeId = UserContext.getCurrentEmployeeId();
        if (employeeId == null) {
            return ResponseUtil.error("未登录");
        }
        Employee employee = employeeService.getById(employeeId);
        if (employee == null) {
            return ResponseUtil.error("员工不存在");
        }
        return ResponseUtil.success(employee);
    }

    @PutMapping("/{id}/status")
    public ResponseUtil.Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        boolean success = employeeService.updateStatus(id, status);
        if (success) {
            return ResponseUtil.success("状态更新成功");
        }
        return ResponseUtil.error("状态更新失败");
    }
}
