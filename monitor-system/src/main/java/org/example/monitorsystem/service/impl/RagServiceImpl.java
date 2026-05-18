package org.example.monitorsystem.service.impl;

import org.example.monitorsystem.service.IRagService;
import org.example.monitorsystem.service.ISysPromptService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RagServiceImpl implements IRagService {

    private final ChatClient chatClient;
    // 【架构升级】：这里的 VectorStore 会被 Spring 自动注入为 RedisVectorStore，无需我们再 new
    private final VectorStore vectorStore;

    @Value("classpath:/docs/maintenance_manual.txt")
    private Resource manualResource;

    @Autowired
    private ISysPromptService sysPromptService;

    // 构造器注入
    public RagServiceImpl(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
    }

    /**
     * 【企业级改造】：抽离出来的知识库同步方法。
     * 在真实系统中，这个方法应该绑定给后台页面的一个“更新知识库”按钮，而不是每次启动服务器都跑一遍。
     */
    public void syncKnowledgeBaseToRedis() {
        System.out.println("========== [RAG] 开始向 Redis 向量库同步物理知识文档 ==========");
        try {
            // 1. 读取并切片
            TikaDocumentReader documentReader = new TikaDocumentReader(manualResource);
            List<Document> rawDocuments = documentReader.get();
            TokenTextSplitter splitter = new TokenTextSplitter();
            List<Document> chunkedDocuments = splitter.apply(rawDocuments);

            // 2. 写入 Redis (如果文档较多，这里底层会自动分批调用 Embedding 模型并存入 Redis)
            vectorStore.add(chunkedDocuments);

            System.out.println("========== [RAG] 同步成功！数据已永久持久化到 Redis 向量库 ==========");
        } catch (Exception e) {
            System.err.println("❌ 知识库同步失败：" + e.getMessage());
        }
    }

    @Override
    public Flux<String> smartChat(String question) {
        // 1. 向量检索 (TopK 限制)
        List<Document> similarDocuments = vectorStore.similaritySearch(
                SearchRequest.query(question).withTopK(3)
        );

        // 2. 提取上下文
        String context = similarDocuments.stream()
                .map(Document::getContent)
                .collect(Collectors.joining("\n"));

        // 3. 从数据库获取纯净的模板 (里面包括 {context} {question})
        String dynamicTemplate = sysPromptService.getPromptContentByCode("device_rag");

        System.out.println(" [DEBUG] 提取到的上下文长度: " + context.length());

        // 4. 发送 Prompt
        // 暂时无奈抛弃 .user()，因为报文会丢失
        return this.chatClient.prompt()
                .system(u -> u.text(dynamicTemplate )
                        .param("context", context)
                        .param("question", question))
                .functions("queryDeviceStatus")
                .stream()
                .content();
    }
}