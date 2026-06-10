package org.example.monitorsystem.core.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.monitorsystem.core.web.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.Set;

/**
 * 内部接口安全拦截器
 * 保护 /api/internal/** 路径，防止外部未授权访问:
 *   - 优先校验 X-Internal-Secret 请求头密钥
 *   - 密钥未配置时降级为 IP 白名单 (本机 + 内网段)
 *   - 生产环境启动时检查密钥配置，未配置则输出安全警告
 */
@Component
public class InternalApiInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(InternalApiInterceptor.class);

    private final String internalSecret;
    private final ObjectMapper objectMapper;
    private final Environment environment;

    private static final Set<String> ALLOWED_NETWORKS = Set.of(
            "127.0.0.1",
            "0:0:0:0:0:0:0:1",
            "::1"
    );

    private static final Set<String> ALLOWED_PREFIXES = Set.of(
            "172.",
            "10.",
            "192.168."
    );

    public InternalApiInterceptor(
            @Value("${ai-service.internal-secret:}") String internalSecret,
            ObjectMapper objectMapper,
            Environment environment) {
        this.internalSecret = internalSecret;
        this.objectMapper = objectMapper;
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        boolean isProd = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (isProd && (internalSecret == null || internalSecret.isEmpty())) {
            log.error("======================================================================");
            log.error("[安全警告] 生产环境未配置 ai-service.internal-secret !");
            log.error("内部接口将仅依赖 IP 白名单，存在安全风险，请立即设置 AI_SERVICE_INTERNAL_SECRET 环境变量");
            log.error("======================================================================");
        }
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String clientSecret = request.getHeader("X-Internal-Secret");

        if (internalSecret != null && !internalSecret.isEmpty()) {
            if (clientSecret == null || !internalSecret.equals(clientSecret)) {
                log.warn("[内部接口] 非法访问 - 密钥校验失败, path={}, remoteAddr={}",
                        request.getRequestURI(), getClientIp(request));
                sendError(response, 403, "内部接口访问被拒绝");
                return false;
            }
        } else {
            String clientIp = getClientIp(request);
            if (!isAllowedIp(clientIp)) {
                log.warn("[内部接口] 非法访问 - IP不在白名单, path={}, remoteAddr={}",
                        request.getRequestURI(), clientIp);
                sendError(response, 403, "内部接口访问被拒绝");
                return false;
            }
        }

        return true;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private boolean isAllowedIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        if (ALLOWED_NETWORKS.contains(ip)) {
            return true;
        }
        for (String prefix : ALLOWED_PREFIXES) {
            if (ip.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private void sendError(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(status, message)));
    }
}
