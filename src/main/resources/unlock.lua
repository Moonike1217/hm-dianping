--判断锁中的线程标识与当前线程标识是否一致
if(redis.call('get', KEYS[1]) == ARGV[1]) then
    --如果一致则释放锁
    redis.call("del", KEYS[1])
end
--如果不一致则什么也不做 返回0
return 0