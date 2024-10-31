package com.hmdp.config;

import io.swagger.annotations.Api;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;

/**
 * swagger2 文档生成
 * 路径:ip:port/swagger-ui.html
 */
@Configuration
public class Swagger2Configuration {

    //        版本号
    private static final String VERSION="0.1.0";

    /**
     * 接口
     * @return
     */
    @Bean
    public Docket userApi(){
        return new Docket(DocumentationType.SWAGGER_2)
                .apiInfo(userApiInfo())//调用apiInfo方法，创建一个ApiInfo实例，里面是展示在文档页面信息内容
                .select()//返回一个ApiSelectorBuilder实例用来控制哪些接口暴露给Swagger来展现
                .apis(RequestHandlerSelectors.withClassAnnotation(Api.class))//扫描的包路径
                .paths(PathSelectors.any())//路径判断
                .build();//创建
    }
    private ApiInfo userApiInfo(){
        return new ApiInfoBuilder()//创建ApiInfoBuilder实例
                .title("project-name")//标题
                .description("接口文档")//描述
                .version(VERSION)//版本号
                .build();//创建
    }
}