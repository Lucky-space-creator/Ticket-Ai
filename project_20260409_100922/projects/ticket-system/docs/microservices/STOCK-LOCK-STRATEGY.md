# 库存：Lua 与 Redisson 边界（lock-atomicity-boundary）

## train-service `StockLockServiceImpl`

- **`tryDeduct`**：主路径仅执行 **Redis Lua 脚本**，在单次脚本执行内完成「可用库存扣减 + 预占增加」，满足 **单 key 空间内原子性**，**不**再套分布式锁。
- **`rollback` / `confirm`**：在 Lua 原子脚本外使用 **`RLock`** 包裹，用于与 **Redisson 降级分支**（脚本异常时的 `fallbackRollback` / `fallbackConfirm`）协调；学习时应区分：
  - **原子脚本**：防竞态、低延迟；
  - **分布式锁**：多步、跨结构或非脚本可表达的一致性时使用，避免在热路径「锁 + 脚本」无意义叠罗汉。
- **跨服务**：订单域仅通过 HTTP/Feign 调用 train 暴露的扣减/回滚接口，**不在 order-service 上再加分布式锁**充当事务。
