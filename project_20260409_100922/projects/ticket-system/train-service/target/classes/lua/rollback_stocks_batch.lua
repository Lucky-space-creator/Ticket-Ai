-- ============================================================
--  功能: N 线段原子释占/回滚；KEYS = stock_1, locked_1, ...
--  ARGV[1]=回滚数量; ARGV[2]=stock Key 过期秒
--  先校验forall locked>=qty；任一段不满足则不写任何 KEY
--  返回: {1,末段locked剩余} 成功; {-1,段号索引1..n或0} 预占不足; {-2,0} 参数错误
-- ============================================================

local nKeys = #KEYS
if nKeys % 2 ~= 0 or nKeys < 2 then
    return {-2, 0}
end
local n = nKeys / 2
local rollbackQty = tonumber(ARGV[1])
local stockExpireSecs = tonumber(ARGV[2])

if rollbackQty == nil or rollbackQty <= 0 or stockExpireSecs == nil then
    return {-2, 0}
end

for i = 1, n do
    local lockedKey = KEYS[2 * i]
    local locked = tonumber(redis.call('GET', lockedKey) or '0')
    if locked < rollbackQty then
        return {-1, i}
    end
end

local lastLockedRemain = 0
for i = 1, n do
    local stockKey = KEYS[2 * i - 1]
    local lockedKey = KEYS[2 * i]
    redis.call('INCRBY', stockKey, rollbackQty)
    lastLockedRemain = redis.call('DECRBY', lockedKey, rollbackQty)
    redis.call('EXPIRE', stockKey, stockExpireSecs)
end

return {1, lastLockedRemain}
