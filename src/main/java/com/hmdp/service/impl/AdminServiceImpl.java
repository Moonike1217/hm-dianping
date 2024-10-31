package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Admin;
import com.hmdp.entity.User;
import com.hmdp.mapper.AdminMapper;
import com.hmdp.service.IAdminService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class AdminServiceImpl extends ServiceImpl<AdminMapper, Admin> implements IAdminService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 管理员登录
     * @param loginForm
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm) {
        //1.检查验证码是否与之前Redis中存放的验证码一致
        String phone = loginForm.getPhone();
        String code = loginForm.getCode();
        String cacheCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);
        if (cacheCode == null || !cacheCode.equals(code)) {
            //若不一致或验证码为空 则不予通过
            return Result.fail("验证码错误，请重试");
        }

        //2.根据手机号查询管理员
        Admin admin= query().eq("phone", phone).one();
        if (admin == null) {
            //管理员不存在 报错
            throw new RuntimeException("管理员不存在!");
        }

        //3.将Admin以Hash形式保存到Redis中 Key为token(字符串) Value为Hash类型的Admin
        //3.1生成UUID作为token
        String token = UUID.randomUUID().toString(true);
        //3.2将Admin转换为StringMap(方便后续使用putAll)
        Map<String, Object> map = BeanUtil.beanToMap(admin);
        Map<String, String> stringMap = new HashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            // 将 Object 转换为 String 存入新 map 中
            stringMap.put(entry.getKey(), String.valueOf(entry.getValue()));
        }

        //3.4将Admin以Hash形式存储到Redis中
        String tokenKey = RedisConstants.LOGIN_ADMIN_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, stringMap);
        //3.5设置token的有效期
        stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_ADMIN_TTL, TimeUnit.MINUTES);

        //4.返回token
        return Result.ok(token);
    }



    /**
     * 发送验证码
     * @param phone
     * @param session
     * @return
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.判断手机号格式是否正确

        if (RegexUtils.isPhoneInvalid(phone)) {
            //手机号格式错误
            return Result.fail("手机号格式不正确");
        }

        //2.生成验证码
        String code = RandomUtil.randomNumbers(6);

        //3.保存验证码到Redis(手机号加前缀为key 验证码为value 过期时间为2分钟) 便于后续验证
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone, code, RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);

        //4.发送验证码
        log.info("已向{}发送验证码: {}", phone, code);

        return Result.ok();
    }
}
