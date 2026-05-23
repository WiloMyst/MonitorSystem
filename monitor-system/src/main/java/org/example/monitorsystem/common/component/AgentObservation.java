package org.example.monitorsystem.common.component;

/**
 * Agent 工具调用的标准观察结果 (Observation)
 * 作用：强制规范大模型接收到的数据格式，且剥离底层业务的复杂性
 */
public record AgentObservation(
        boolean success,      // 工具是否执行成功
        Object data,          // 成功的业务数据（失败时为 null）
        String errorReason,   // 失败的明确原因（成功时为 null）
        String actionAdvice   // 给大模型的“行为建议”，引导它自我纠错或回复用户
) {}