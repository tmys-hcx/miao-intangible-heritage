-- TCC 二阶段：释放冻结库存（Confirm 落库 / Cancel 还原共用，原子操作防止冻结数瞬时为负）
-- KEYS[1] = 可用库存 key（goods:stock:avail:{goodsId}）
-- KEYS[2] = 冻结库存 key（goods:stock:frozen:{goodsId}）
-- ARGV[1] = 释放数量
-- ARGV[2] = 是否回补可用库存：1 回补（Cancel：库存重新可售）；0 不回补（Confirm：已随 DB 落扣）
-- 返回：释放后的冻结库存值
local frozenKey = KEYS[2]
local num = tonumber(ARGV[1])

local frozen = redis.call('DECRBY', frozenKey, num)
if tonumber(ARGV[2]) == 1 then
    redis.call('INCRBY', KEYS[1], num)
end
if frozen < 0 then
    redis.call('SET', frozenKey, 0)
end
return frozen
