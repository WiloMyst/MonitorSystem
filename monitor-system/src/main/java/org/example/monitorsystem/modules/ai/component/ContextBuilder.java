package org.example.monitorsystem.modules.ai.component;

import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG 动态上下文组装引擎（带Token/长度溢出安全防御机制）
 */
@Component
public class ContextBuilder {

    // 设定的安全字符预算上限（对应大模型的上下文窗口，此处以字符数作为安全工程代理）
    private static final int MAX_TOTAL_CHAR_LIMIT = 8000;
    // 为核心系统基础提示词（System Prompt）预留的保底字符数
    private static final int SYSTEM_PROMPT_RESERVE = 1500;

    /**
     * 动态组装大模型所需的全部系统级上下文，并进行严格的长度截断防御
     */
    public Map<String, Object> build(String question, List<String> sortedRagChunks) {
        Map<String, Object> promptParams = new HashMap<>();

        // =====================================================================
        // 1. 用户当前问题绝对不截断
        // =====================================================================
        String safeQuestion = question != null ? question : "";
        // 极端防爆破：如果用户恶意输入了超长文本，硬截断保护前 1000 字
        if (safeQuestion.length() > 1000) {
            safeQuestion = safeQuestion.substring(0, 1000);
        }
        promptParams.put("question", safeQuestion);

        // =====================================================================
        // 2. 动态计算分配给 RAG 上下文的“剩余字数预算”
        // 算法公式：可用预算 = 总窗口上限 - 系统预留额度 - 用户问题长度
        // =====================================================================
        int remainingBudget = MAX_TOTAL_CHAR_LIMIT - SYSTEM_PROMPT_RESERVE - safeQuestion.length();

        // 确保极端情况下预算不为负数，至少留出 2000 字的基础空间
        if (remainingBudget < 2000) {
            remainingBudget = 2000;
        }

        // =====================================================================
        // 3. 基于 Chunk 的滑动窗口组装，保证语义完整性
        // =====================================================================
        StringBuilder safeContextBuilder = new StringBuilder();
        boolean isTruncated = false;

        if (sortedRagChunks != null && !sortedRagChunks.isEmpty()) {
            for (String chunk : sortedRagChunks) {
                // 如果当前 Chunk 放进去不会超标，就完整放进去
                if (safeContextBuilder.length() + chunk.length() <= remainingBudget) {
                    safeContextBuilder.append(chunk).append("\n\n");
                } else {
                    // 如果放不下了，直接丢弃这个 Chunk 以及后续所有相关度更低的 Chunk！
                    isTruncated = true;
                    break;
                }
            }
        }

        if (safeContextBuilder.isEmpty()) {
            safeContextBuilder.append("暂无相关底层设备手册知识");
        } else if (isTruncated) {
            safeContextBuilder.append("[...系统提示：因单次对话篇幅限制，部分次要手册内容已被整体安全丢弃...]");
        }

        promptParams.put("context", safeContextBuilder.toString().trim());

        // 注入当前时间等动态元数据
        // promptParams.put("currentTime", LocalDateTime.now().toString());

        return promptParams;
    }
}