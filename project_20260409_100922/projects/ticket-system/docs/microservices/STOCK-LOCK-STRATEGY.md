# 库存：Lua 与 Redisson 边界（lock-atomicity-boundary）

## train-service `StockLockServiceImpl`

- **`tryDeduct`**：主路径仅执行 **Redis Lua 脚本**，在单次脚本执行内完成「可用库存扣减 + 预占增加」，满足 **单 key 空间内原子性**，**不**再套分布式锁。
- **`tryDeductBatch`**：联程/N 段 **`deduct_stocks_batch.lua`**，`KEYS = stock_1, locked_1, …, stock_N, locked_N`（`2N`），单次 `EVAL`，**先读后写**——任一段库存不足则 **全省**；热路径 **不配 `RLock`**（与单段预扣一致）。
- **`rollbackBatch`**：联程 **`rollback_stocks_batch.lua`**，先校验各段 `locked >= count`，再统一 `INCRBY` 可用 / `DECRBY` 预占；任一段预占不足则 **全省**。**不配 `RLock`**，与单段 `rollback`（脚本外带锁）路径不同，属有意取舍。
- **`confirmBatch`**：联程 **`confirm_stocks_batch.lua`**，仅传入各段 `locked` key（`N` 个），先校验再逐段 `DECRBY`。**不配 `RLock`**。
- **`rollback` / `confirm`（单段）**：在 Lua 原子脚本外使用 **`RLock`** 包裹，用于与 **Redisson 降级分支**（脚本异常时的 `fallbackRollback` / `fallbackConfirm`）协调；学习时应区分：
  - **原子脚本**：防竞态、低延迟；
  - **分布式锁**：多步、跨结构或非脚本可表达的一致性时使用，避免在热路径「锁 + 脚本」无意义叠罗汉。
- **跨服务**：订单域仅通过 HTTP/Feign 调用 train 暴露的扣减/回滚接口，**不在 order-service 上再加分布式锁**充当事务。

## 脚本与 Key 约定

- 单段直筒/管理端：仍用 `deduct_stock.lua` / `rollback_stock.lua` / `confirm_stock.lua`（confirm 仅消费 `KEYS[1]` 为 locked）。
- 线段 id 即 `TrainStockCommand.trainId` 拼 `CacheKey.formatTrainStockKey` / `formatTrainLockedKey`（部署为 **Redis 主从**；本方案 **不**假设 Cluster 多 slot）。

## 对内 HTTP（供 Feign）

- `POST /api/trains/internal/order/deduct-stocks-batch`：`deductStocksBatch`（Redis 批量预扣 + DB 每段 `ticket_stock` 存在性校验，与单段 `deduct-stock` 一致）。
- `POST /api/trains/internal/order/reservation/rollback-batch`：仅 Redis 释占（单笔 batch Lua），**不**调 MySQL。
- `POST /api/trains/internal/order/rollback-stocks-batch`：Redis 批量释占 + 各段 MySQL 可用席回填（与单段 `rollback-stock` 对齐）。
- `POST /api/trains/internal/order/confirm-stocks-batch`：支付侧批量确认预占（仅 Redis locked）。

`order-service` 经 [`TrainOrderFeignClient`](order-service/src/main/java/com/ticket/order/client/TrainOrderFeignClient.java) / [`TrainOrderGateway`](order-service/src/main/java/com/ticket/order/integration/TrainOrderGateway.java) 封装上述路径。

## 批量段落约束

- 同一 `List<TrainStockCommand>` 内：**`count`、`trainDate`、`seatType` 各段必须相同**；各段 `trainId`、起讫站可不同（联程）。

## 支付事件

- `PaymentConfirmedEvent.stockLegs` 非空时，`PaymentConfirmedConsumer` **只调** `confirmBatch(stockLegs)`，与预扣 legs 一致，避免「只确认一段 locked」。
