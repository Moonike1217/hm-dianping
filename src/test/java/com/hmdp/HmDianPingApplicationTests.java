package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;

@SpringBootTest
@Slf4j
class HmDianPingApplicationTests {
    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Test
    public void testSaveShop() throws InterruptedException {
//        shopService.saveShop2Redis(1L, 10L);
        log.info(stringRedisTemplate.opsForValue().get("cache:shop:1").toString());
        Shop shop = shopService.queryWithLogicalExpire(1L);
        log.info(shop.toString());

        Thread.sleep(1000);
        log.info(stringRedisTemplate.opsForValue().get("cache:shop:1").toString());
        shop = shopService.queryWithLogicalExpire(1L);
        log.info(shop.toString());
        log.info(stringRedisTemplate.opsForValue().get("cache:shop:1").toString());

    }

}
