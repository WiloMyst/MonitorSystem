package org.example.monitorsystem.modules.ai.controller;

import cn.dev33.satoken.stp.StpUtil;
import io.netty.channel.ChannelOption;
import org.example.monitorsystem.core.security.AiCircuitBreaker;
import org.example.monitorsystem.core.security.AiRateLimiter;
import org.example.monitorsystem.modules.ai.service.IRagService;
import org.example.monitorsystem.modules.system.log.aop.annotation.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * AI 聊天 BFF 控制器
 * 作为前端与 AI 微服务之间的聚合层，职责:
 *   1. 限流: AiRateLimiter 按用户维度限流
 *   2. 熔断: AiCircuitBreaker 保护 AI 微服务调用
 *   3. 降级: AI 微服务不可用时降级到本地 Spring AI RAG
 *   4. 安全: 校验 conversation_id 用户归属，防止越权
 *   5. 链路追踪: 注入 traceId 便于全链路排查
 *
 * 线程模型与防阻塞说明:
 *   本控制器运行在 Servlet 容器 (Tomcat) 中，但 SSE 流式转发全链路基于 Reactor 响应式流:
 *   - 返回类型为 Flux<ServerSentEvent<String>>，Spring MVC 在 Servlet 环境下
 *     会自动通过 ResponseBodyEmitter 适配，将 Reactor 信号异步写入 HTTP 响应
 *   - WebClient 调用 AI 微服务时使用 bodyToFlux()，数据在 Reactor Netty 的
 *     EventLoop 线程中接收，通过 Reactor 背压机制逐条推送到 Tomcat 的 I/O 线程
 *   - 全链路绝对没有使用 .block()，保证 Tomcat 工作线程在发送 SSE 请求后立即释放，
 *     不会因等待 AI 微服务响应而阻塞，从而实现单线程处理多个并发 SSE 连接
 *   - 限流/熔断判断在 Flux 构建前完成（同步判断），不涉及阻塞等待
 */
@CrossOrigin
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private static final Logger log = LoggerFactory.getLogger(AiController.class);

    private final WebClient webClient;
    private final String internalSecret;
    private final AiRateLimiter rateLimiter;
    private final AiCircuitBreaker circuitBreaker;
    private final IRagService ragService;

    public AiController(
            @Value("${ai-service.url:http://127.0.0.1:8000}") String aiServiceUrl,
            @Value("${ai-service.internal-secret:}") String internalSecret,
            AiRateLimiter rateLimiter,
            AiCircuitBreaker circuitBreaker,
            IRagService ragService) {
        this.internalSecret = internalSecret;
        this.rateLimiter = rateLimiter;
        this.circuitBreaker = circuitBreaker;
        this.ragService = ragService;
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
                .responseTimeout(Duration.ofSeconds(120));

        this.webClient = WebClient.builder()
                .baseUrl(aiServiceUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    @Log(title = "AI智能排障(流式)", businessType = "CHAT")
    @PostMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> askAi(
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = "satoken", required = false) String satoken) {

        String question = (String) payload.get("question");
        if (question == null || question.toString().trim().isEmpty()) {
            return Flux.just(ServerSentEvent.<String>builder().event("error").data("{\"message\":\"问题不能为空\"}").build());
        }

        String clientId = resolveClientId(satoken);
        if (!rateLimiter.tryAcquire(clientId)) {
            log.warn("[BFF] AI请求被限流: clientId={}", clientId);
            return Flux.just(ServerSentEvent.<String>builder()
                    .event("error").data("{\"message\":\"请求过于频繁，请稍后再试\"}").build());
        }

        if (!circuitBreaker.allowRequest()) {
            log.warn("[BFF] AI请求被熔断，降级到本地Spring AI: 熔断器状态={}", circuitBreaker.getState());
            return fallbackToLocalRag(question);
        }

        String userId = resolveUserId(satoken);
        if (payload.containsKey("conversation_id") && payload.get("conversation_id") != null) {
            String convId = payload.get("conversation_id").toString();
            if (!convId.isEmpty() && !convId.startsWith("conv-" + userId + "-")) {
                log.warn("[BFF] conversation_id 用户归属校验失败: userId={}, convId={}", userId, convId);
                payload.remove("conversation_id");
            }
        }
        payload.put("user_id", userId);

        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        log.info("[BFF-{}] AI请求透传: question={}", traceId, question.substring(0, Math.min(question.length(), 50)));

        return webClient.post()
                .uri("/api/ai/ask")
                .header("Content-Type", "application/json")
                .header("satoken", satoken != null ? satoken : "")
                .header("X-Internal-Secret", internalSecret)
                .header("X-Trace-Id", traceId)
                .bodyValue(payload)
                .retrieve()
                .bodyToFlux(ServerSentEvent.class)
                .map(sse -> {
                    String event = sse.event() != null ? sse.event() : "message";
                    String data = sse.data() != null ? sse.data().toString() : "";
                    return ServerSentEvent.<String>builder()
                            .event(event)
                            .data(data)
                            .build();
                })
                .doOnComplete(() -> circuitBreaker.recordSuccess())
                .doOnError(e -> circuitBreaker.recordFailure())
                .onErrorResume(e -> {
                    log.error("[BFF-{}] AI微服务调用失败，降级到本地Spring AI: {}", traceId, e.getMessage());
                    circuitBreaker.recordFailure();
                    return fallbackToLocalRag(question);
                });
    }

    private Flux<ServerSentEvent<String>> fallbackToLocalRag(String question) {
        return ragService.smartChat(question)
                .map(chunk -> ServerSentEvent.<String>builder()
                        .event("message")
                        .data("{\"content\":" + escapeJson(chunk) + "}")
                        .build())
                .concatWith(Flux.just(
                        ServerSentEvent.<String>builder()
                                .event("fallback_notice")
                                .data("{\"message\":\"当前为降级模式，AI能力受限，完整服务恢复后将自动切换\"}")
                                .build()
                ))
                .onErrorResume(e -> Flux.just(
                        ServerSentEvent.<String>builder()
                                .event("error")
                                .data("{\"message\":\"AI 服务暂时不可用，请稍后重试\"}")
                                .build()
                ));
    }

    private String resolveClientId(String satoken) {
        if (satoken != null && !satoken.isEmpty()) {
            try {
                Object loginId = StpUtil.getLoginIdByToken(satoken);
                if (loginId != null) {
                    return "user:" + loginId;
                }
            } catch (Exception ignored) {
            }
        }
        return "anonymous:" + (satoken != null ? satoken.hashCode() : "unknown");
    }

    private String resolveUserId(String satoken) {
        if (satoken != null && !satoken.isEmpty()) {
            try {
                Object loginId = StpUtil.getLoginIdByToken(satoken);
                if (loginId != null) {
                    return loginId.toString();
                }
            } catch (Exception ignored) {
            }
        }
        return "anon";
    }

    private String escapeJson(String text) {
        if (text == null) return "null";
        return "\"" + text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                + "\"";
    }
}
