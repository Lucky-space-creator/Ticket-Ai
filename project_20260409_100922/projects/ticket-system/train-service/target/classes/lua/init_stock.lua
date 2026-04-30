-- ============================================================
--  功能: 初始化库存（启动时或数据变更时调用）
--  参数:
--    KEYS[1]   库存Key
--    ARGV[1]   库存数量
--    ARGV[2]   是否强制覆盖（1=是，0=否）
--    ARGV[3]   过期时间（秒）
--  返回:
--    {-1, 0}   参数错误
--    {0, 0}    库存已存在且不强制覆盖
--    {1, 0}    初始化成功
-- ============================================================

local stockKey = KEYS[1]
local stockQty = tonumber(ARGV[1])
local forceFlag = tonumber(ARGV[2])
local expireSecs = tonumber(ARGV[3])

-- 参数校验
if stockKey == nil then
    return {-1, 0}
end
if stockQty == nil or stockQty < 0 then
    return {-1, 0}
end
if forceFlag == nil then
    return {-1, 0}
end

-- 检查是否已存在（如果存在且不强制覆盖则跳过）
local exists = redis.call('EXISTS', stockKey)
if exists == 1 and forceFlag == 0 then
    return {0, 0}
end

-- 设置库存值
redis.call('SET', stockKey, stockQty)

-- 设置过期时间
if expireSecs ~= nil and expireSecs > 0 then
    redis.call('EXPIRE', stockKey, expireSecs)
end

return {1, 0}