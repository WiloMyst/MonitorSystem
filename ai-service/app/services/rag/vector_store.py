import logging
from langchain_community.vectorstores import Redis
from langchain_openai import OpenAIEmbeddings
from app.core.config import settings

logger = logging.getLogger(__name__)

class RedisVectorStoreFactory:
    """
    Redis 向量数据库工厂类 (单例模式)
    职责: 维护与 Redis-Stack 的长连接，管理 Embedding 模型，提供统一的向量检索和入库能力。
    对应 Java: RagConfig.java
    """
    _instance: Redis = None

    @classmethod
    def get_instance(cls) -> Redis:
        """
        获取 RedisVectorStore 实例
        :return: 初始化的 Redis 向量库对象
        """
        if cls._instance is not None:
            return cls._instance

        print("====== [架构师接管] 手动初始化 Redis 向量库连接池 ======")
        
        # 1. 注入智谱大模型的 Embedding 引擎
        embeddings = OpenAIEmbeddings(
            openai_api_key=settings.ZHIPU_API_KEY,
            openai_api_base=settings.ZHIPU_BASE_URL,
            model=settings.EMBEDDING_MODEL
        )

        # 2. 初始化 Redis VectorStore
        try:
            cls._instance = Redis(
                redis_url=settings.REDIS_URL,
                index_name=settings.REDIS_INDEX_NAME,
                embedding=embeddings,
                key_prefix="rag:doc:"  # 严格对齐你 application.yml 中的前缀配置
            )
            print(f" [Redis] 向量库连接成功！索引名: {settings.REDIS_INDEX_NAME}")
        except Exception as e:
            # 严重基础设施故障，需向上抛出或终止服务
            print(f" [Redis] 向量库初始化失败: {e}")
            raise e

        return cls._instance

# 导出一个快捷的单例获取方法供外界调用
def get_vector_store() -> Redis:
    return RedisVectorStoreFactory.get_instance()