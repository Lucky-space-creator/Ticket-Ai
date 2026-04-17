# 12306后台管理系统 - 前端

## 项目概述

这是12306购票系统的后台管理前端，基于 Vue 3 + Element Plus + Pinia + Vue Router 构建。

## 功能模块

- **仪表盘**：数据概览、最近订单、系统信息
- **车次管理**：车次列表、添加/编辑车次、设置停运、配置余票
- **订单管理**：订单列表、订单详情、退票操作
- **用户管理**：用户列表、禁用/启用用户、角色分配
- **权限管理**：角色管理、权限分配、菜单权限控制
- **数据统计**：用户增长、订单趋势、销售额分析、系统访问量
- **知识库管理**：FAQ列表、添加/编辑/删除知识条目
- **操作日志监控**：管理员操作审计、系统变更追踪

## 快速启动

### 环境要求
- Node.js 16+
- npm 8+

### 安装依赖
```bash
npm install
```

### 启动开发服务器
```bash
npm run dev
```
前端将在 http://localhost:3001 启动。

### 构建生产版本
```bash
npm run build
```

## 后端接口联调

### 1. 启动后端服务
确保后端 Spring Boot 应用已启动：
```bash
cd ../backend
mvn spring-boot:run
```
后端默认运行在 http://localhost:8080。

### 2. 配置代理
前端通过 Vite 代理到后端，`vite.config.js` 中已配置：
```js
proxy: {
  '/api': {
    target: 'http://localhost:8080',
    changeOrigin: true
  }
}
```

### 3. 测试登录
使用测试账号登录：
- 手机号：13800138000
- 密码：123456

### 4. 测试各模块

#### 知识库管理
- 访问 http://localhost:3001/knowledge
- 列表应显示已有知识条目
- 可进行添加、编辑、删除操作

#### 车次管理
- 访问 http://localhost:3001/trains
- 列表显示车次信息
- 可添加、编辑车次，设置停运状态
- **注意**：余票设置功能需后端实现 `/api/admin/trains/{id}/stock` 接口

#### 订单管理
- 访问 http://localhost:3001/orders
- 显示所有订单列表
- 可查看订单详情，执行退票操作

#### 用户管理
- 访问 http://localhost:3001/users
- 显示用户列表
- 可禁用/启用用户账号

## 后端接口清单

### 管理端接口（新增）
| 模块 | 接口 | 方法 | 说明 |
|------|------|------|------|
| 车次 | `/api/admin/trains` | GET | 获取车次列表 |
| 车次 | `/api/admin/trains` | POST | 添加车次 |
| 车次 | `/api/admin/trains/{id}` | PUT | 更新车次 |
| 车次 | `/api/admin/trains/{id}/status` | PUT | 更新车次状态 |
| 车次 | `/api/admin/trains/{id}/stock` | POST | 设置余票 |
| 订单 | `/api/admin/orders` | GET | 获取所有订单 |
| 订单 | `/api/admin/orders/{orderNo}/refund` | POST | 退票 |
| 用户 | `/api/admin/users` | GET | 获取用户列表 |
| 用户 | `/api/admin/users/{id}/status` | PUT | 更新用户状态 |
| 用户 | `/api/admin/users/{id}/role` | PUT | 更新用户角色 |
| 角色 | `/api/admin/roles` | GET | 获取角色列表 |
| 角色 | `/api/admin/roles` | POST | 创建角色 |
| 角色 | `/api/admin/roles/{id}` | PUT | 更新角色 |
| 角色 | `/api/admin/roles/{id}/status` | PUT | 更新角色状态 |
| 角色 | `/api/admin/roles/{id}/permissions` | POST | 为角色分配权限 |
| 权限 | `/api/admin/permissions` | GET | 获取权限列表 |
| 权限 | `/api/admin/permissions/tree` | GET | 获取权限树 |
| 权限 | `/api/admin/permissions/{id}` | PUT | 更新权限 |
| 统计 | `/api/admin/stats/overview` | GET | 获取数据概览 |
| 统计 | `/api/admin/stats/trend/{type}` | GET | 获取趋势数据 |

### 现有接口（直接使用）
| 模块 | 接口 | 方法 | 说明 |
|------|------|------|------|
| 认证 | `/api/auth/login` | POST | 登录 |
| 用户 | `/api/user/profile` | GET | 获取当前用户信息 |
| 订单 | `/api/orders` | GET | 获取当前用户订单 |
| 订单 | `/api/orders/{orderNo}` | GET | 获取订单详情 |
| 订单 | `/api/orders/{orderNo}/refund` | POST | 退票 |
| 知识库 | `/api/knowledge/list` | GET | 获取知识库列表 |
| 知识库 | `/api/knowledge/add` | POST | 添加知识 |
| 知识库 | `/api/knowledge/update` | PUT | 更新知识 |
| 知识库 | `/api/knowledge/{id}` | DELETE | 删除知识 |

## 注意事项

1. **管理端权限**：当前系统未区分管理员和普通用户，所有登录用户均可访问管理端。生产环境应增加角色权限控制。
2. **数据安全**：用户身份证号等敏感信息已加密存储，前端显示时需解密（当前已处理）。
3. **接口兼容**：部分管理端接口为简化实现，可能需要根据实际业务完善。
4. **样式兼容**：使用 Element Plus 组件库，已导入全局样式。

## 常见问题

### 1. 前端无法连接后端
- 检查后端是否运行在 http://localhost:8080
- 查看浏览器控制台网络请求，确认代理是否正确

### 2. 登录后无法访问管理页面
- 检查登录接口返回的 token 是否存储正确（localStorage）
- 确认请求头中携带了 Authorization: Bearer <token>

### 3. 管理接口返回 404
- 确保后端已添加管理端控制器（`AdminTrainController` 等）
- 重启后端服务使新控制器生效

### 4. 页面样式异常
- 检查 Element Plus 样式是否正常导入
- 确认没有 CSS 冲突

## 开发建议

1. **状态管理**：使用 Pinia 管理全局状态，如用户信息、权限等。
2. **路由守卫**：已实现路由守卫，未登录用户重定向到登录页。
3. **请求封装**：`utils/request.js` 封装了 axios，统一处理 token 和错误。
4. **组件复用**：相似功能组件可提取为公共组件，减少重复代码。

## 后续优化方向

1. **权限管理**：增加角色（管理员、操作员）和权限控制。
2. **数据统计**：增加图表展示订单、用户增长等数据。
3. **操作日志**：记录管理员操作日志。
4. **批量操作**：支持批量导入车次、批量设置余票等。
5. **响应式设计**：适配移动端管理界面。

---

**祝您使用愉快！**