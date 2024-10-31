package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.Admin;
import com.hmdp.entity.User;

import javax.servlet.http.HttpSession;

public interface IAdminService extends IService<Admin> {

    /**
     * 用户登录
     * @param loginForm
     * @return
     */
    Result login(LoginFormDTO loginForm);


    /**
     * 发送验证码
     * @param phone
     * @param session
     * @return
     */
    Result sendCode(String phone, HttpSession session);
}
