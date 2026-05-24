import uvicorn
import logging
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

# 导入内部配置与路由
from app.core.config import settings
from app.core.exceptions import setup_exception_handlers
from app.api.v1 import chat_router

# 初始化日志体系
logging.basicConfig(
    level=logging.INFO, 
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger("MonitorAI")

# ==========================================
# 1. 实例化核心应用
# ==========================================
app = FastAPI(
    title="Monitor AI Microservice",
    description="企业级设备监控 AI 调度微服务 (FastAPI + LangChain)",
    version="1.0.0"
)

# ==========================================
# 2. 挂载全局中间件 (跨域处理)
# ==========================================
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"], # 生产环境建议替换为前端实际域名
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ==========================================
# 3. 装配全局组件
# ==========================================
# 注册全局异常拦截器
setup_exception_handlers(app)

# 注册 API 路由树
app.include_router(chat_router.router)

# ==========================================
# 4. 服务启动钩子 (可选)
# ==========================================
@app.on_event("startup")
async def startup_event():
    logger.info(" Monitor AI 微服务启动成功，正在监听请求...")

if __name__ == "__main__":
    # 使用 Uvicorn 启动 ASGI 服务
    uvicorn.run(
        "app.main:app", 
        host="0.0.0.0", 
        port=8000, 
        reload=True # 开发模式下开启代码热更
    )