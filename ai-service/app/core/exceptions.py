import logging
from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from fastapi.exceptions import RequestValidationError

logger = logging.getLogger(__name__)

class BusinessException(Exception):
    """自定义业务异常基类"""
    def __init__(self, code: int, message: str):
        self.code = code
        self.message = message

def setup_exception_handlers(app: FastAPI):
    """
    注册全局异常拦截器
    对应 Java: @RestControllerAdvice
    """
    
    # 1. 拦截参数校验异常 (对应 Java 的 MethodArgumentNotValidException)
    @app.exception_handler(RequestValidationError)
    async def validation_exception_handler(request: Request, exc: RequestValidationError):
        # 提取第一个校验错误的信息
        error_msg = exc.errors()[0].get('msg', '参数校验失败')
        logger.warning(f"参数校验拦截: {error_msg} | 请求路径: {request.url}")
        return JSONResponse(
            status_code=400,
            content={"code": 400, "message": f"参数格式错误: {error_msg}", "data": None}
        )

    # 2. 拦截自定义业务异常 (对应 Java 的 BusinessException)
    @app.exception_handler(BusinessException)
    async def business_exception_handler(request: Request, exc: BusinessException):
        logger.warning(f"业务异常: {exc.message} | 请求路径: {request.url}")
        return JSONResponse(
            status_code=200, # 业务报错 HTTP 状态码依然给 200，靠内部 code 区分
            content={"code": exc.code, "message": exc.message, "data": None}
        )

    # 3. 终极兜底：拦截所有未知的系统崩溃 (对应 Java 的 Exception)
    @app.exception_handler(Exception)
    async def global_exception_handler(request: Request, exc: Exception):
        # 在后台必须把完整堆栈打印出来供程序员排查
        logger.error(f" 系统发生严重崩溃 | 请求路径: {request.url}", exc_info=True)
        # 给前端只返回一句温柔的提示
        return JSONResponse(
            status_code=500,
            content={"code": 500, "message": "系统内部繁忙，请稍后再试", "data": None}
        )