package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
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
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 根据店铺id查询店铺信息
     *
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {
        //1.从redis中查询商铺缓存
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String shopJSON = stringRedisTemplate.opsForValue().get(key);

        //2.判断是否存在:如存在，则直接返回
        if (StrUtil.isNotBlank(shopJSON)) {
            //先将JSON字符串转换为对象
            Shop shop = JSONUtil.toBean(shopJSON, Shop.class);
            //然后返回
            return Result.ok(shop);
        }

        //程序运行至此，shopJSON只有两种情况:1.为空字符串，2.为null
        if (shopJSON != null) {
            //shopJSON命中缓存，返回错误信息
            return Result.fail("店铺信息不存在(Redis拦截)");
        }

        //3.如不存在，则从数据库中查询
        Shop shop = getById(id);

        //4.如数据库中不存在，则
        if (shop == null) {
            //4.1将空值缓存到Redis中
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            //4.2返回错误信息
            return Result.fail("店铺信息不存在(数据库)");
        }

        //5.如数据库中存在
        //5.1则将商铺信息先存到Redis中
        //5.1.1将商铺信息转换为JSON字符串
        String json = JSONUtil.toJsonStr(shop);
        //5.1.2放入Redis
        stringRedisTemplate.opsForValue().set(key, json, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        //5.2然后返回
        return Result.ok(shop);
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
}
