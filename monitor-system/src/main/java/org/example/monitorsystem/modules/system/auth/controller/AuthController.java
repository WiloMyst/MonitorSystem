package org.example.monitorsystem.modules.system.auth.controller;

import org.example.monitorsystem.core.web.Result;
import org.example.monitorsystem.modules.system.log.aop.annotation.Log;
import org.example.monitorsystem.modules.system.auth.service.ISysUserService;
import org.example.monitorsystem.modules.system.auth.model.LoginDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 认证授权控制器
 * 提供用户登录接口，校验通过后颁发 Sa-Token。
 */
@CrossOrigin
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private ISysUserService sysUserService;

    @Log(title = "认证授权", businessType = "LOGIN")
    @PostMapping("/login")
    public Result<String> login(@Validated @RequestBody LoginDTO loginDTO) {
        String token = sysUserService.doLogin(loginDTO);
        return Result.success(token);
    }
}