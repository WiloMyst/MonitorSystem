package org.example.monitorsystem.controller;

import org.example.monitorsystem.common.Result;
import org.example.monitorsystem.common.annotation.Log;
import org.example.monitorsystem.service.ISysPromptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@CrossOrigin
@RestController
@RequestMapping("/api/prompt")
public class SysPromptController {

    @Autowired
    private ISysPromptService sysPromptService;

    @Log(title = "提示词管理", businessType = "UPDATE")
    @PostMapping("/refreshCache")
    public Result<String> refreshCache(@RequestParam String promptCode) {
        // 调用你昨天写好的清空 Redis 缓存的方法
        sysPromptService.refreshPromptCache(promptCode);
        return Result.success("缓存清理成功，大模型将热加载最新配置！");
    }
}