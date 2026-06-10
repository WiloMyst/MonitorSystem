package org.example.monitorsystem.modules.ai.controller;

import cn.dev33.satoken.stp.StpUtil;
import org.example.monitorsystem.core.web.Result;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 内部认证控制器
 * 供 AI 微服务验证前端传递的 Sa-Token 有效性，实现服务间认证透传。
 */
@RestController
@RequestMapping("/api/internal/auth")
public class InternalAuthController {

    @GetMapping("/check")
    public Result<Map<String, Object>> checkToken(@RequestParam("token") String tokenValue) {
        if (tokenValue == null || tokenValue.trim().isEmpty()) {
            return Result.error(400, "token 不能为空");
        }

        Object loginId;
        try {
            loginId = StpUtil.getLoginIdByToken(tokenValue);
        } catch (Exception e) {
            Map<String, Object> data = new HashMap<>();
            data.put("valid", false);
            data.put("reason", "token无效或已过期");
            return Result.success(data);
        }

        if (loginId == null) {
            Map<String, Object> data = new HashMap<>();
            data.put("valid", false);
            data.put("reason", "token无效或已过期");
            return Result.success(data);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("valid", true);
        data.put("userId", loginId.toString());
        data.put("tokenTimeout", StpUtil.getTokenTimeout(tokenValue));
        return Result.success(data);
    }
}
