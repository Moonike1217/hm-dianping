package com.hmdp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.entity.ShopType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    // RedisTemplate for String type
    @Bean
    public RedisTemplate<String, String> redisTemplateForString(RedisConnectionFactory factory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // Key uses String serialization
        template.setKeySerializer(new StringRedisSerializer());
        // Value uses String serialization
        template.setValueSerializer(new StringRedisSerializer());

        template.afterPropertiesSet();
        return template;
    }

    // RedisTemplate for ShopType type
    @Bean
    public RedisTemplate<String, ShopType> redisTemplateForShopType(RedisConnectionFactory factory) {
        RedisTemplate<String, ShopType> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // Key uses String serialization
        template.setKeySerializer(new StringRedisSerializer());
        // Value uses Jackson serialization for ShopType objects
        Jackson2JsonRedisSerializer<ShopType> serializer = new Jackson2JsonRedisSerializer<>(ShopType.class);
        template.setValueSerializer(serializer);

        template.afterPropertiesSet();
        return template;
    }
}
