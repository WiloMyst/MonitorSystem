import logging
from typing import Dict, Any

logger = logging.getLogger(__name__)

class ContextBuilder:
    """
    RAG 动态上下文组装引擎
    职责: 负责屏蔽底层参数的复杂性，将零散的数据（用户输入、知识库检索结果、系统环境变量等）
          统一组装为 LangChain PromptTemplate 能够识别的标准字典格式。
    对应 Java: ContextBuilder.java
    """

    @staticmethod
    def build(question: str, rag_context: str) -> Dict[str, Any]:
        """
        动态组装大模型所需的全部系统级上下文
        
        :param question: 用户的当前原始提问
        :param rag_context: 从 Redis 向量库中检索出的底层文档切片
        :return: 包含完整 Prompt 参数的字典
        """
        prompt_params: Dict[str, Any] = {}

        # 1. 注入用户当前问题
        # 注意: LangChain 的标准提示词模板中，通常使用 'input' 代表用户的提问
        prompt_params["input"] = question.strip() if question else ""

        # 2. 注入 RAG 检索到的私有化知识库 (做兜底处理)
        # 防止因 Redis 挂掉或未检索到内容导致大模型拿到 None 产生幻觉
        if rag_context and rag_context.strip():
            prompt_params["context"] = rag_context.strip()
        else:
            logger.debug("未提供有效的 rag_context，使用默认安全兜底文案")
            prompt_params["context"] = "暂无相关底层设备手册知识"

        # 3. 企业级扩展预留位
        # import datetime
        # prompt_params["current_time"] = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        # prompt_params["user_role"] = "运维工程师"

        return prompt_params

# 导出服务单例
context_builder = ContextBuilder()