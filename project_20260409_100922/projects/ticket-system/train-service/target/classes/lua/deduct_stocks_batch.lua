-- ============================================================
--  功能: N 线段原子预扣（联程单笔 EVAL）；KEYS = stock_1, locked_1, stock_2, locked_2, ...
--  ARGV[1]=扣减数量, ARGV[2]=预占过期秒, ARGV[3]=可用库存Key过期秒
--  返回: {1,剩余末段可用} 成功; {0,不足处当前可用} 任一段不足; {-2,0} 参数错误
-- ============================================================

local nKeys = #KEYS
if nKeys % 2 ~= 0 or nKeys < 2 then
    return {-2, 0}
end
local n = nKeys / 2
local deductQty = tonumber(ARGV[1])
local lockedExpireSecs = tonumber(ARGV[2])
local stockExpireSecs = tonumber(ARGV[3])

if deductQty == nil or deductQty <= 0 or lockedExpireSecs == nil or stockExpireSecs == nil then
    return {-2, 0}
end

for i = 1, n do
    local stockKey = KEYS[2 * i - 1]
    local currentStock = tonumber(redis.call('GET', stockKey) or '0')
    if currentStock < deductQty then
        return {0, currentStock}
    end
end

local lastRemain = 0
for i = 1, n do
    local stockKey = KEYS[2 * i - 1]
    local lockedKey = KEYS[2 * i]
    lastRemain = redis.call('DECRBY', stockKey, deductQty)
    redis.call('INCRBY', lockedKey, deductQty)
    redis.call('EXPIRE', lockedKey, lockedExpireSecs)
    redis.call('EXPIRE', stockKey, stockExpireSecs)
end

return {1, lastRemain}
