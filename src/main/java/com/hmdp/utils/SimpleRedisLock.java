package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{

    // 不同的业务有不同的锁 锁的名称不能够写死
    private String name;
    private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    // 锁前缀
    private static final String KEY_PREFIX = "lock:";

    // key
    String key = KEY_PREFIX + name;

    @Override
    public boolean tryLock(long timeoutSec) {
        /*
            获取锁实际上就是set指令加上nx和ex参数
         */

        // value应当与线程有关(分布式锁) 所以获取线程标识(转换为字符串类型)
        String value = String.valueOf(Thread.currentThread().getId());

        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, value, timeoutSec, TimeUnit.SECONDS);

        // 防止空指针的问题 如果success为null也会返回false
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
        /*
            释放锁其实就是del key
         */
        stringRedisTemplate.delete(key);
    }
}
