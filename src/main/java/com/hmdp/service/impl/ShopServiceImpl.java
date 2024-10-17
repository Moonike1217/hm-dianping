package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    //线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 根据店铺id查询店铺信息
     *
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {
        //缓存穿透
        //queryWithPassThrough(id);

        //互斥锁解决缓存击穿
        //Shop shop = queryWithMutex(id);

        //逻辑过期解决缓存击穿
        Shop shop = queryWithLogicalExpire(id);

        if (shop == null) {
            return Result.fail("店铺不存在");
        }

        return Result.ok(shop);

    }

    /**
     * 缓存穿透代码封装
     * @param id
     * @return
     */
    public Shop queryWithPassThrough(Long id) {
        //1.从redis中查询商铺缓存
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String shopJSON = stringRedisTemplate.opsForValue().get(key);

        //2.判断是否存在:如存在，则直接返回
        if (StrUtil.isNotBlank(shopJSON)) {
            //先将JSON字符串转换为对象
            //然后返回
            return JSONUtil.toBean(shopJSON, Shop.class);
        }

        //程序运行至此，shopJSON只有两种情况:1.为空字符串，2.为null
        if (shopJSON != null) {
            //shopJSON命中缓存，返回错误信息
            return null;
        }

        //3.如不存在，则从数据库中查询
        Shop shop = getById(id);

        //4.如数据库中不存在，则
        if (shop == null) {
            //4.1将空值缓存到Redis中
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            //4.2返回错误信息
            return null;
        }

        //5.如数据库中存在
        //5.1则将商铺信息先存到Redis中
        //5.1.1将商铺信息转换为JSON字符串
        String json = JSONUtil.toJsonStr(shop);
        //5.1.2放入Redis
        stringRedisTemplate.opsForValue().set(key, json, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        //5.2然后返回
        return shop;
    }

    /**
     * 互斥锁解决缓存击穿
     * @param id
     * @return
     */
    public Shop queryWithMutex(Long id) {
        //1.从redis中查询商铺缓存
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String shopJSON = stringRedisTemplate.opsForValue().get(key);

        //2.判断是否存在:如存在，则直接返回
        if (StrUtil.isNotBlank(shopJSON)) {
            //先将JSON字符串转换为对象
            //然后返回
            return JSONUtil.toBean(shopJSON, Shop.class);
        }

        //程序运行至此，shopJSON只有两种情况:1.为空字符串，2.为null
        if (shopJSON != null) {
            //shopJSON命中缓存，返回错误信息
            return null;
        }

        //3.如不存在，尝试获取分布式锁

        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        int retryCount = 0;
        int maxRetries = 5;

        while (retryCount < maxRetries) {
            boolean isLock = tryLock(lockKey);
            if (!isLock) {
                //如果没有获取到锁，休眠一段时间后重新尝试获取锁
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                retryCount++;
            } else {
                try {
                    //获取到锁，首先再次从Redis中查询商铺缓存
                    shopJSON = stringRedisTemplate.opsForValue().get(key);
                    if (StrUtil.isNotBlank(shopJSON)) {
                        //如果查询到信息，则先将JSON字符串转换为对象，然后直接返回
                        return JSONUtil.toBean(shopJSON, Shop.class);
                    } else {
                        //如果查询不到，则开始重建缓存

                        //从数据库中查询商铺信息
                        Shop shop = getById(id);
                        //手动模拟缓存重建延时
                        Thread.sleep(200);

                        //将数据库的查询结果缓存到Redis中
                        if (shop == null) {
                            //数据库中不存在，则将空值缓存到Redis中
                            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                            return null;
                        } else {
                            //数据库中存在，则将商铺信息存到Redis中
                            String json = JSONUtil.toJsonStr(shop);
                            stringRedisTemplate.opsForValue().set(key, json, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
                            return shop;
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

    public Shop queryWithLogicalExpire(Long id) {
        //1.从redis中查询商铺缓存
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String shopJSON = stringRedisTemplate.opsForValue().get(key);

        //2.判断缓存是否命中
        if (StrUtil.isBlank(shopJSON)) {
            //未命中直接返回(因为已经事先做了缓存预热 所以需要的key一定在Redis中)
            return null;
        }

        //3.缓存命中，判断缓存是否过期
        //3.1将商铺JSON信息反序列化
        RedisData redisData = JSONUtil.toBean(shopJSON, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();

        //4.缓存未过期，返回商铺信息
        if (LocalDateTime.now().isBefore(expireTime)) {
            return shop;
        } else {
            //5.缓存过期，尝试获取锁
            String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
            boolean isLock = tryLock(lockKey);
            if(isLock) {
                //7.获取到锁，首先进行DoubleCheck
                shopJSON = stringRedisTemplate.opsForValue().get(key);
                if (StrUtil.isNotBlank(shopJSON)) {
                    //查询到了数据 将json反序列化进行判断
                    redisData = JSONUtil.toBean(shopJSON, RedisData.class);
                    shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
                    expireTime = redisData.getExpireTime();
                    if (LocalDateTime.now().isBefore(expireTime)) {
                        return shop;
                    }
                }
                //8.DoubleCheck并未返回数据，所以要另开进程进行缓存重建
                CACHE_REBUILD_EXECUTOR.submit(() -> {
                    //重建缓存
                    try {
                        this.saveShop2Redis(id, 20L);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    } finally {
                        //释放锁
                        unLock(lockKey);
                    }
                });
            }
            //返回过期信息
            return shop;
        }
    }

    /**
     * 将店铺信息保存到redis
     * @param id
     */
    public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
        //1.查询店铺信息
        Shop shop = getById(id);
        //模拟缓存重建的延时
        Thread.sleep(200);
        //2.封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));

        //3.将店铺信息转换为JSON字符串存储到Redis
        String json = JSONUtil.toJsonStr(redisData);
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, json);

        return;
    }

    @Override
    @Transactional
    public void update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            throw new RuntimeException("店铺id为空");
        }
        //1.更新数据库
        updateById(shop);
        //2.删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);
        //3.返回
        return;
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
}
