package org.example.monitorsystem.modules.ai.component;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

@Component
public class ToolUseAdapter {

    // 注入 Java 标准的校验器 (JSR-303)
    @Autowired
    private Validator validator;

    /**
     * 通用的 Tool-Use 适配层核心方法。
     *
     * @param toolName 工具名称（用于日志记录）
     * @param businessLogic 真正的底层业务逻辑
     * @return 包装后的、安全的 Function，供 Spring AI 调用
     */
    public <Req, Resp> Function<Req, AgentObservation> wrap(String toolName, Function<Req, Resp> businessLogic) {

        return request -> {
            System.out.println(" [Tool-Use 适配层] 拦截到大模型调用工具: " + toolName);

            // ==========================================
            // 1. 参数前置自动校验机制 (动态化)
            // ==========================================
            Set<ConstraintViolation<Req>> violations = validator.validate(request);
            if (!violations.isEmpty()) {
                // 如果校验不通过，自动提取第一条错误信息
                String errorMsg = violations.iterator().next().getMessage();
                System.err.println(" [" + toolName + "] 参数校验被拦截: " + errorMsg);

                // 返回标准错误 Observation，引导模型纠错
                return new AgentObservation(false, null, "参数校验失败：" + errorMsg,
                        "请检查你生成的参数，如果用户提供的信息不足以满足格式要求，请主动向用户追问缺失的信息。");
            }

            // ==========================================
            // 2. 异常拦截与安全隔离机制
            // ==========================================
            try {
                // 将同步的业务逻辑提交到异步线程池，并强制设置 5 秒主动超时阈值
                Resp result = CompletableFuture.supplyAsync(() -> businessLogic.apply(request))
                        .get(5, TimeUnit.SECONDS);

                System.out.println(" [" + toolName + "] 业务逻辑执行成功");
                return new AgentObservation(true, result, null, "执行成功，请结合该数据组织最终回复给用户。");

            } catch (TimeoutException e) {
                // 主动捕获超时异常，实现强行打断假死线程
                System.err.println(" [" + toolName + "] 底层 API 响应超时(3s)，触发主动熔断！");
                return new AgentObservation(false, null, "底层系统接口响应超时",
                        "工具调用超时。请向用户致歉，说明当前硬件网络繁忙，引导用户稍后再试。");

            } catch (ExecutionException e) {
                // 异步执行中底层业务抛出的真实异常（如 SQL 报错、业务逻辑报错）
                Throwable realCause = e.getCause() != null ? e.getCause() : e;
                System.err.println(" [" + toolName + "] 底层业务发生崩溃: " + realCause.getMessage());
                return new AgentObservation(false, null, "底层系统业务异常：" + realCause.getMessage(),
                        "工具调用遭遇业务级异常，请转告用户暂时无法处理该设备数据。");

            } catch (InterruptedException e) {
                // 线程被强行中断的兜底处理
                Thread.currentThread().interrupt();
                System.err.println(" [" + toolName + "] 工具调用线程被意外中断");
                return new AgentObservation(false, null, "系统线程中断",
                        "系统内部调度异常，请提示用户刷新页面。");
            }
        };
    }
}