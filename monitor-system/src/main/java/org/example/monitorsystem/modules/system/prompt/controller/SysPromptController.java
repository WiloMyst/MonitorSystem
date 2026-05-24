package org.example.monitorsystem.modules.system.prompt.controller;

import org.example.monitorsystem.core.web.Result;
import org.example.monitorsystem.modules.system.log.aop.annotation.Log;
import org.example.monitorsystem.modules.system.prompt.service.ISysPromptService;
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

    // ================= 专供 Python 内部调用的回源接口 =================
    @GetMapping("/internal/get")
    public Result<String> getInternalPrompt(@RequestParam String promptCode) {
        // 直接调用 Service 获取内容（如果有 Redis 缓存它也会走 Java 的缓存，没有就查 MySQL）
        String content = sysPromptService.getPromptContentByCode(promptCode);
        return Result.success(content);
    }
}