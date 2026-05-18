package org.example.monitorsystem.controller;

import org.example.monitorsystem.common.Result;
import org.example.monitorsystem.common.annotation.Log;
import org.example.monitorsystem.dto.LoginDTO;
import org.example.monitorsystem.service.ISysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 认证授权控制器
 * 企业级特性：参数自动校验、全局异常捕获映射
 */
@CrossOrigin
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private ISysUserService sysUserService;

    /**
     * 用户登录接口
     * @param loginDTO 登录信息 (加了 @Validated 开启参数校验)
     * @return 颁发的 Token 护照
     */
    @Log(title = "认证授权", businessType = "LOGIN")
    @PostMapping("/login")
    public Result<String> login(@Validated @RequestBody LoginDTO loginDTO) {
        // 核心逻辑下推到 Service，Controller 只负责接收与返回
        String token = sysUserService.doLogin(loginDTO);
        return Result.success(token);
    }
}