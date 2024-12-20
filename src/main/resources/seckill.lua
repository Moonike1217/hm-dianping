---
--- Generated by EmmyLua(https://github.com/EmmyLua)
--- Created by mooni.
--- DateTime: 2024/11/9 18:16
--- 判断用户是否具有购买资格，只有当返回结果为0是用户才具有购买资格
---

-- 1 参数列表
-- 1.1 优惠券id
local voucherId = ARGV[1]
-- 1.2 用户id
local userId = ARGV[2]

-- 2 key列表
-- 2.1 库存key
local stockKey = "seckill:stock:" .. voucherId
-- 2.2 订单key
local orderKey = "seckill:order:" .. voucherId

-- 3 判断库存是否充足
if (tonumber(redis.call("get", stockKey)) <= 0) then
    -- 3.1 库存不足
    return 1
end

-- 4 判断用户是否已经购买过
if (redis.call("sismember", orderKey, userId) == 1) then
    -- 4.1 用户已经购买过
    return 2
end

-- 5 库存充足，用户未下单，执行操作：扣减库存，将用户添加到set中
-- 5.1 扣减库存
redis.call("incrby", stockKey, -1)
-- 5.2 将用户添加到set中
redis.call("sadd", orderKey, userId)
-- 5.3 逻辑执行完成 返回0
return 0