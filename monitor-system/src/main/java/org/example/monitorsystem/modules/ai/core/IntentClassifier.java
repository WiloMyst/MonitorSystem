package org.example.monitorsystem.modules.ai.core;

import jakarta.annotation.PostConstruct;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 独立的 AI 意图分类引擎
 */
@Component
public class IntentClassifier {

    @Autowired
    private EmbeddingModel embeddingModel;

    // 用 Map 替代硬编码的单一变量，注册表模式
    private final Map<IntentType, List<Double>> intentRegistry = new EnumMap<>(IntentType.class);

    private static final double INTENT_THRESHOLD = 0.75;

    // 用来打包“意图”和“具体分数”的返回对象
    public record IntentMatch(IntentType type, double score) {}

    @PostConstruct
    public void init() {
        System.out.println("========== [AI组件] 初始化意图基准向量 ==========");
        intentRegistry.put(IntentType.STATUS_QUERY, embeddingModel.embed("查询设备的实时运行状态、温度、指标、当前情况是否正常"));
        intentRegistry.put(IntentType.FAULT_RAG, embeddingModel.embed("设备出现故障、报错、脱机、异常、需要维修方案、怎么修、排障指导"));
        // 假设未来加了新功能：
        // intentRegistry.put(IntentType.REPORT_EXPORT, embeddingModel.embed("导出报表、生成PDF、下载数据"));
    }

    /**
     * 对外暴露的多标签分类方法：返回所有超过阈值的意图
     */
    public List<IntentMatch> classify(String question) {
        List<Double> questionVector = embeddingModel.embed(question);
        List<IntentMatch> matches = new ArrayList<>();

        // 动态遍历注册表里的所有意图
        for (Map.Entry<IntentType, List<Double>> entry : intentRegistry.entrySet()) {
            double sim = cosineSimilarity(questionVector, entry.getValue());
            System.out.printf(" [意图分类引擎] 校验 [%s] 相似度: %.4f%n", entry.getKey(), sim);

            // 只要超过阈值，统统加进备选列表（解决混合意图的核心！）
            if (sim > INTENT_THRESHOLD) {
                matches.add(new IntentMatch(entry.getKey(), sim));
            }
        }

        // 按照相似度分数从高到低降序排列
        matches.sort((a, b) -> Double.compare(b.score(), a.score()));

        return matches;
    }

    /**
     * 余弦相似度计算
     */
    private double cosineSimilarity(List<Double> vectorA, List<Double> vectorB) {
        if (vectorA == null || vectorB == null || vectorA.size() != vectorB.size()) {
            throw new IllegalArgumentException("向量为空或维度不一致，无法计算相似度");
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vectorA.size(); i++) {
            double a = vectorA.get(i);
            double b = vectorB.get(i);
            dotProduct += a * b;
            normA += Math.pow(a, 2);
            normB += Math.pow(b, 2);
        }

        if (normA == 0 || normB == 0) return 0.0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}