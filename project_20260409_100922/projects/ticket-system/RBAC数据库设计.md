# RBAC（基于角色的访问控制）数据库设计文档

## 概述

本系统采用 RBAC（Role-Based Access Control）模型实现权限管理，支持多级角色、精细化权限控制，适用于后台管理系统的权限管理需求。

## 核心概念

- **用户（User）**：系统的使用者，可分配一个或多个角色
- **角色（Role）**：权限的集合，如超级管理员、管理员、操作员等
- **权限（Permission）**：系统中的最小权限单元，分为菜单权限、按钮权限、API权限
- **角色权限关联（RolePermission）**：角色与权限的多对多关系

## 数据库表设计

### 1. 角色表（role）

| 字段名 | 类型 | 长度 | 可空 | 默认值 | 说明 |
|--------|------|------|------|--------|------|
| id | BIGINT | | 否 | 自增 | 角色ID，主键 |
| role_name | VARCHAR | 50 | 否 | | 角色名称（如：super_admin） |
| role_display_name | VARCHAR | 50 | 否 | | 角色显示名称（如：超级管理员） |
| description | VARCHAR | 200 | 是 | | 角色描述 |
| status | TINYINT | | 否 | 1 | 状态：0-禁用，1-启用 |
| created_at | DATETIME | | 否 | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | | 否 | CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

**索引**：
- 唯一索引：`uk_role_name` (`role_name`)

### 2. 权限表（permission）

| 字段名 | 类型 | 长度 | 可空 | 默认值 | 说明 |
|--------|------|------|------|--------|------|
| id | BIGINT | | 否 | 自增 | 权限ID，主键 |
| permission_name | VARCHAR | 100 | 否 | | 权限名称（唯一标识） |
| permission_display_name | VARCHAR | 100 | 否 | | 权限显示名称 |
| permission_type | TINYINT | | 否 | | 权限类型：1-菜单，2-按钮，3-API |
| parent_id | BIGINT | | 是 | 0 | 父权限ID，用于构建权限树 |
| path | VARCHAR | 200 | 是 | | 前端路由路径（菜单权限使用） |
| component | VARCHAR | 200 | 是 | | 前端组件路径（菜单权限使用） |
| icon | VARCHAR | 50 | 是 | | 菜单图标 |
| sort | INT | | 否 | 0 | 排序序号 |
| api_method | VARCHAR | 10 | 是 | | API请求方法（GET/POST/PUT/DELETE） |
| api_path | VARCHAR | 200 | 是 | | API路径（API权限使用） |
| description | VARCHAR | 200 | 是 | | 权限描述 |
| created_at | DATETIME | | 否 | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | | 否 | CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

**索引**：
- 唯一索引：`uk_permission_name` (`permission_name`)
- 普通索引：`idx_parent_id` (`parent_id`)
- 普通索引：`idx_permission_type` (`permission_type`)

### 3. 角色权限关联表（role_permission）

| 字段名 | 类型 | 长度 | 可空 | 默认值 | 说明 |
|--------|------|------|------|--------|------|
| id | BIGINT | | 否 | 自增 | 主键ID |
| role_id | BIGINT | | 否 | | 角色ID |
| permission_id | BIGINT | | 否 | | 权限ID |
| created_at | DATETIME | | 否 | CURRENT_TIMESTAMP | 创建时间 |

**索引**：
- 唯一索引：`uk_role_permission` (`role_id`, `permission_id`)
- 普通索引：`idx_role_id` (`role_id`)
- 普通索引：`idx_permission_id` (`permission_id`)

### 4. 用户表更新（user）

在原有用户表基础上增加角色ID字段：

```sql
ALTER TABLE `user` ADD COLUMN `role_id` BIGINT DEFAULT NULL COMMENT '角色ID' AFTER `status`;
```

## 初始化数据

### 预定义角色

1. **超级管理员（super_admin）**：拥有系统所有权限
2. **管理员（admin）**：拥有大部分管理权限，但无法管理系统用户和角色
3. **操作员（operator）**：拥有基础操作权限，如查看订单、处理退票等

