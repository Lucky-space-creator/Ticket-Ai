# 12306购票系统 + 智能客服

> 版本: v2.0.0  
> **微服务运行说明**：业务运行以仓库根目录 `pom.xml` 子模块（`ticket-gateway`、`user-service` 等）为准；`backend/` 仅作单体对照，不参与注册发现。本地依赖与分层改造要点见 [docs/microservices/README.md](docs/microservices/README.md)，一键基础设施见根目录 [docker-compose.yml](docker-compose.yml)。

> 状态: 开发完成，可直接运行

## 项目简介

这是一个基于 Spring Boot + Vue3 的精简版铁路票务系统，保留核心购票流程，预留了 AI 智能客服接口。系统采用前后端分离架构，后端使用 MyBatisPlus 进行数据交互，使用内存缓存替代 Redis，使用 JWT 进行用户认证。

## 技术栈

### 后端
- **框架**: Spring Boot 3.2.0
- **数据库**: MySQL 8.0+
- **ORM**: MyBatis Plus 3.5.5
- **认证**: JWT (jjwt)
- **加密**: BCrypt
- **工具**: Hutool、FastJSON2、Lombok

### 前端
- **框架**: Vue 3.4.0
- **构建工具**: Vite 5.0.0
- **UI组件**: Element Plus 2.5.0
- **路由**: Vue Router 4.2.5
- **状态管理**: Pinia 2.1.7
- **HTTP客户端**: Axios 1.6.2
- **日期处理**: Day.js 1.11.10

## 项目结构

```
ticket-system/
├── backend/                      # 后端项目
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/ticket/
│   │   │   │   ├── controller/   # 控制器层
│   │   │   │   ├── service/      # 服务层
│   │   │   │   ├── mapper/       # 数据访问层
│   │   │   │   ├── entity/       # 实体类
│   │   │   │   ├── dto/          # 数据传输对象
│   │   │   │   ├── config/       # 配置类
│   │   │   │   ├── util/         # 工具类
│   │   │   │   ├── constants/    # 常量定义
│   │   │   │   └── TicketSystemApplication.java
│   │   │   └── resources/
│   │   │       └── application.yml
│   │   └── test/
│   └── pom.xml
├── frontend/                     # 前端项目
│   ├── src/
│   │   ├── views/               # 页面组件
│   │   ├── router/              # 路由配置
│   │   ├── stores/              # 状态管理
│   │   ├── utils/               # 工具函数
│   │   ├── styles/              # 全局样式
│   │   ├── App.vue
│   │   └── main.js
│   ├── package.json
│   └── vite.config.js
└── database/
    └── init.sql                  # 数据库初始化脚本
```

## 快速开始

### 1. 环境要求

- **JDK**: 17+
- **Maven**: 3.6+
- **MySQL**: 8.0+
- **Node.js**: 16+

### 2. 数据库配置

创建数据库并执行初始化脚本：

```bash
# 创建数据库
mysql -u root -p

# 执行初始化脚本
source database/init.sql
```

默认数据库配置：
- 数据库名: `ticket_system`
- 用户名: `root`
- 密码: `root`

如需修改，请编辑 `backend/src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ticket_system?...
    username: your_username
    password: your_password
```

### 3. 后端启动

```bash
# 进入后端目录
cd backend

# 安装依赖
mvn install

# 启动项目
mvn spring-boot:run
```

后端服务将在 `http://localhost:8080` 启动。

### 4. 前端启动

```bash
# 进入前端目录
cd frontend

# 安装依赖
npm install

# 启动开发服务器
npm run dev
```

前端服务将在 `http://localhost:3000` 启动。

## 功能模块

### 已实现功能

#### 用户端
- ✅ 用户注册/登录
- ✅ 个人信息管理
- ✅ 常用联系人管理
- ✅ 车票查询（按起终点、日期）
- ✅ 车次列表展示
- ✅ 余票查询
- ✅ 订单创建
- ✅ 订单支付
- ✅ 订单退票
- ✅ 订单列表查看

#### 管理端（预留接口）
- 🔲 车次管理
- 🔲 订单管理
- 🔲 用户管理
- 🔲 知识库管理

#### AI 智能客服（预留接口）
- 🔲 对话接口
- 🔲 知识库查询
- 🔲 LLM 集成

