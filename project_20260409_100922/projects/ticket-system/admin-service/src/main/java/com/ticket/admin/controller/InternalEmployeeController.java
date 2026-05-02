package com.ticket.admin.controller;

import com.ticket.admin.service.EmployeeService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/employees")
public class InternalEmployeeController {

    @Resource
    private EmployeeService employeeService;

    @GetMapping("/available-id")
    public Long availableEmployeeId() {
        return employeeService.findAvailableEmployee();
    }
}
