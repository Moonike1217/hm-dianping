package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
@Slf4j
class HmDianPingApplicationTests {
    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedisIdWorker redisIdWorker;

    private final ExecutorService es = Executors.newFixedThreadPool(500);

    @Test
    public void testSaveShop() throws InterruptedException {
        shopService.saveShop2Redis(1L, 30L);
//        log.info(stringRedisTemplate.opsForValue().get("cache:shop:1").toString());
//        Shop shop = shopService.queryWithLogicalExpire(1L);
//        log.info(shop.toString());
//
//        Thread.sleep(1000);
//        log.info(stringRedisTemplate.opsForValue().get("cache:shop:1").toString());
//        shop = shopService.queryWithLogicalExpire(1L);
//        log.info(shop.toString());
//        log.info(stringRedisTemplate.opsForValue().get("cache:shop:1").toString());

    }

    @Test
    void testIdWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);

        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                log.info("id:{}", id);
            }
            latch.countDown();
        };

        long begin = System.currentTimeMillis();

        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }

        latch.await();

        long end = System.currentTimeMillis();
        log.info("time:{}", (end - begin));

    }

}