### 权限结构（树状）

```
- 系统管理（type=1）
  ├─ 用户管理（type=1）
  │   ├─ 用户列表（type=1, path=/users）
  │   │   ├─ 禁用用户（type=2）
  │   │   └─ 启用用户（type=2）
  │   └─ 添加用户（type=2）
  ├─ 角色管理（type=1）
  │   ├─ 角色列表（type=1, path=/roles）
  │   │   ├─ 添加角色（type=2）
  │   │   ├─ 编辑角色（type=2）
  │   │   └─ 删除角色（type=2）
  │   └─ 权限分配（type=2）
  └─ 操作日志（type=1, path=/logs）
- 车次管理（type=1）
  ├─ 车次列表（type=1, path=/trains）
  │   ├─ 添加车次（type=2）
  │   ├─ 编辑车次（type=2）
  │   ├─ 停运车次（type=2）
  │   └─ 设置余票（type=2）
  └─ 车站管理（type=1, path=/stations）
- 订单管理（type=1）
  ├─ 订单列表（type=1, path=/orders）
  │   └─ 退票操作（type=2）
  └─ 订单统计（type=1, path=/order-stats）
- 知识库管理（type=1）
  └─ 知识列表（type=1, path=/knowledge）
      ├─ 添加知识（type=2）
      ├─ 编辑知识（type=2）
      └─ 删除知识（type=2）
- 智能客服（type=1）
  └─ 对话记录（type=1, path=/chat-records）
```

### API权限示例

| 权限名称 | 显示名称 | 类型 | 方法 | 路径 | 描述 |
|----------|----------|------|------|------|------|
| user:list:api | 用户列表API | 3 | GET | /api/admin/users | 获取用户列表接口 |
| user:status:api | 更新用户状态API | 3 | PUT | /api/admin/users/{id}/status | 更新用户状态接口 |
| train:list:api | 车次列表API | 3 | GET | /api/admin/trains | 获取车次列表接口 |
| train:add:api | 添加车次API | 3 | POST | /api/admin/trains | 添加车次接口 |

## 使用示例

### 1. 查询用户权限

```sql
-- 查询用户拥有的所有权限
SELECT p.* 
FROM user u
JOIN role r ON u.role_id = r.id
JOIN role_permission rp ON r.id = rp.role_id
JOIN permission p ON rp.permission_id = p.id
WHERE u.id = 1 AND r.status = 1 AND p.status = 1
ORDER BY p.sort ASC;
```

### 2. 检查用户是否有特定API权限

```sql
-- 检查用户是否有访问某个API的权限
SELECT COUNT(*) > 0 AS has_permission
FROM user u
JOIN role r ON u.role_id = r.id
JOIN role_permission rp ON r.id = rp.role_id
JOIN permission p ON rp.permission_id = p.id
WHERE u.id = 1 
  AND r.status = 1 
  AND p.status = 1
  AND p.permission_type = 3  -- API权限
  AND p.api_method = 'GET' 
  AND p.api_path = '/api/admin/users';
```

## 集成指南

### 后端集成

1. **JWT Token扩展**：在JWT中增加角色ID和角色名称声明
2. **权限拦截器**：在API请求前检查用户是否有访问权限
3. **权限注解**：使用`@RequiresPermission("user:list:api")`注解保护API

### 前端集成

1. **动态路由**：根据用户权限动态生成侧边栏菜单
2. **按钮控制**：根据权限控制按钮的显示/隐藏
3. **API拦截**：请求前检查是否有对应API权限

## 维护建议

1. **权限粒度**：建议按功能模块划分权限，避免过度细化
2. **角色设计**：根据实际岗位职责设计角色，一般3-5个角色足够
3. **定期审核**：定期检查权限分配，避免权限过度集中
4. **备份恢复**：定期备份角色权限数据，防止误操作

---

*文档版本：1.0*  
*更新日期：2026-04-17*  
*适用版本：12306购票系统 v2.0.0+*