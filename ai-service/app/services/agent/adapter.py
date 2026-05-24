import functools
import json
import logging
from pydantic import ValidationError
from schemas.tool_schema import AgentObservation

logger = logging.getLogger(__name__)

def tool_use_adapter(tool_name: str):
    """
    通用的 Tool-Use 适配层核心装饰器。
    职责: 拦截参数校验异常与底层业务崩溃，输出标准化指令引导大模型自我纠错。
    对应 Java: ToolUseAdapter.java
    
    :param tool_name: 工具名称（用于日志记录）
    """
    def decorator(func):
        @functools.wraps(func)
        def wrapper(*args, **kwargs):
            print(f" [Tool-Use 适配层] 拦截到大模型调用工具: {tool_name}")
            
            try:
                # ==========================================
                # 1. 执行真正的业务逻辑
                # ==========================================
                result = func(*args, **kwargs)
                print(f" [{tool_name}] 业务逻辑执行成功")
                
                # 构建成功的标准化回复
                obs = AgentObservation(
                    success=True,
                    data=result,
                    error_reason=None,
                    action_advice="执行成功，请结合该数据组织最终回复给用户。"
                )
                # 大模型只能接收字符串，所以将对象序列化为 JSON
                return obs.model_dump_json()

            except ValidationError as ve:
                # ==========================================
                # 2. 参数前置自动校验机制 (拦截 Pydantic 报错)
                # ==========================================
                # 提取第一条验证错误信息
                error_msg = ve.errors()[0].get('msg', '参数格式不符合规范')
                print(f" [{tool_name}] 参数校验被拦截: {error_msg}")
                
                obs = AgentObservation(
                    success=False,
                    data=None,
                    error_reason=f"参数校验失败：{error_msg}",
                    action_advice="请检查你生成的参数，如果用户提供的信息不足以满足格式要求，请主动向用户追问缺失的信息。"
                )
                return obs.model_dump_json()

            except Exception as e:
                # ==========================================
                # 3. 异常拦截与安全隔离机制
                # ==========================================
                # 彻底阻断底层异常抛出到 AI 引擎，防止对话流崩溃
                print(f" [{tool_name}] 底层业务发生崩溃: {str(e)}")
                
                obs = AgentObservation(
                    success=False,
                    data=None,
                    error_reason=f"底层系统异常：{str(e)}",
                    action_advice="工具调用遭遇系统级异常，请向用户致歉并告知系统暂时繁忙。"
                )
                return obs.model_dump_json()
                
        return wrapper
    return decorator