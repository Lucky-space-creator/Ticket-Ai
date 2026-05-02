# 本地环境与入口（env-gateway）

## 一键依赖

在项目根目录执行：

```bash
docker compose up -d
```

将启动：

- **Nacos** `8848`（控制台 `http://localhost:8848/nacos`，默认账号/密码常为 `nacos/nacos`，以镜像说明为准）
- **Redis** `6379`
- **MySQL** `3306`（root/root，库 `ticket_system`，首次启动执行 `database/init.sql`）
- **RocketMQ** NameServer `9876`，Broker `10911` 等

各微服务 `bootstrap.yml` 中 `spring.cloud.nacos.*.server-addr` 默认 `localhost:8848`，与 Compose 对齐。

## 应用启动顺序（建议）

1. 等待 Nacos 健康检查通过。
2. 启动 `user-service`、`train-service`、`order-service` 等（顺序不限，但依赖 Nacos/Redis/MySQL/RocketMQ 已就绪）。
3. 最后启动 **`ticket-gateway`**（默认 `server.port: 8080`），**前端与 Postman 只访问网关端口**，勿直连各服务端口，以免绕过限流与 JWT 校验。

## 健康检查

- 网关与各服务已引入 `spring-boot-starter-actuator`，可访问 `/actuator/health`（若未在网关屏蔽）。
- Nacos 控制台查看实例是否注册、元数据与健康状态。

## Nacos 单点（学习认知）

本地 Compose 中 Nacos 为单实例；配置与路由依赖其可用性。生产需另行规划集群与高可用，见 [GATEWAY-SECURITY.md](GATEWAY-SECURITY.md)。
