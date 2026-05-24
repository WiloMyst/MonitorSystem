from pydantic import BaseModel, Field
from typing import Optional, Any

class AgentObservation(BaseModel):
    """
    Agent 工具调用的标准观察结果 (安全包装器)
    对应 Java: AgentObservation.java
    """
    success: bool = Field(..., description="工具是否执行成功")
    data: Optional[Any] = Field(default=None, description="成功的业务数据")
    error_reason: Optional[str] = Field(default=None, description="失败的明确原因")
    action_advice: str = Field(..., description="给大模型的行为建议，引导自我纠错")

class DeviceStatusRequest(BaseModel):
    """
    设备状态查询工具的入参约束
    """
    device_code: str = Field(
        ..., 
        pattern=r"^[A-Z0-9]+-[A-Z0-9]+-[A-Z0-9]+$", 
        description="需要查询的设备唯一编号。请务必从用户的提问中精准提取此编号。",
        json_schema_extra={"example": "ATM-SN-001"}
    )