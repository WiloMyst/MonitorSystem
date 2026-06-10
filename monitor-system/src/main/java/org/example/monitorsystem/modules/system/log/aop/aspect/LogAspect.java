package org.example.monitorsystem.modules.system.log.aop.aspect;

import cn.dev33.satoken.stp.StpUtil;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.example.monitorsystem.modules.system.log.aop.annotation.Log;
import org.example.monitorsystem.modules.system.log.entity.SysOperLog;
import org.example.monitorsystem.modules.system.log.service.ISysOperLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

/**
 * 操作日志切面
 * 拦截所有标注 @Log 注解的方法，自动记录操作人、请求路径、耗时和异常信息，
 * 并通过 CompletableFuture 异步落库，避免拖慢业务接口。
 */
@Aspect
@Component
public class LogAspect {

    @Autowired
    private ISysOperLogService sysOperLogService;

    @Around("@annotation(org.example.monitorsystem.modules.system.log.aop.annotation.Log)")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        long beginTime = System.currentTimeMillis();
        Object result = null;
        Exception exception = null;

        try {
            result = point.proceed();
        } catch (Exception e) {
            exception = e;
            throw e;
        } finally {
            long costTime = System.currentTimeMillis() - beginTime;
            recordLog(point, exception, costTime);
        }
        return result;
    }

    private void recordLog(ProceedingJoinPoint point, Exception exception, long costTime) {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();

        MethodSignature signature = (MethodSignature) point.getSignature();
        Log logAnnotation = signature.getMethod().getAnnotation(Log.class);

        SysOperLog operLog = new SysOperLog();
        operLog.setTitle(logAnnotation.title());
        operLog.setBusinessType(logAnnotation.businessType());
        operLog.setMethod(signature.getDeclaringTypeName() + "." + signature.getName() + "()");
        operLog.setRequestMethod(request.getMethod());
        operLog.setOperUrl(request.getRequestURI());
        operLog.setOperIp(request.getRemoteAddr());
        operLog.setCostTime(costTime);
        operLog.setOperTime(LocalDateTime.now());

        try {
            if (StpUtil.isLogin()) {
                operLog.setOperName(StpUtil.getLoginIdAsString());
            } else {
                operLog.setOperName("未登录/匿名用户");
            }
        } catch (Exception e) {
            operLog.setOperName("获取异常");
        }

        if (exception != null) {
            operLog.setStatus(0);
            String errorMsg = exception.getMessage();
            operLog.setErrorMsg(errorMsg != null && errorMsg.length() > 2000 ? errorMsg.substring(0, 2000) : errorMsg);
        } else {
            operLog.setStatus(1);
        }

        String operName;
        try {
            operName = StpUtil.isLogin() ? StpUtil.getLoginIdAsString() : "未登录/匿名";
        } catch (Exception e) {
            operName = "获取异常";
        }

        String finalOperName = operName;
        CompletableFuture.runAsync(() -> {
            operLog.setOperName(finalOperName);
            sysOperLogService.save(operLog);
        });
    }
}