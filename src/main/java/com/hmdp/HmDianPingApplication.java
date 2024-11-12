package com.hmdp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

@MapperScan("com.hmdp.mapper")
@SpringBootApplication
@EnableSwagger2

// 暴露代理对象
@EnableAspectJAutoProxy(exposeProxy = true)

// 开启RabbitMQ消息队列监听功能
@EnableRabbit

public class HmDianPingApplication {

    public static void main(String[] args) {
        SpringApplication.run(HmDianPingApplication.class, args);
    }

}
