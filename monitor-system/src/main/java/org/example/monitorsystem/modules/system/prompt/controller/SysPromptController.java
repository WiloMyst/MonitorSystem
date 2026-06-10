package org.example.monitorsystem.modules.system.prompt.controller;

import org.example.monitorsystem.core.web.Result;
import org.example.monitorsystem.modules.system.log.aop.annotation.Log;
import org.example.monitorsystem.modules.system.prompt.service.ISysPromptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 提示词管理控制器
 * 提供缓存刷新接口（前端管理用）和内部回源接口（Python AI 微服务调用）。
 */
@CrossOrigin
@RestController
@RequestMapping("/api/prompt")
public class SysPromptController {

    @Autowired
    private ISysPromptService sysPromptService;

    @Log(title = "提示词管理", businessType = "UPDATE")
    @PostMapping("/refreshCache")
    public Result<String> refreshCache(@RequestParam String promptCode) {
        sysPromptService.refreshPromptCache(promptCode);
        return Result.success("缓存清理成功，大模型将热加载最新配置！");
    }

    @GetMapping("/internal/get")
    public Result<String> getInternalPrompt(@RequestParam String promptCode) {
        String content = sysPromptService.getPromptContentByCode(promptCode);
        return Result.success(content);
    }
}