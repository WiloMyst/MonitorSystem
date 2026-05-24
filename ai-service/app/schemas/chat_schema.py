from pydantic import BaseModel, Field

class ChatRequest(BaseModel):
    """
    用户对话请求 DTO
    对应 Java: Payload Map 或 ChatDTO
    """
    question: str = Field(
        ..., 
        min_length=1, 
        max_length=2000, 
        description="用户的原始提问内容，绝不能为空",
        json_schema_extra={"example": "ATM-SN-001设备温度异常怎么排查？"}
    )