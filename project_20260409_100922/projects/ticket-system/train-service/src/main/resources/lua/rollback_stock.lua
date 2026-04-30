-- ============================================================
--  功能: 原子性回滚库存（下单失败或取消未支付订单时调用）
--  参数:
--    KEYS[1]   库存Key
--    KEYS[2]   预占Key
--    ARGV[1]   回滚数量
--    ARGV[2]   库存过期时间  秒，用于库存Key自动清理
--  返回:
--    {-1, 0}  回滚数量超过预占量（异常）
--    {1, 剩余预占量}  回滚成功
-- ============================================================

local stockKey = KEYS[1]
local lockedKey = KEYS[2]
local rollbackQty = tonumber(ARGV[1])
local stockExpireSecs = tonumber(ARGV[2])

if rollbackQty == nil or rollbackQty <= 0 then
    return {-2, 0}
end

-- 还原可用库存
redis.call('INCRBY', stockKey, rollbackQty)

-- 减少预占库存
local locked = tonumber(redis.call('GET', lockedKey) or '0')
if locked < rollbackQty then
    -- 异常保护：回滚量不应超过预占量，但仍执行防止库存不一致
    redis.call('SET', lockedKey, 0)
    return {-1, 0}
end

local newLocked = redis.call('DECRBY', lockedKey, rollbackQty)

-- 为库存Key刷新过期时间（防止数据永久驻留）
redis.call('EXPIRE', stockKey, stockExpireSecs)

return {1, newLocked}