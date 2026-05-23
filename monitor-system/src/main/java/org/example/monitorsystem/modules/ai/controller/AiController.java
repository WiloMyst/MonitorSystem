package org.example.monitorsystem.modules.ai.controller;

import org.example.monitorsystem.modules.ai.service.IRagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import org.example.monitorsystem.modules.system.log.aop.annotation.Log;
import reactor.core.publisher.Flux;
import org.springframework.http.MediaType;

@CrossOrigin
@RestController
@RequestMapping("/api/ai")
public class AiController {

    @Autowired
    private IRagService ragService;

    @Log(title = "AI智能排障(流式)", businessType = "CHAT")
    // 增加 produces 属性，告诉前端我要持续推数据了
    @PostMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> askAi(@RequestBody Map<String, String> payload) {
        String question = payload.get("question");

        if (question == null || question.trim().isEmpty()) {
            return Flux.just("问题不能为空"); // 响应式返回错误
        }

        // 直接返回流，Spring WebFlux 会自动帮我们把 Flux 转换为 SSE 事件流
        return ragService.smartChat(question);
    }
}