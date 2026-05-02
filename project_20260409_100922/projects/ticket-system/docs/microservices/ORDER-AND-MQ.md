# 订单链、MQ 与补偿（boundary-order-chain / mq-ownership / mq-reliable-coordination）

## 订单链与单体对照

| 能力 | 单体（backend 对照） | 微服务（order-service） |
|------|----------------------|-------------------------|
| 跨域车次/库存 | `TrainService` Bean | `TrainOrderGateway` → `TrainOrderFeignClient` → train-service |
| 下单写库 | `OrderServiceImpl` 本地事务 | HTTP 预扣（train）+ MQ 异步写库（order） |
| 补偿回滚 | 同进程 `rollbackStock` | `TrainOrderGateway.rollbackStock` |

详见 `order-service` 中 `OrderController`、`TrainOrderGateway`、`order/client/TrainOrderFeignClient`。

## RocketMQ Topic 归属（mq-ownership）

> **约定**：每个 Topic 仅允许**一个**业务消费者组在运行平面内消费，避免与 `backend` 同时在线导致重复消费。

| Topic 常量 | 值 | 生产者（典型） | 消费者（运行平面） |
|------------|-----|------------------|---------------------|
| `MQTopics.ORDER_QUEUE` | `order-queue` | `order-service`（`RocketMQProducerService`） | **`order-service`**（`OrderQueueConsumer`） |
| `MQTopics.TICKET_ORDER` | `ticket-order` | 订单创建后事件 | 规划：`order-service` 或独立通知服务（勿与 backend 重复监听） |
| `MQTopics.TICKET_PAYMENT` | `ticket-payment` | 支付确认后 | 规划：`train-service` 库存确认等 |
| `MQTopics.AI_CHAT_TRACE` | `ai-chat-trace` | `aichat-service` | 规划：持久化/WS 广播服务（单组） |
| `MQTopics.OPERATION_LOG` | `operation-log` | 各服务 | 规划：`admin-service` 或日志服务 |
| `MQTopics.KNOWLEDGE_SYNC` | `knowledge-sync` | `aichat-service` | 规划：`aichat-service` 内向量任务消费者 |
| `MQTopics.STOCK_RECONCILE` / `CACHE_EVENT` | 预留 | — | 未启用 |

**说明**：历史上消费者仅在 `backend` 中实现；微服务化后 **`order-queue` 的消费已迁移至 `order-service`**，请勿再让 `backend` 与微服务同时订阅同一 consumer group。

## 可靠消息与「伪异步」

- **已移除**：`order-service` 在 MQ 入队失败时**同步降级整单写入**的分支；入队失败时由 `OrderQueueServiceImpl` 回滚 Redis 预扣并返回明确错误，符合跨服务场景（无法再假定「本地同步调下游」）。
- **建议练习**：Outbox 表 / RocketMQ 事务消息 + 回查、订单–库存 **Saga 状态机** 文档化（超时、重复消费、补偿）。

## 库存与不确定状态

`order-service` 预扣（train Redis Lua）成功但后续失败时，须依赖 **`TrainOrderGateway.rollbackStock`** 等与 `train-service` 对齐的补偿接口；请在设计文档中画出状态迁移。

## Feign 超时（rpc-resilience-kit 基础项）

`order-service` 的 `application.yml` 已配置 `spring.cloud.openfeign.client.config` 的 **connectTimeout / readTimeout**（含 `train-service` 专用段）。熔断（Resilience4J/Sentinel）可在后续迭代中按需引入并与 Spring Cloud BOM 对齐版本。
