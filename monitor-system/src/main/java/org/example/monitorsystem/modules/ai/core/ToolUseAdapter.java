package org.example.monitorsystem.modules.ai.core;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Set;
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
                // 执行真正的业务逻辑
                Resp result = businessLogic.apply(request);
                System.out.println(" [" + toolName + "] 业务逻辑执行成功");

                return new AgentObservation(true, result, null, "执行成功，请结合该数据组织最终回复给用户。");

            } catch (Exception e) {
                // 彻底阻断底层异常抛出到 AI 引擎，防止对话流崩溃
                System.err.println(" [" + toolName + "] 底层业务发生崩溃: " + e.getMessage());
                return new AgentObservation(false, null, "底层系统异常：" + e.getMessage(),
                        "工具调用遭遇系统级异常，请向用户致歉并告知系统暂时繁忙。");
            }
        };
    }
}