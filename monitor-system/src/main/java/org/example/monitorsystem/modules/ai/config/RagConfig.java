package org.example.monitorsystem.modules.ai.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.RedisVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RAG 与向量数据库的核心配置类
 * 终极方案：彻底绕过 Spring AI 1.0.0-M1 的残疾 AutoConfiguration，手动接管 Bean 的生命周期
 */
@Configuration
public class RagConfig {

    // 直接从你的 application-dev.yml 里把值拽出来
    @Value("${spring.ai.vectorstore.redis.uri}")
    private String redisUri;

    @Value("${spring.ai.vectorstore.redis.index}")
    private String indexName;

    @Value("${spring.ai.vectorstore.redis.prefix}")
    private String prefix;

    /**
     * 手动创建 RedisVectorStore。
     * EmbeddingModel 这个参数 Spring 会自动把智谱 AI 的模型注入进来。
     */
    @Bean
    public RedisVectorStore vectorStore(EmbeddingModel embeddingModel) {

        System.out.println("====== [架构师接管] 绕过框架 Bug，手动初始化 Redis 向量库 ======");

        // 1. 照着源码的样子，手动把配置对象拼装起来
        RedisVectorStore.RedisVectorStoreConfig config = RedisVectorStore.RedisVectorStoreConfig.builder()
                .withURI(redisUri)
                .withIndexName(indexName)
                .withPrefix(prefix)
                .build();

        // 2. new 出最终的向量数据库对象
        // 第三个参数 true 代表：如果 Redis-stack 里还没有这个索引，系统启动时自动建一个
        return new RedisVectorStore(config, embeddingModel, true);
    }
}