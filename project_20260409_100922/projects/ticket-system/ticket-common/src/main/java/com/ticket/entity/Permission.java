package com.ticket.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 权限实体
 */
@Data
@TableName("permission")
public class Permission {

    /**
     * 权限ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 权限名称（唯一标识）
     */
    private String permissionName;

    /**
     * 权限显示名称
     */
    private String permissionDisplayName;

    /**
     * 权限类型 1-菜单 2-按钮 3-API
     */
    private Integer permissionType;

    /**
     * 父权限ID
     */
    private Long parentId;

    /**
     * 前端路由路径（菜单权限使用）
     */
    private String path;

    /**
     * 前端组件路径（菜单权限使用）
     */
    private String component;

    /**
     * 菜单图标
     */
    private String icon;

    /**
     * 排序序号
     */
    private Integer sort;

    /**
     * API请求方法（GET/POST/PUT/DELETE）
     */
    private String apiMethod;

    /**
     * API路径（API权限使用）
     */
    private String apiPath;

    /**
     * 权限描述
     */
    private String description;

    /**
     * 状态 0-禁用 1-启用
     */
    private Integer status;

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

    /**
     * 子权限列表（非数据库字段）
     */
    @TableField(exist = false)
    private List<Permission> children;
}