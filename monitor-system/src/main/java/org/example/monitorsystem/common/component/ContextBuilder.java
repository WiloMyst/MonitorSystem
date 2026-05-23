package org.example.monitorsystem.common.component;

import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

/**
 * RAG 动态上下文组装引擎
 */
@Component
public class ContextBuilder {

    /**
     * 动态组装大模型所需的全部系统级上下文
     */
    public Map<String, Object> build(String question, String ragContext) {
        Map<String, Object> promptParams = new HashMap<>();

        // 1. 注入用户当前问题
        promptParams.put("question", question);

        // 2. 注入 RAG 检索到的私有化知识库（做兜底处理）
        promptParams.put("context", (ragContext != null && !ragContext.isBlank()) ? ragContext : "暂无相关底层设备手册知识");

        // 3. 注入当前系统时间、用户角色、历史对话等
        // promptParams.put("currentTime", LocalDateTime.now().toString());

        return promptParams;
    }
}