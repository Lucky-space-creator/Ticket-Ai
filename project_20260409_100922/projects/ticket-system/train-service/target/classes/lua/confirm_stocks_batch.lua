-- ============================================================
--  功能: N 线段原子支付确认；KEYS = locked_1..locked_N
--  ARGV[1]=确认数量（各段相同）
--  先校验 forall locked>=qty；任一不足全省
--  返回: {1,末段locked剩余} 成功; {-1,段号} 预占不足; {-2,0} 参数错误
-- ============================================================

local n = #KEYS
if n < 1 then
    return {-2, 0}
end

local confirmQty = tonumber(ARGV[1])

if confirmQty == nil or confirmQty <= 0 then
    return {-2, 0}
end

for i = 1, n do
    local lockedKey = KEYS[i]
    local locked = tonumber(redis.call('GET', lockedKey) or '0')
    if locked < confirmQty then
        return {-1, i}
    end
end

local lastLockedRemain = 0
for i = 1, n do
    lastLockedRemain = redis.call('DECRBY', KEYS[i], confirmQty)
end

return {1, lastLockedRemain}
