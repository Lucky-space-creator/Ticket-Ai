package com.ticket.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ticket.entity.ChatSession;
import com.ticket.entity.Employee;
import com.ticket.admin.mapper.ChatSessionMapper;
import com.ticket.admin.mapper.EmployeeMapper;
import com.ticket.admin.service.EmployeeService;
import com.ticket.common.util.CryptoUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * 员工服务实现类
 */
@Service
public class EmployeeServiceImpl extends ServiceImpl<EmployeeMapper, Employee> implements EmployeeService {

    @Resource
    private EmployeeMapper employeeMapper;

    @Resource
    private ChatSessionMapper chatSessionMapper;

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

    @Override
    public Long findAvailableEmployee() {
        // 查询所有在职员工
        LambdaQueryWrapper<Employee> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Employee::getStatus, 1); // 1-在职
        List<Employee> employees = employeeMapper.selectList(wrapper);
        if (employees.isEmpty()) {
            return null;
        }
        Long bestEmployeeId = null;
        int minActiveSessions = Integer.MAX_VALUE;
        for (Employee employee : employees) {
            // 查询该员工的活跃会话数
            LambdaQueryWrapper<ChatSession> sessionWrapper = new LambdaQueryWrapper<>();
            sessionWrapper.eq(ChatSession::getEmployeeId, employee.getId())
                    .eq(ChatSession::getStatus, ChatSession.STATUS_ACTIVE);
            int activeCount = chatSessionMapper.selectCount(sessionWrapper).intValue();
            if (activeCount == 0) {
                // 完全空闲，直接返回
                return employee.getId();
            }
            if (activeCount < minActiveSessions) {
                minActiveSessions = activeCount;
                bestEmployeeId = employee.getId();
            }
        }
        return bestEmployeeId;
    }
}