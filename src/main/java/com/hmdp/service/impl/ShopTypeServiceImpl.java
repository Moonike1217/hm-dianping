package com.hmdp.service.impl;

import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private RedisTemplate<String, ShopType> redisTemplateForShopType;

    @Override
    public List<ShopType> queryTypeList() {
        //1.在redis中查询
        String key = RedisConstants.CACHE_SHOPTYPE_KEY;
        List<ShopType> shopTypeList = redisTemplateForShopType.opsForList().range(key, 0, -1);
        //2.如果查询到，则直接返回
        if (shopTypeList != null && !shopTypeList.isEmpty()) {
            return shopTypeList;
        }
        //3.如果未查询到，则在数据库中查询
        shopTypeList = query().list();

        //4.如果数据库中未查询到结果，则报错
        if (shopTypeList == null || shopTypeList.isEmpty()) {
            throw new RuntimeException();
        }
        //5.如果查询到
        //5.1将查询到的结果添加到Redis中
        for (ShopType shopType : shopTypeList) {
            redisTemplateForShopType.opsForList().rightPush(key, shopType);
        }

        //5.2返回查询结果
        return shopTypeList;
    }
}
