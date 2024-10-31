package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IVoucherOrderService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/voucher-order")
@Api(tags = "优惠券订单相关接口")
public class VoucherOrderController {

    @Resource
    private IVoucherOrderService voucherOrderService;


    @PostMapping("seckill/{id}")
    @ApiOperation("优惠券秒杀")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        Long orderId = voucherOrderService.seckill(voucherId);
        return Result.ok(orderId);
    }
}
