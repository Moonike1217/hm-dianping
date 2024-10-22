package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Component
@Slf4j
public class CacheClient {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 将数据存入Redis中(设置过期时间)
     * @param key
     * @param value
     * @param time
     * @param unit
     */
    public void set(String key, Object value, Long time, TimeUnit unit)  {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    /**
     * 将数据存入Redis中(设置逻辑过期时间)
     * @param key
     * @param value
     * @param time
     * @param unit
     */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 缓存空值解决缓存穿透
     * @param keyPrefix
     * @param id
     * @param type
     * @param dbFallback
     * @param time
     * @param unit
     * @return
     * @param <R>
     * @param <ID>
     */
    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        //1.从Redis中查询缓存
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);

        //2.判断是否存在:如存在，则直接返回
        if (StrUtil.isNotBlank(json)) {
            //先将JSON字符串转换为对象
            //然后返回
            return JSONUtil.toBean(json, type);
        }

        //程序运行至此，json只有两种情况:1.为空字符串，2.为null
        if (json != null) {
            //json命中缓存，返回错误信息
            return null;
        }

        //3.如不存在，则从数据库中查询
        R r = dbFallback.apply(id);

        //4.如数据库中不存在，则
        if (r == null) {
            //4.1将空值缓存到Redis中
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            //4.2返回错误信息
            return null;
        }

        //5.如数据库中存在
        //5.1则将信息先存到Redis中
        //5.1.1将信息转换为JSON字符串
        json = JSONUtil.toJsonStr(r);
        //5.1.2放入Redis
        this.set(key, r, time, unit);

        //5.2然后返回
        return r;
    }

    /**
     * 尝试获取锁
     * @param key
     * @return
     */
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放锁
     * @param key
     */
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }

    /**
     * 互斥锁缓存查询(解决缓存击穿,测试时需要先将热点key加载到Redis中)
     * @param keyPrefix
     * @param lockKeyPrefix
     * @param id
     * @param type
     * @param dbFallback
     * @param time
     * @param unit
     * @return
     * @param <R>
     * @param <ID>
     */
    public <R, ID> R queryWithMutex(String keyPrefix, String lockKeyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {

        int retryCount = 0;
        int maxRetries = 5;
        String key = null;
        String json = null;

        while (retryCount < maxRetries) {

            //1.从redis中查询缓存
            key = keyPrefix + id;
            json = stringRedisTemplate.opsForValue().get(key);

            //2.判断缓存是否命中
            if (StrUtil.isNotBlank(json)) {
                //命中普通缓存
                //先将JSON字符串转换为对象
                //然后返回
                return JSONUtil.toBean(json, type);
            } else if (json != null) {
                //命中空缓存，返回null
                return null;
            }

            //3.未命中空缓存和正常缓存，尝试获取分布式锁
            String lockKey = lockKeyPrefix + id;
            boolean isLock = tryLock(lockKey);
            if (!isLock) {
                //如果没有获取到锁，休眠一段时间后重新从缓存中查询
                try {
                    log.info("no get lock");
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                retryCount++;
                continue;
            } else {
                try {
                    //获取到锁，首先再次从Redis中查询缓存
                    json = stringRedisTemplate.opsForValue().get(key);
                    if (StrUtil.isNotBlank(json)) {
                        //如果查询到信息，则先将JSON字符串转换为对象，然后直接返回
                        return JSONUtil.toBean(json, type);
                    } else {
                        //如果查询不到，则开始重建缓存
                        //从数据库中查询信息
                        R r = dbFallback.apply(id);
                        //手动模拟缓存重建延时
                        Thread.sleep(200);

                        //将数据库的查询结果缓存到Redis中
                        if (r == null) {
                            //数据库中不存在，则将空值缓存到Redis中
                            stringRedisTemplate.opsForValue().set(key, "", time, unit);
                            return null;
                        } else {
                            //数据库中存在，则将信息存到Redis中
                            json = JSONUtil.toJsonStr(r);
                            stringRedisTemplate.opsForValue().set(key, json, time, unit);
                            return r;
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放互斥锁
                    unLock(lockKey);
                }
            }
        }
        // 如果超过最大重试次数仍未获取到锁，返回 null
        return null;
    }

    /**
     * 逻辑过期缓存查询(解决缓存穿透, 测试时需要先将热点key加载到Redis中)
     * @param keyPrefix
     * @param lockKeyPrefix
     * @param id
     * @param type
     * @param dbFallback
     * @param time
     * @param unit
     * @return
     * @param <R>
     * @param <ID>
     */
    public <R, ID> R queryWithLogicalExpire(String keyPrefix, String lockKeyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        //1.从redis中查询缓存
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);

        //2.判断缓存是否命中
        if (StrUtil.isBlank(json)) {
            //未命中直接返回(因为已经事先做了缓存预热 所以需要的key一定在Redis中)
            return null;
        }

        //3.缓存命中，判断缓存是否过期
        //3.1将JSON信息反序列化
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();

        //4.缓存未过期，返回商铺信息
        if (LocalDateTime.now().isBefore(expireTime)) {
            return r;
        } else {
            //5.缓存过期，尝试获取锁
            String lockKey = lockKeyPrefix + id;
            boolean isLock = tryLock(lockKey);
            if(isLock) {
                //7.获取到锁，首先进行DoubleCheck
                json = stringRedisTemplate.opsForValue().get(key);
                if (StrUtil.isNotBlank(json)) {
                    //查询到了数据 将json反序列化进行判断
                    redisData = JSONUtil.toBean(json, RedisData.class);
                    r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
                    expireTime = redisData.getExpireTime();
                    if (LocalDateTime.now().isBefore(expireTime)) {
                        return r;
                    }
                }
                //8.DoubleCheck并未返回数据，所以要另开进程进行缓存重建
                CACHE_REBUILD_EXECUTOR.submit(() -> {
                    //重建缓存
                    try {
                        //从数据库中查询信息
                        R r1 = dbFallback.apply(id);
                        //将数据写入Redis
                        setWithLogicalExpire(key, r1, time, unit);

                    } catch (Exception e) {
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    } finally {
                        //释放锁
                        unLock(lockKey);
                    }
                });
            }
            //缓存过期，没获取到锁，返回过期信息
            return r;
        }
    }

}
