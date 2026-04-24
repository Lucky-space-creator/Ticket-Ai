-- ============================================================
--  功能: 将MySQL库存初始化/同步到Redis
--  参数:
--    KEYS[1]   库存Key
--    ARGV[1]   初始库存量
--    ARGV[2]   过期时间（秒）
--  返回:
--    {-1, 当前值}  Key已存在
--    {1, 设定值}    初始化成功
-- ============================================================

local stockKey = KEYS[1]
local initValue = tonumber(ARGV[1])
local expireSecs = tonumber(ARGV[2])

local exists = redis.call('EXISTS', stockKey)
if exists == 1 then
    local current = tonumber(redis.call('GET', stockKey))
    return {-1, current}
end

redis.call('SET', stockKey, initValue)
redis.call('EXPIRE', stockKey, expireSecs)

return {1, initValue}