import logging
from fastapi import APIRouter, Header, Depends
from sse_starlette.sse import EventSourceResponse

from app.schemas.chat_schema import ChatRequest
from app.services.rag.rag_service import rag_service

logger = logging.getLogger(__name__)

# 创建路由组 (对齐 Spring 的 @RequestMapping("/api/ai"))
router = APIRouter(prefix="/api/ai", tags=["AI 智能排障"])

@router.post("/ask", summary="AI 智能排障问答 (流式接口)")
async def ask_ai(request: ChatRequest, satoken: str = Header(None)):
    """
    接收前端问题，调用 RAG 调度引擎，并以 Server-Sent Events (SSE) 格式流式返回。
    """
    logger.info(f"收到用户提问 (Token: {satoken[:10] if satoken else 'None'}...): {request.question}")

    async def event_generator():
        try:
            # 调用 Service 层的调度逻辑
            async for text_chunk in rag_service.smart_chat_stream(request.question):
                # 严格按照 SSE 规范传输数据
                yield f"data: {text_chunk}\n\n"
        except Exception as e:
            logger.error(f"流式生成中断: {e}", exc_info=True)
            yield f"data: \n\n 抱歉，AI 引擎发生内部错误，流式响应已中断。\n\n"

    return EventSourceResponse(event_generator(), media_type="text/event-stream")