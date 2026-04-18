package com.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ticket.entity.Employee;
import org.apache.ibatis.annotations.Mapper;

/**
 * 员工 Mapper
 */
@Mapper
public interface EmployeeMapper extends BaseMapper<Employee> {
}