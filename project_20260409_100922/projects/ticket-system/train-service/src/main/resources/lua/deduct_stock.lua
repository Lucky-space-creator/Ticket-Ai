-- ============================================================
--  功能: 原子性预扣库存（下单时调用）
--  参数:
--    KEYS[1]   库存Key   train:stock:{trainId}:{date}:{seatType}
--    KEYS[2]   预占Key   train:locked:{trainId}:{date}:{seatType}
--    ARGV[1]   扣减数量  (正整数)
--    ARGV[2]   预占过期时间  秒，用于预占库存自动释放（防死锁）
--    ARGV[3]   库存过期时间  秒，用于库存Key自动清理
--  返回:
--    {0, 剩余库存}  表示扣减失败（库存不足）
--    {1, 剩余库存}  表示扣减成功
-- ============================================================

local stockKey = KEYS[1]
local lockedKey = KEYS[2]
local deductQty = tonumber(ARGV[1])
local lockedExpireSecs = tonumber(ARGV[2])
local stockExpireSecs = tonumber(ARGV[3])

-- 参数校验
if stockKey == nil or lockedKey == nil then
    return {-2, 0}
end
if deductQty == nil or deductQty <= 0 then
    return {-2, 0}
end

-- 获取当前可用库存（不存在视为0）
local currentStock = tonumber(redis.call('GET', stockKey) or '0')

-- 库存不足判定
if currentStock < deductQty then
    -- 返回失败 + 当前剩余量
    return {0, currentStock}
end

-- 原子操作：DECRBY 是原子的
local remaining = redis.call('DECRBY', stockKey, deductQty)

-- 记录预占数量（已扣减但尚未确认支付）
redis.call('INCRBY', lockedKey, deductQty)

-- 为预占Key设置过期（防止未支付订单永远占用库存）
redis.call('EXPIRE', lockedKey, lockedExpireSecs)

-- 为库存Key刷新过期时间（防止数据永久驻留）
redis.call('EXPIRE', stockKey, stockExpireSecs)

return {1, remaining}