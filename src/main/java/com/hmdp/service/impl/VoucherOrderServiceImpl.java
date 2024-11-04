package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.apache.ibatis.javassist.Loader;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;


@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    // test


    @Resource
    private SeckillVoucherServiceImpl seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 优惠券秒杀
     * @param voucherId
     * @return
     */
    @Override
    public Long seckill(Long voucherId) {
        //1.查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        if (voucher == null) {
            throw new RuntimeException("优惠券不存在");
        }

        //2.判断秒杀是否开始
        LocalDateTime beginTime = voucher.getBeginTime();
        if (beginTime.isAfter(LocalDateTime.now())) {
            throw new RuntimeException("秒杀尚未开始");
        }

        //3.判断秒杀是否结束
        LocalDateTime endTime = voucher.getEndTime();
        if (endTime.isBefore(LocalDateTime.now())) {
            throw new RuntimeException("秒杀已经结束");
        }

        //4.判断库存是否充足
        if (voucher.getStock() < 1) {
            throw new RuntimeException("优惠券库存不足");
        }

        Long userId = UserHolder.getUser().getId();

        // 创建锁对象
        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        // 获取锁
        boolean isLock = lock.tryLock(1200);

        // 判断锁是否获取成功
        if (!isLock) {
            //获取锁失败 则返回错误
            throw new RuntimeException("不允许重复下单");
        }

        // 获取锁成功 执行业务逻辑
        try {
            // 获取代理对象
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            // 通过代理对象调用createVoucherOrder方法 确保事务生效
            return proxy.createVoucherOrder(voucherId);
        } finally {
            //释放锁
            lock.unlock();
        }


    }

    @Transactional
    public synchronized long createVoucherOrder(Long voucherId) {
        //5-.一人一单业务逻辑
        //1.查询订单
        Long userId = UserHolder.getUser().getId();
        //2.判断订单是否存在
        Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            throw new RuntimeException("该用户已经购买过该优惠券");
        }

        //5.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                //CAS法实现乐观锁
                //.eq("voucher_id", voucherId).eq("stock", voucher.getStock())

                //大于零即可，通过MySQL的行锁完成
                .eq("voucher_id", voucherId).gt("stock", 0)
                .update();
        if (!success) {
            throw new RuntimeException("库存不足");
        }


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
}
