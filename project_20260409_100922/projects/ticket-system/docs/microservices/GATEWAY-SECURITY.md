# 网关、鉴权与限流（gateway-cross-cutting / gateway-nacos-ha-mindset / defense-in-depth-ratelimit）

## 单体「GatewayConfig」与真网关

- `backend` 中 `GatewayConfig` 仅为 **限流等参数的 `@ConfigurationProperties`**，并非 Spring Cloud Gateway 进程。
- 运行时入口为 **`ticket-gateway`**：路由、`RequestRateLimiter`（Redis）、以及 **`JwtAuthenticationGlobalFilter`**（校验 JWT、白名单路径）。

## JWT 与匿名路径

- 网关使用与业务相同的默认密钥配置项 **`jwt.secret`**（与 `ticket-common` 中 `JwtUtil` 对齐）。
- **匿名**（不校验 Bearer）：`OPTIONS` 全部；`POST /api/auth/login`、`POST /api/auth/register`；`GET /api/trains/search`；`GET /actuator/health`（及 `/actuator/health/**`）。
- 其余携带 `Authorization: Bearer …` 的请求将校验签名与过期；失败返回 **401**。

下游服务仍可通过既有 `AuthenticationInterceptor` 解析 JWT（**纵深防御**）；内网若存在**绕过网关**的调用，须在服务侧保留或加强校验（见下节）。

## 限流纵深（defense-in-depth-ratelimit）

| 层级 | 职责 | 本项目触点 |
|------|------|----------------|
| 南北向入口 | 用户流量整形、防刷 | `ticket-gateway` `RequestRateLimiter` + Redis |
| 领域服务 | 防绕过网关的内网滥用、保护线程池 | 各服务可保留/补强拦截器或 bucket（按需） |
| 服务间 Feign | 超时、熔断、重试仅幂等 | 见 `order-service` 等 `application.yml` 中 `spring.cloud.openfeign` / resilience 配置 |

## Nacos 与配置一致性

- 路由可从 Nacos 导入（`optional:nacos:…`）；**发布前**自检：每条对外 Path 是否落在受保护路由上、是否与 `application.yml` 默认路由冲突。
- 建议维护一份 **路由与 Filter 勾选表**（路径 → 鉴权 → 限流 → 下游服务名）。
