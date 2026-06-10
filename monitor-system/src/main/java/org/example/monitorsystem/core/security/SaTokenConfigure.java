package org.example.monitorsystem.core.security;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 安全配置
 * 注册两类拦截器:
 *   1. Sa-Token 登录拦截器: 拦截 /api/** 路径，排除登录、IoT、内部接口
 *   2. InternalApiInterceptor: 拦截 /api/internal/** 路径，校验内部服务密钥或 IP 白名单
 */
@Configuration
public class SaTokenConfigure implements WebMvcConfigurer {

    private final InternalApiInterceptor internalApiInterceptor;

    public SaTokenConfigure(InternalApiInterceptor internalApiInterceptor) {
        this.internalApiInterceptor = internalApiInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> {
            if (SaHolder.getRequest().getMethod().equals("OPTIONS")) {
                return;
            }
            StpUtil.checkLogin();
        }))
        .addPathPatterns("/api/**")
        .excludePathPatterns(
                "/api/auth/login",
                "/api/iot/**",
                "/api/prompt/internal/**",
                "/api/internal/**"
        );

        registry.addInterceptor(internalApiInterceptor)
                .addPathPatterns("/api/internal/**")
                .order(0);
    }
}
