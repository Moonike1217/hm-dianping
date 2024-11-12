package com.hmdp.utils;

import cn.hutool.json.JSON;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
@Slf4j
@RabbitListener(queues = RabbitConstants.SECKILL_ORDER_QUEUE)
public class AsyncRabbitListener {
    @Resource
    private IVoucherOrderService voucherOrderService;
    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @RabbitHandler
    public void AsyncSave(VoucherOrder voucherOrder)
    {
        log.info("接收到存储订单信息的消息:{}", JSONUtil.toJsonStr(voucherOrder));
        boolean success = seckillVoucherService.update().setSql("stock=stock-1").eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0).update();
        voucherOrderService.save(voucherOrder);
        log.info("订单信息存储完成:{}",success);
    }
}
