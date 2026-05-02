# 契约、BFF、实时连接与数据层（api-contracts / bff-or-page-aggregation / realtime-connection-strategy / db-per-service-readmodel / redis-cache-governance）

## API 契约（api-contracts）

- 各服务已引入 **springdoc-openapi**，开发环境可访问 `/swagger-ui.html`（路径以实际配置为准）。
- **规则**：Feign 使用的 DTO 变更须 **向后兼容**（新增可选字段、避免随意改类型/删字段）；重大变更走新版本路径或消费者驱动契约（如 Pact）演练。

## BFF 与页面聚合（bff-or-page-aggregation）

- 订单详情等「多域读模型」推荐：**二选一**  
  - 独立 **BFF** 服务聚合；或  
  - 在 **`order-service` 增加聚合读接口**（内部 Feign user/train），前端只调一次。
- 目标：前端不串联多个异构 Base URL 拼页面。

## 实时连接（realtime-connection-strategy）

- AI **SSE**、客服 **WebSocket** 拆分后，应约定 **单一入口**（网关/BFF 代理）或 **统一会话 ID + Redis 会话**，避免上下文分散在多个长连接中无协调。

## 数据库（db-per-service-readmodel）

- 长期目标：**每服务独享库/Schema**，禁止跨域 SQL `JOIN` 他表。
- 学习沙盘：可先用 **不同 schema** 或文档级「禁止 JOIN」约束；报表/大屏走只读副本或 **CQRS** 同步。

## Redis（redis-cache-governance）

- 即使共用一个 Redis 集群，**key 必须带服务/聚合根前缀**（如 `order:`、`train:`），避免键冲突与误删。
- 用户信息等被多服务缓存时，变更后需 **TTL / 主动失效 / 事件广播** 策略，并在设计中写明一例（如改手机号）。
