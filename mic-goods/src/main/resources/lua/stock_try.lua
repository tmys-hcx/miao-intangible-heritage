-- TCC Try：Redis 预扣库存（原子校验 + 扣减可用、转入冻结，杜绝并发超卖）
-- KEYS[1] = 可用库存 key（goods:stock:avail:{goodsId}）
-- KEYS[2] = 冻结库存 key（goods:stock:frozen:{goodsId}）
-- ARGV[1] = 预扣数量
-- 返回：-1 可用库存 key 不存在（触发 DB 回源预热后重试）；-2 库存不足；>=0 预扣成功后的剩余可用额度
local availKey = KEYS[1]
local frozenKey = KEYS[2]
local num = tonumber(ARGV[1])

if redis.call('EXISTS', availKey) == 0 then
    return -1
end

local avail = tonumber(redis.call('GET', availKey))
if avail < num then
    return -2
end

redis.call('DECRBY', availKey, num)
redis.call('INCRBY', frozenKey, num)
return avail - num
