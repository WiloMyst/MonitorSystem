from enum import Enum
from pydantic import BaseModel, Field

class IntentType(str, Enum):
    """
    AI 意图类型字典
    """
    STATUS_QUERY = "STATUS_QUERY"  # 状态查询
    FAULT_RAG = "FAULT_RAG"        # 故障排查
    UNKNOWN = "UNKNOWN"            # 兜底意图

class IntentMatch(BaseModel):
    """
    意图匹配结果封装
    """
    intent_type: IntentType = Field(..., description="匹配到的目标意图类型")
    score: float = Field(..., description="余弦相似度得分 (0.0 ~ 1.0)", ge=0.0, le=1.0)