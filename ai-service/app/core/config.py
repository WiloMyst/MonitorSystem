import logging
from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict

logger = logging.getLogger(__name__)

class Settings(BaseSettings):
    """
    企业级全局统一配置中心 (平替 Spring Boot application.yml)
    职责: 集中管理所有环境变量、数据库连接串与 API 密钥。支持强类型校验与默认值回退。
    
    使用说明:
    1. 优先读取系统环境变量。
    2. 若系统变量不存在，自动读取项目根目录的 .env 文件。
    3. 若 .env 亦无配置，则使用 Field(default=...) 的默认值。
    """

    # ======================== AI 大模型配置 ========================
    ZHIPU_API_KEY: str = Field(
        default="", 
        description="智谱大模型 API Key，必须从系统环境变量或 .env 中注入"
    )
    ZHIPU_BASE_URL: str = Field(
        default="https://open.bigmodel.cn/api/paas/v4/", 
        description="智谱兼容 OpenAI 格式的网关地址"
    )
    CHAT_MODEL: str = Field(
        default="glm-4-flash", 
        description="对话生成基座模型名称"
    )
    EMBEDDING_MODEL: str = Field(
        default="embedding-2", 
        description="向量化模型名称"
    )

    # ======================== 向量数据库配置 ========================
    REDIS_URL: str = Field(
        default="redis://:123456@127.0.0.1:6379/0", 
        description="Redis Stack 向量数据库连接串"
    )
    REDIS_INDEX_NAME: str = Field(
        default="monitor_manual_index", 
        description="RAG 知识库在 Redis 中的索引空间名称"
    )

    # ======================== 内部微服务调用配置 ========================
    JAVA_BACKEND_URL: str = Field(
        default="http://127.0.0.1:9090", 
        description="Java 核心网关地址，供 Agent 逆向回调使用"
    )
    
    # 启用 Pydantic 的 .env 文件解析支持
    model_config = SettingsConfigDict(
        env_file=".env", 
        env_file_encoding="utf-8",
        extra="ignore"  # 忽略 .env 中多余的且未在此处定义的变量
    )

# 实例化全局单例配置对象，供整个微服务项目导入使用
settings = Settings()

if not settings.ZHIPU_API_KEY:
    logger.warning(" 警告: 未检测到 ZHIPU_API_KEY，AI 功能将无法正常工作！")