import logging
import redis
import requests
from typing import Optional
from app.core.config import settings

logger = logging.getLogger(__name__)

class SysPromptService:
    """
    动态提示词热更新与缓存服务
    职责: 负责大模型 Prompt 模板的获取、缓存管理（Cache-Aside 模式）以及热加载。
    对应 Java: SysPromptServiceImpl.java
    """

    def __init__(self):
        # 初始化 Redis 连接池 (复用连接，避免每次请求频繁握手)
        try:
            self.redis_client = redis.from_url(
                url=settings.REDIS_URL, 
                decode_responses=True, # 自动将 Redis 返回的 bytes 解码为 str
                socket_timeout=3.0     # 设置超时防雪崩
            )
            self.cache_prefix = "sys:prompt:"
            logger.info(" [提示词服务] 成功连接 Redis 缓存集群。")
        except Exception as e:
            logger.error(f" [提示词服务] Redis 连接失败: {e}")
            raise RuntimeError("基础设施异常: 提示词缓存服务无法启动")

    def _fetch_prompt_from_db(self, prompt_code: str) -> str:
        """
        [私有方法] 穿透到持久层 (Java 核心网关) 获取配置的逻辑
        """
        logger.warning(f" [提示词服务] Redis 缓存未命中，触发持久层回源查询，业务编码: {prompt_code}")
        
        # 1. 动态拼接 Java 内部 API 的 URL
        api_url = f"{settings.JAVA_BACKEND_URL}/api/internal/prompt/get"
        params = {"promptCode": prompt_code}

        try:
            # 发起 HTTP GET 请求 (务必设置 timeout，防止 Java 假死导致 Python 线程耗尽雪崩)
            response = requests.get(api_url, params=params, timeout=3.0)
            
            # 校验 HTTP 状态码
            if response.status_code == 200:
                res_data = response.json()
                # 校验 Java 封装的 Result 状态码
                if res_data.get("code") == 200 and res_data.get("data"):
                    logger.info(f" [提示词服务] 成功从 Java 持久层拉取提示词: {prompt_code}")
                    return res_data.get("data")
                else:
                    raise ValueError(f"Java 后端业务异常: {res_data.get('message')}")
            else:
                raise RuntimeError(f"HTTP 网络异常: 状态码 {response.status_code}")

        except Exception as e:
            logger.error(f" [提示词服务] 从 Java 核心系统获取提示词失败: {e}")
            
            # 2. 终极容灾防线 (Disaster Recovery)
            logger.warning(" [提示词服务] 启动内存容灾模板兜底！")
            fallback_prompt = (
                "你是一个专业的工业设备排障AI助手，根据【知识库上下文】来回答问题。\n\n"
                "【知识库上下文】\n{context}\n\n"
                "【用户问题】\n{input}"
            )
            
            if prompt_code == "device_rag":
                return fallback_prompt
                
            raise ValueError(f"系统提示词配置丢失且无兜底方案: {prompt_code}")

    def get_prompt_content(self, prompt_code: str) -> str:
        """
        核心方法：根据业务编码获取提示词模板 (带有防击穿逻辑)
        
        :param prompt_code: 提示词的唯一业务编码 (如 "device_rag")
        :return: 渲染前的大模型提示词模板
        """
        redis_key = f"{self.cache_prefix}{prompt_code}"
        
        try:
            # 1. 尝试从 Redis 缓存中极速获取
            cached_prompt: Optional[str] = self.redis_client.get(redis_key)
            if cached_prompt:
                logger.debug(f"命中缓存：极速加载提示词 [{prompt_code}]")
                return cached_prompt
        except Exception as e:
            logger.error(f"Redis 读取异常，准备降级直连数据库: {e}")

        # 2. 缓存未命中或 Redis 崩溃，回源查询数据库 (持久层)
        prompt_content = self._fetch_prompt_from_db(prompt_code)
        
        # 3. 异步回写 Redis 缓存，设置 24 小时过期时间 (防止冷数据长期占用内存)
        try:
            # ex=86400 即过期时间为 24 小时
            self.redis_client.set(name=redis_key, value=prompt_content, ex=86400)
            logger.info(f"缓存重建完毕：已将 [{prompt_code}] 写入 Redis。")
        except Exception as e:
            logger.error(f"Redis 缓存回写失败，但不影响主业务: {e}")

        return prompt_content

    def refresh_prompt_cache(self, prompt_code: str) -> None:
        """
        【热更新机制】暴露给内部管理后台的接口。
        当管理员在 Vue 页面修改了提示词后，调用此方法清空缓存，实现大模型人设的秒级热切换。
        
        :param prompt_code: 需要刷新的业务编码
        """
        redis_key = f"{self.cache_prefix}{prompt_code}"
        try:
            deleted_count = self.redis_client.delete(redis_key)
            if deleted_count > 0:
                logger.info(f" 成功清空提示词 [{prompt_code}] 的缓存，下次调用将热加载最新配置！")
            else:
                logger.info(f" 提示词 [{prompt_code}] 缓存本身为空，无需清理。")
        except Exception as e:
            logger.error(f" 缓存清理失败，可能导致提示词脏读: {e}")
            raise RuntimeError("热加载缓存清理失败")

# 导出单例，确保整个微服务生命周期内共用同一个 Redis 连接池
prompt_service = SysPromptService()