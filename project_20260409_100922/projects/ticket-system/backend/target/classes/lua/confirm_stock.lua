-- ============================================================
--  功能: 确认库存扣减（支付成功后调用，将预占转为实际售出）
--  参数:
--    KEYS[1]   预占Key
--    ARGV[1]   确认数量
--  返回:
--    {-1, 0}  确认量超过预占量
--    {1, 剩余预占量}  确认成功
-- ============================================================

local lockedKey = KEYS[1]
local confirmQty = tonumber(ARGV[1])

if confirmQty == nil or confirmQty <= 0 then
    return {-2, 0}
end

local locked = tonumber(redis.call('GET', lockedKey) or '0')
if locked < confirmQty then
    return {-1, 0}
end

local newLocked = redis.call('DECRBY', lockedKey, confirmQty)

return {1, newLocked}