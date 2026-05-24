import requests
from langchain_core.tools import tool
from schemas.tool_schema import DeviceStatusRequest
from app.services.agent.adapter import tool_use_adapter
from app.core.config import settings

@tool("query_device_status", args_schema=DeviceStatusRequest)
@tool_use_adapter(tool_name="设备状态查询工具")
def query_device_status(device_code: str) -> str:
    """
    这是一个查询设备状态的工具。当你需要回答某个具体设备的实时状态、运行温度等动态信息时，必须调用此工具。
    (注意：这里的 docstring 等同于 Java 中的 @Description，LangChain 会自动将其发送给智谱模型)
    """
    
    # 1. 模拟 Java 内部查询：向 Java 核心系统发起 HTTP 调用
    # 在生产环境中，此处可以是 gRPC 或是 HTTP 内网穿透
    api_url = f"{settings.JAVA_BACKEND_URL}/api/internal/device/status?code={device_code}"
    
    response = requests.get(api_url, timeout=5)
    
    # 2. 校验远端响应
    if response.status_code != 200:
        # 直接抛出异常，外层的适配器会自动捕获并转为安全 JSON 告诉大模型
        raise RuntimeError(f"Java 核心系统响应异常，状态码: {response.status_code}")
        
    res_json = response.json()
    data = res_json.get("data")
    
    if not data:
        raise RuntimeError("数据库中未查找到该设备编号")

    # 3. 组装结果 (平替 Java 中的业务处理)
    status_str = "异常/高温报警" if data.get("status") == 1 else "正常"
    temp_str = f"{data.get('temperature')}℃" if data.get("temperature") is not None else "暂无数据"

    return {
        "deviceCode": device_code,
        "status": status_str,
        "temperature": temp_str
    }