## 核心特性

### 1. 统一返回格式

所有接口返回格式统一：

```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "timestamp": 1712602800000
}
```

### 2. JWT 认证

- Token 有效期: 7天
- 存储方式: LocalStorage
- 自动刷新: 无需手动

### 3. 内存缓存

使用 `CacheUtil` 替代 Redis，支持：
- 自动过期（默认30分钟）
- 定时清理（每5分钟）
- 线程安全（ConcurrentHashMap）

### 4. 数据库锁

使用 `SELECT ... FOR UPDATE` 防止超卖。

## API 接口文档

### 认证接口

#### 注册
```http
POST /api/auth/register
Content-Type: application/json

{
  "phone": "13800138000",
  "password": "123456"
}
```

#### 登录
```http
POST /api/auth/login
Content-Type: application/json

{
  "phone": "13800138000",
  "password": "123456"
}
```

### 车次接口

#### 搜索车次
```http
GET /api/trains/search?startStation=北京南&endStation=上海虹桥&trainDate=2024-04-10
```

### 订单接口

#### 创建订单
```http
POST /api/orders
Authorization: Bearer {token}
Content-Type: application/json

{
  "trainId": 1,
  "trainDate": "2024-04-10",
  "startStation": "北京南",
  "endStation": "上海虹桥",
  "seatType": 3,
  "items": [
    {
      "passengerName": "张三",
      "idCard": "110101199001011234"
    }
  ]
}
```

#### 支付订单
```http
POST /api/orders/{orderNo}/pay
Authorization: Bearer {token}
```

#### 退票
```http
POST /api/orders/{orderNo}/refund
Authorization: Bearer {token}
```

## 测试账号

系统已预置测试账号：

| 手机号 | 密码 | 备注 |
|--------|------|------|
| 13800138000 | 123456 | 测试用户1 |
| 13800138001 | 123456 | 测试用户2 |

**注意**: 密码已使用 BCrypt 加密存储。

## 测试数据

系统已预置测试数据：

- 车次: G101, G102, G103
- 余票: 未来7天
- 知识库: 4条常见问题

## 设计风格

采用 12306 现代化简约风格：

- **主色调**: #1890FF（铁路蓝）
- **辅助色**: #40A9FF（浅蓝）、#333333（深灰）
- **圆角**: 8px
- **字体**: 系统默认字体

## 工具类说明

### ResponseUtil
统一返回格式工具类。

### JwtUtil
JWT 生成和验证工具类。

### CacheUtil
内存缓存工具类（替代 Redis）。

### CryptoUtil
加密工具类（密码、身份证号）。

### DateUtils
日期处理工具类。

## 常量说明

### ResponseCode
响应码常量（200-5999）。

### CacheKey
缓存键常量。

### BusinessStatus
业务状态常量（用户、车次、订单等）。

## 后续开发计划

### 第一阶段（管理端）
1. 管理员登录
2. 车次管理
3. 订单管理
4. 用户管理

### 第二阶段（AI 客服）
1. 知识库管理
2. 关键词匹配
3. LLM 集成
4. 对话记录

### 第三阶段（完善）
1. 候补购票
2. 改签功能
3. 统计报表
4. 日志系统

## 注意事项

1. **生产环境部署前**:
   - 修改 JWT 密钥
   - 修改数据库密码
   - 启用 HTTPS
   - 配置跨域

2. **安全性**:
   - 密码使用 BCrypt 加密
   - 身份证号加密存储
   - SQL 注入防护（MyBatisPlus）
   - XSS 防护

3. **性能优化**:
   - 使用缓存减少数据库查询
   - 数据库索引优化
   - 前端代码分割

## 常见问题

### 1. 后端启动失败

检查：
- MySQL 是否启动
- 数据库配置是否正确
- 端口 8080 是否被占用

### 2. 前端无法连接后端

检查：
- 后端是否启动
- Vite 代理配置是否正确
- 跨域配置是否正确

### 3. 缓存不生效

检查：
- 缓存键是否正确
- 过期时间是否设置
- 是否手动清除缓存

## 许可证

MIT License

## 联系方式

如有问题，请联系项目维护者。

---

**祝您使用愉快！**
