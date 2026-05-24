import logging
import numpy as np
from typing import List, Dict
from langchain_openai import OpenAIEmbeddings

# 导入内部依赖
from core.config import settings
from schemas.intent_schema import IntentType, IntentMatch

# 配置当前模块的企业级日志
logger = logging.getLogger(__name__)

class IntentClassifier:
    """
    轻量级 AI 意图分类引擎
    职责: 基于文本 Embedding 与余弦相似度，对用户的输入进行多标签意图识别。
    对应 Java: IntentClassifier.java
    """

    def __init__(self):
        logger.info("========== [AI引擎] 初始化意图分类基准向量 ==========")
        
        # 1. 注入大模型 Embedding 客户端
        # 注意: 智谱大模型的 API 完全兼容 OpenAI 的 SDK
        self.embeddings = OpenAIEmbeddings(
            openai_api_key=settings.ZHIPU_API_KEY,
            openai_api_base=settings.ZHIPU_BASE_URL,
            model=settings.EMBEDDING_MODEL
        )
        
        # 2. 初始化核心参数 (对齐 Java: INTENT_THRESHOLD = 0.75)
        self.intent_threshold: float = 0.75
        self.intent_registry: Dict[IntentType, List[float]] = {}
        
        # 3. 执行启动时的基准向量预热
        self._init_registry()

    def _init_registry(self) -> None:
        """
        初始化意图注册表 (将基准描述转化为高维向量并常驻内存)
        未来优化点: 这些基准字符串可以移入 Redis 或 MySQL 实现动态加载
        """
        try:
            self.intent_registry[IntentType.STATUS_QUERY] = self.embeddings.embed_query(
                "查询设备的实时运行状态、温度、指标、当前情况是否正常"
            )
            self.intent_registry[IntentType.FAULT_RAG] = self.embeddings.embed_query(
                "设备出现故障、报错、脱机、异常、需要维修方案、怎么修、排障指导"
            )
            logger.info(f" [AI引擎] 成功注册 {len(self.intent_registry)} 个核心业务意图。")
        except Exception as e:
            logger.error(f" [AI引擎] 意图基准向量初始化失败: {e}", exc_info=True)
            raise RuntimeError("分类引擎初始化失败，服务阻断")

    @staticmethod
    def cosine_similarity(vec_a: List[float], vec_b: List[float]) -> float:
        """
        企业级数学计算库: 基于 Numpy 的极速余弦相似度计算
        平替 Java 中手动写的 for 循环 dotProduct 累加逻辑
        
        :param vec_a: 向量 A
        :param vec_b: 向量 B
        :return: 相似度得分 (-1.0 ~ 1.0)
        """
        a, b = np.array(vec_a), np.array(vec_b)
        
        # 防御性编程: 防止零向量导致的除零异常 (ZeroDivisionError)
        norm_a, norm_b = np.linalg.norm(a), np.linalg.norm(b)
        if norm_a == 0 or norm_b == 0:
            return 0.0
            
        return float(np.dot(a, b) / (norm_a * norm_b))

    def classify(self, question: str) -> List[IntentMatch]:
        """
        核心对外方法: 对传入的自然语言进行多标签分类
        
        :param question: 用户原始提问
        :return: 满足阈值条件的所有意图列表 (按分数降序排列)
        """
        if not question or not question.strip():
            logger.warning("传入的提问为空，自动判定为 UNKNOWN 意图")
            return []

        # 1. 实时计算当前问题的 Embedding
        try:
            question_vector = self.embeddings.embed_query(question)
        except Exception as e:
            logger.error(f"提取提问特征向量失败: {e}")
            return []

        matches: List[IntentMatch] = []

        # 2. 动态遍历注册表，进行相似度比对
        for intent_type, base_vector in self.intent_registry.items():
            sim = self.cosine_similarity(question_vector, base_vector)
            logger.debug(f" [意图分发] 校验 [{intent_type.value}] 余弦相似度: {sim:.4f}")
            
            # 只要超过阈值，统统加进备选列表 (解决混合意图的核心)
            if sim > self.intent_threshold:
                matches.append(IntentMatch(intent_type=intent_type, score=sim))
                
        # 3. 按照相似度分数从高到低降序排列 (平替 Java 的 matches.sort)
        matches.sort(key=lambda x: x.score, reverse=True)
        
        if matches:
            top_intent = matches[0].intent_type.value
            logger.info(f" [意图分发] 识别完成，Top1 命中意图: {top_intent} (得分: {matches[0].score:.4f})")
        else:
            logger.info(" [意图分发] 未命中任何注册意图，即将走兜底逻辑")

        return matches

# 导出服务单例，保证全局复用
intent_classifier = IntentClassifier()