package com.hmdp.service;

import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;


public interface IVoucherOrderService extends IService<VoucherOrder> {

    /**
     * 优惠券秒杀
     * @param voucherId
     * @return
     */
    Long seckill(Long voucherId);

    /**
     * 创建订单
     * @param voucherId
     */
    long createVoucherOrder(Long voucherId);
}
