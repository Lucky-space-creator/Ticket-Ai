# 微服务改造与运行说明（学习型）

`backend/` 目录为**单体对照源码**，不参与 Nacos 注册与网关路由；**唯一运行平面**为根目录 `pom.xml` 下的 `ticket-gateway` 与各 `*-service`。

## 文档索引

| 文档 | 内容 |
|------|------|
| [LOCAL-ENVIRONMENT.md](LOCAL-ENVIRONMENT.md) | Docker Compose、Nacos、端口、健康检查 |
| [ORDER-AND-MQ.md](ORDER-AND-MQ.md) | 订单链、TrainOrderGateway、Topic 归属、可靠消息与 Saga 学习要点 |
| [GATEWAY-SECURITY.md](GATEWAY-SECURITY.md) | 网关 JWT、匿名路径、限流纵深、Nacos 依赖说明 |
| [CONTRACTS-BFF-DATABASE.md](CONTRACTS-BFF-DATABASE.md) | OpenAPI、BFF/长连接策略、库表边界、Redis 命名空间 |
| [STOCK-LOCK-STRATEGY.md](STOCK-LOCK-STRATEGY.md) | 库存 Lua 与 Redisson 职责边界 |
| [AI-GUARDRAILS.md](AI-GUARDRAILS.md) | AI 写路径护栏与代码策略 |

## 与单体（backend）的关系

- **不要**将 `backend` 加入父工程模块或注册到 Nacos。
- 对照迁移时，以各 `*-service` + `ticket-common` 为**唯一演进主线**，避免双轨长期分叉。
