package com.hmdp.interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RefreshTokenInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1.获取请求头中的token
        String token = request.getHeader("authorization");

        //2.token判空
        if (StrUtil.isBlank(token)) {
            return true;
        }

        //3.根据token拿到用户信息(Hash)
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        Map<Object, Object> user = stringRedisTemplate.opsForHash().entries(tokenKey);

        //4.用户判空
        if (user.isEmpty()) {
            return true;
        }

        //5.将map转换为UserDTO
        UserDTO userDTO = new UserDTO();
        BeanUtil.fillBeanWithMap(user, userDTO, false);

        //6.将DTO存入ThreadLocal中
        UserHolder.saveUser(userDTO);

        //7.刷新token的有效期
        stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        return true;
    }
}
