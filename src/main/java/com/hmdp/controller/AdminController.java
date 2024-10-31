package com.hmdp.controller;

import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.service.IAdminService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

@RestController
@Slf4j
@RequestMapping("/admin")
@Api(tags = "管理端相关接口")
public class AdminController {

    @Resource
    private IAdminService adminService;

    /**
     * 发送验证码
     * @param phone
     * @param session
     * @return
     */
    @PostMapping("code")
    @ApiOperation("发送验证码")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        return adminService.sendCode(phone, session);
    }

    /**
     * 管理员登录
     * @param loginFormDTO
     * @return
     */
    @ApiOperation("管理员登录")
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginFormDTO) {
        log.info("管理员登录:{}", loginFormDTO);
        adminService.login(loginFormDTO);
        return Result.ok();
    }
}
