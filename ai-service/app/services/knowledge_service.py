import traceback
from app.services.rag.document_loader import DocumentLoader
from app.services.rag.text_splitter import KnowledgeTextSplitter
from app.services.rag.vector_store import get_vector_store

class KnowledgeBaseService:
    """
    私有化知识库核心业务服务
    职责: 协调 Loader, Splitter 和 VectorStore，实现物理文件的全量或增量同步。
    对应 Java: KnowledgeBaseServiceImpl.java
    """

    def __init__(self):
        # 模拟 application.yml 中的 @Value("classpath:/docs/maintenance_manual.pdf")
        self.manual_path = "./resources/docs/maintenance_manual.pdf"
        self.vector_store = get_vector_store()

    def sync_to_redis(self) -> None:
        """
        将物理知识文档同步并持久化至 Redis 向量库
        注: 在真实系统中，该方法应绑定至后台管理系统的一个定时任务或“手动触发”接口中。
        """
        print("========== [RAG] 开始向 Redis 向量库同步物理知识文档 ==========")
        try:
            # 1. 实例化加载器并读取原始文件
            loader = DocumentLoader(file_path=self.manual_path)
            raw_documents = loader.load()

            # 2. 实例化切片器并执行文本切片 (对齐 Java 参数 800, 350)
            splitter = KnowledgeTextSplitter(chunk_size=800, chunk_overlap=350)
            chunked_documents = splitter.apply(raw_documents)

            # 3. 写入 Redis (LangChain 底层会自动并发调用 Embedding 接口并将高维向量写入 Redis)
            # 使用 add_documents 方法实现平滑插入
            self.vector_store.add_documents(documents=chunked_documents)

            print("========== [RAG] 同步成功！数据已永久持久化到 Redis 向量库 ==========")
            
        except FileNotFoundError as fnf_error:
            # 捕获已知的文件丢失异常
            print(str(fnf_error))
        except Exception as e:
            # 捕获网络、Redis 连接或大模型 API 异常
            print(f" [RAG] 知识库同步遭遇未知失败: {e}")
            # 在企业级日志中，必须打印完整堆栈
            traceback.print_exc()

# 导出服务单例
knowledge_base_service = KnowledgeBaseService()