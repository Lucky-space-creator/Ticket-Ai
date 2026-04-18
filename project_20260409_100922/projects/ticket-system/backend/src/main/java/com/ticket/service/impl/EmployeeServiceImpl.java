package com.ticket.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ticket.entity.Employee;
import com.ticket.mapper.EmployeeMapper;
import com.ticket.service.EmployeeService;
import com.ticket.util.CryptoUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * 员工服务实现类
 */
@Service
public class EmployeeServiceImpl extends ServiceImpl<EmployeeMapper, Employee> implements EmployeeService {

    @Resource
    private EmployeeMapper employeeMapper;

    @Override
    public Employee login(String phone, String password) {
        if (phone == null || phone.isEmpty() || password == null || password.isEmpty()) {
            throw new RuntimeException("手机号和密码不能为空");
        }

        // 根据手机号查询员工
        Employee employee = getByPhone(phone);
        if (employee == null) {
            throw new RuntimeException("员工不存在");
        }

        // 检查员工状态（1-在职）
        if (employee.getStatus() != null && employee.getStatus() == 0) {
            throw new RuntimeException("员工已离职");
        }

        // 验证密码
        if (!CryptoUtil.verifyPassword(password, employee.getPassword())) {
            throw new RuntimeException("密码错误");
        }

        return employee;
    }

    @Override
    public Employee getByPhone(String phone) {
        LambdaQueryWrapper<Employee> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Employee::getPhone, phone);
        return employeeMapper.selectOne(wrapper);
    }

    @Override
    public Employee getByEmployeeNo(String employeeNo) {
        LambdaQueryWrapper<Employee> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Employee::getEmployeeNo, employeeNo);
        return employeeMapper.selectOne(wrapper);
    }

    @Override
    public boolean updateStatus(Long employeeId, Integer status) {
        Employee employee = new Employee();
        employee.setId(employeeId);
        employee.setStatus(status);
        return employeeMapper.updateById(employee) > 0;
    }

    @Override
    public boolean updateEmployee(Long employeeId, Employee employee) {
        employee.setId(employeeId);
        return employeeMapper.updateById(employee) > 0;
    }
}