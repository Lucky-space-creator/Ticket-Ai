# Ticket-AI · 基于 LangChain4j 的多智能体铁路票务客服平台

> 版本: v2.0.0
> 一个基于 Spring Cloud 微服务架构的铁路票务系统，集成「多智能体编排 + 混合检索 RAG」AI 客服，支持用户购票、AI 智能答疑与办单、人工客服实时转接、后台管理等完整业务闭环。

> **微服务运行说明**：业务运行以仓库根目录 `pom.xml` 子模块（`ticket-gateway`、`user-service`、`aichat-service`、`customer-service` 等）为准；`backend/` 仅作单体对照，不参与 Nacos 注册与网关路由，运行与分层改造要点见 [docs/microservices/README.md](docs/microservices/README.md)，基础设施一键启动见根目录 [docker-compose.yml](docker-compose.yml)。

> 状态: 开发完成，可直接运行

## 项目简介

本项目是一个基于 **Spring Cloud Alibaba 微服务架构**的铁路票务系统，在前端 Vue3 + 后端多服务的完整购票链路之上，构建了以 **LangChain4j** 为核心的 AI 智能客服：通过「编排器 + 专业 Agent」多智能体架构与「向量 + BM25 混合检索」RAG 知识库，实现能查、能答、能办的智能服务；常规咨询由 AI 自主解决（约 70%），复杂场景无缝转接 WebSocket 人工坐席，形成「AI 自助 + 人工兜底」的生产级客服闭环。

## 技术栈

### 后端 / 微服务
- **框架**: Spring Boot 3.2 / Spring Cloud Alibaba（Nacos 注册配置中心、Gateway 网关）
- **AI 框架**: LangChain4j + Ollama（本地大模型）
- **检索**: ChromaDB 向量库 + BM25 稀疏检索（RRF 融合）
- **消息队列**: RocketMQ（异步落库、事件驱动）
- **缓存 / 锁**: Redis + Redisson 分布式锁
- **数据库**: MySQL 8.0（MyBatis-Plus）
- **实时通讯**: WebSocket（人工客服双向通道）
- **可观测**: ELK（Filebeat → Logstash → Elasticsearch → Kibana）+ Actuator + TraceId 全链路追踪

### 前端
- **框架**: Vue 3.4 + Vite 5
- **UI 组件**: Element Plus
- **路由**: Vue Router 4
- **状态管理**: Pinia
- **HTTP 客户端**: Axios
- **日期处理**: Day.js

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

#### 管理端
- ✅ 车次管理
- ✅ 订单管理
- ✅ 用户管理
- ✅ 知识库管理

#### AI 智能客服
- ✅ 多智能体对话接口（IntentRouter 两级路由 + 5 个 Specialist Agent）
- ✅ 混合检索 RAG 知识库（向量 + BM25 + RRF 融合）
- ✅ LLM 集成（LangChain4j + Ollama 本地大模型）

## AI 智能客服核心特性

- **多智能体编排**：`IntentRouter` 实现「关键词规则优先 + LLM 兜底分类」两级意图路由，分派至
  **车次查询 / 订单办理 / 知识问答 / 个人信息 / 人工转接** 5 个 Specialist Agent；
  Agent 通过 Tool-Calling 调用订单、车次等下游微服务接口，实现「能查能答能办」，AI 自主解决率约 70%。
- **混合检索 RAG**：向量检索（ChromaDB）与 BM25 稀疏检索双路召回，经 **RRF 融合 + 治理表过滤**保障召回质量；
  支持单路 / 混合模式配置切换。
- **用户画像个性化**：对话记忆按用户隔离，自动注入用户画像系统消息，并按消息阈值 CAS 增量更新，实现个性化应答。
- **Token 成本治理**：自研 `TokenMonitor` 统一采集全局 / 分 Agent / 分用户 Token 消耗，设日限额与单请求限额告警，经 Actuator 暴露。
- **生产级人工客服**：基于 WebSocket 的实时会话（会话池、心跳、广播、待接入提醒），AI 与人工无缝转接；
  聊天记录经 RocketMQ 异步落库与广播，主链路与存储解耦。

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
