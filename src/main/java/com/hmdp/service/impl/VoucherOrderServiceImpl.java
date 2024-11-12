package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RabbitConstants;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.apache.ibatis.javassist.Loader;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;


@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    // test


    @Resource
    private SeckillVoucherServiceImpl seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RabbitTemplate rabbitTemplate;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    /**
     * 优惠券秒杀
     * @param voucherId
     * @return
     */
    @Override
    public Long seckill(Long voucherId) {
        Long userId = UserHolder.getUser().getId();

        // 1.执行Lua脚本
        Long result = stringRedisTemplate.execute(
                //参数1: 脚本
                SECKILL_SCRIPT,
                //参数2: key集合(已经在seckill脚本中写死，不需要传参)
                Collections.emptyList(),
                //参数3: 参数(优惠券id 和 用户id)
                voucherId.toString(), userId.toString()
        );
        // 2.判断结果
        int r = result.intValue();
        if (r != 0) {
            throw new RuntimeException(r == 1? "库存不足" : "同一用户不可重复下单");
        }
        // 3.将下单信息保存到阻塞队列
        long orderId = redisIdWorker.nextId("order");
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        rabbitTemplate.convertAndSend(RabbitConstants.SECKILL_ORDER_EXCHANGE, RabbitConstants.SECKILL_ORDER_ROUTING_KEY, voucherOrder);

        // 4.返回订单id
        return orderId;
    }

    @Transactional
    public synchronized long createVoucherOrder(Long voucherId) {
        //5-.一人一单业务逻辑
        //1.获取用户ID
        Long userId = UserHolder.getUser().getId();
        //2.判断该用户是否已经下过单(订单数据库中查询是否存在记录:用户id=... 优惠券id-...)
        Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            throw new RuntimeException("该用户已经购买过该优惠券");
        }

        //5.用户未下过单，扣减库存(利用MySQL行锁解决了超卖问题)
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                //大于零即可，通过MySQL的行锁完成
                .eq("voucher_id", voucherId).gt("stock", 0)
                .update();
        if (!success) {
            throw new RuntimeException("库存不足");
        }

        //扣减库存成功，创建订单
        //6.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //6.1订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        //6.2用户id(前面已经定义)
        //Long userId = UserHolder.getUser().getId();
        voucherOrder.setUserId(userId);
        //6.3优惠券id
        voucherOrder.setVoucherId(voucherId);

        //7.将订单保存到数据库
        save(voucherOrder);
        return orderId;
    }


//    @Override
//    public Long seckill(Long voucherId) {
//        //1.查询优惠券
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        if (voucher == null) {
//            throw new RuntimeException("优惠券不存在");
//        }
//
//        //2.判断秒杀是否开始
//        LocalDateTime beginTime = voucher.getBeginTime();
//        if (beginTime.isAfter(LocalDateTime.now())) {
//            throw new RuntimeException("秒杀尚未开始");
//        }
//
//        //3.判断秒杀是否结束
//        LocalDateTime endTime = voucher.getEndTime();
//        if (endTime.isBefore(LocalDateTime.now())) {
//            throw new RuntimeException("秒杀已经结束");
//        }
//
//        //4.判断库存是否充足
//        if (voucher.getStock() < 1) {
//            throw new RuntimeException("优惠券库存不足");
//        }
//
//        Long userId = UserHolder.getUser().getId();
//
//        // 创建锁对象
//        // SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        // 获取锁
//        boolean isLock = lock.tryLock();
//
//        // 判断锁是否获取成功
//        if (!isLock) {
//            //获取锁失败 则返回错误
//            throw new RuntimeException("不允许重复下单");
//        }
//
//        // 获取锁成功 执行业务逻辑
//        try {
//            // 获取代理对象
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            // 通过代理对象调用createVoucherOrder方法 确保事务生效
//            return proxy.createVoucherOrder(voucherId);
//        } finally {
//            //释放锁
//            lock.unlock();
//        }
//    }
}
