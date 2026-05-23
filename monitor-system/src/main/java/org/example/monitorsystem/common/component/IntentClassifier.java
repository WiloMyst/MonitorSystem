package org.example.monitorsystem.common.component;

import jakarta.annotation.PostConstruct;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 独立的 AI 意图分类引擎
 */
@Component
public class IntentClassifier {

    @Autowired
    private EmbeddingModel embeddingModel;

    private List<Double> statusIntentVector;
    private List<Double> faultIntentVector;
    private static final double INTENT_THRESHOLD = 0.75;

    @PostConstruct
    public void init() {
        System.out.println("========== [AI组件] 初始化意图基准向量 ==========");
        this.statusIntentVector = embeddingModel.embed("查询设备的实时运行状态、温度、指标、当前情况是否正常");
        this.faultIntentVector = embeddingModel.embed("设备出现故障、报错、脱机、异常、需要维修方案、怎么修、排障指导");
    }

    /**
     * 对外暴露的分类方法
     */
    public IntentType classify(String question) {
        List<Double> questionVector = embeddingModel.embed(question);

        double statusSim = cosineSimilarity(questionVector, statusIntentVector);
        double faultSim = cosineSimilarity(questionVector, faultIntentVector);

        System.out.printf(" [意图分类引擎] 状态查询概率: %.4f | 故障排查概率: %.4f%n", statusSim, faultSim);

        if (statusSim > INTENT_THRESHOLD && statusSim > faultSim) {
            return IntentType.STATUS_QUERY;
        } else if (faultSim > INTENT_THRESHOLD && faultSim > statusSim) {
            return IntentType.FAULT_RAG;
        }
        return IntentType.UNKNOWN;
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