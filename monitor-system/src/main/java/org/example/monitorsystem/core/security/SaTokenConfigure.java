package org.example.monitorsystem.core.security;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SaTokenConfigure implements WebMvcConfigurer {

    // 注册 Sa-Token 拦截器
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册路由拦截器，校验规则为：校验是否登录
        registry.addInterceptor(new SaInterceptor(handle -> {
            // 如果是跨域的 OPTIONS 预检请求，直接全部放行！
            if (SaHolder.getRequest().getMethod().equals("OPTIONS")) {
                return;
            }

            // 正常的请求再去校验登录状态
            StpUtil.checkLogin();
        }))
        .addPathPatterns("/api/**")
        .excludePathPatterns(
                "/api/auth/login",  // 放行人类登录接口
                "/api/iot/**"       // 放行所有硬件设备上报数据的接口
        );
    }
}