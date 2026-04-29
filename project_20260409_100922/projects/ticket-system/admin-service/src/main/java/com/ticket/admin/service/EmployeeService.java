package com.ticket.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ticket.entity.Employee;

/**
 * 员工服务接口
 */
public interface EmployeeService extends IService<Employee> {

    /**
     * 员工登录
     * @param phone 手机号
     * @param password 密码
     * @return 员工信息
     */
    Employee login(String phone, String password);

    /**
     * 根据手机号查询员工
     */
    Employee getByPhone(String phone);

    /**
     * 根据工号查询员工
     */
    Employee getByEmployeeNo(String employeeNo);

    /**
     * 更新员工状态
     */
    boolean updateStatus(Long employeeId, Integer status);

    /**
     * 更新员工信息
     */
    boolean updateEmployee(Long employeeId, Employee employee);

    /**
     * 查找可用客服（空闲或会话数最少）
     * @return 员工ID，如果无可用客服返回null
     */
    Long findAvailableEmployee();
}