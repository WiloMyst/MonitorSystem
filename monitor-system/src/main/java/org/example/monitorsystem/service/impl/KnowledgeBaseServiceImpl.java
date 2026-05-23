package org.example.monitorsystem.service.impl;

import org.example.monitorsystem.service.IKnowledgeBaseService;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KnowledgeBaseServiceImpl implements IKnowledgeBaseService {

    @Autowired
    private VectorStore vectorStore;

    @Value("classpath:/docs/maintenance_manual.pdf")
    private Resource manualResource;

    /**
     * 抽离出来的知识库同步方法。
     * 在真实系统中，这个方法应该绑定给后台页面的一个“更新知识库”按钮，而不是每次启动服务器都跑一遍。
     */
    @Override
    public void syncToRedis() {
        System.out.println("========== [RAG] 开始向 Redis 向量库同步物理知识文档 ==========");
        try {
            // 1. 读取并切片
            TikaDocumentReader documentReader = new TikaDocumentReader(manualResource);
            List<Document> rawDocuments = documentReader.get();
            // 手动指定切片参数
            // chunkSize = 800 (每个块最大长度), keepSeparator = true (保留分隔符)
            TokenTextSplitter splitter = new TokenTextSplitter(800, 350, 5, 10000, true);
            List<Document> chunkedDocuments = splitter.apply(rawDocuments);

            // 2. 写入 Redis (如果文档较多，这里底层会自动分批调用 Embedding 模型并存入 Redis)
            vectorStore.add(chunkedDocuments);

            System.out.println("========== [RAG] 同步成功！数据已永久持久化到 Redis 向量库 ==========");
        } catch (Exception e) {
            System.err.println("知识库同步失败：" + e.getMessage());
        }
    }
}