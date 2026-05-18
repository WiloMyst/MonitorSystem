package org.example.monitorsystem.common.aspect;

import cn.dev33.satoken.stp.StpUtil;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.example.monitorsystem.common.annotation.Log;
import org.example.monitorsystem.entity.SysOperLog;
import org.example.monitorsystem.service.ISysOperLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

@Aspect      // 告诉 Spring 这是一个切面类
@Component   // 交给 Spring 容器管理
public class LogAspect {

    @Autowired
    private ISysOperLogService sysOperLogService;

    // 环绕通知：拦截所有加了 @Log 注解的方法
    @Around("@annotation(org.example.monitorsystem.common.annotation.Log)")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        long beginTime = System.currentTimeMillis();
        Object result = null;
        Exception exception = null;

        try {
            // 执行真正的业务方法（比如 Controller 里的登录或设备查询逻辑）
            result = point.proceed();
        } catch (Exception e) {
            exception = e; // 如果业务报错了，把异常抓起来
            throw e;       // 然后继续往外抛，让全局异常处理器去接盘
        } finally {
            // 无论业务方法成功还是失败，都计算耗时并记录日志
            long costTime = System.currentTimeMillis() - beginTime;
            recordLog(point, exception, costTime);
        }
        return result;
    }

    /**
     * 核心逻辑：提取请求信息并【异步】入库
     */
    private void recordLog(ProceedingJoinPoint point, Exception exception, long costTime) {
        // 1. 获取 Request 对象
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();

        // 2. 获取方法上的 @Log 注解信息
        MethodSignature signature = (MethodSignature) point.getSignature();
        Log logAnnotation = signature.getMethod().getAnnotation(Log.class);

        // 3. 构建日志实体
        SysOperLog operLog = new SysOperLog();
        operLog.setTitle(logAnnotation.title());
        operLog.setBusinessType(logAnnotation.businessType());
        operLog.setMethod(signature.getDeclaringTypeName() + "." + signature.getName() + "()");
        operLog.setRequestMethod(request.getMethod());
        operLog.setOperUrl(request.getRequestURI());
        operLog.setOperIp(request.getRemoteAddr());
        operLog.setCostTime(costTime);
        operLog.setOperTime(LocalDateTime.now());

        // 4. 获取当前登录人 (结合 Sa-Token)
        try {
            if (StpUtil.isLogin()) {
                // 如果登录了，获取账号名（这里假设你在 token 里存了 username，或直接存 loginId）
                operLog.setOperName(StpUtil.getLoginIdAsString());
            } else {
                operLog.setOperName("未登录/匿名用户");
            }
        } catch (Exception e) {
            operLog.setOperName("获取异常");
        }

        // 5. 记录状态与异常信息
        if (exception != null) {
            operLog.setStatus(0); // 0 代表异常
            // 截取前 2000 个字符，防止数据库字段超长撑爆
            String errorMsg = exception.getMessage();
            operLog.setErrorMsg(errorMsg != null && errorMsg.length() > 2000 ? errorMsg.substring(0, 2000) : errorMsg);
        } else {
            operLog.setStatus(1); // 1 代表成功
        }

        String operName;
        try {
            operName = StpUtil.isLogin() ? StpUtil.getLoginIdAsString() : "未登录/匿名";
        } catch (Exception e) {
            operName = "获取异常";
        }

        // 6. 【企业级精髓】：异步落库，绝对不能让写日志拖慢业务接口！
        String finalOperName = operName;
        CompletableFuture.runAsync(() -> {
            // 在异步线程中直接使用提前拿到的 operName
            operLog.setOperName(finalOperName);
            sysOperLogService.save(operLog);
            System.out.println("📝 [审计日志] 异步记录成功：" + operLog.getTitle() + " - 耗时: " + costTime + "ms");
        });
    }
}