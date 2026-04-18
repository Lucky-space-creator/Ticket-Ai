package com.ticket.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 员工实体
 */
@Data
@TableName("employee")
public class Employee {

    /**
     * 员工ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 员工工号（唯一）
     */
    private String employeeNo;

    /**
     * 员工姓名
     */
    private String name;

    /**
     * 性别 0-未知 1-男 2-女
     */
    private Integer gender;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 加密密码
     */
    private String password;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 所属部门ID
     */
    private Long departmentId;

    /**
     * 职位
     */
    private String position;

    /**
     * 入职日期
     */
    private LocalDate hireDate;

    /**
     * 身份证号（加密存储）
     */
    private String idCard;

    /**
     * 状态 0-离职 1-在职
     */
    private Integer status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}