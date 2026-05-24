import re
import logging
import requests
from typing import AsyncGenerator

from langchain_openai import ChatOpenAI
from langchain.agents import create_tool_calling_agent, AgentExecutor
from langchain_core.prompts import ChatPromptTemplate

# 导入底层组件 (得益于良好的工程切分，这里如同组装乐高)
from app.core.config import settings
from app.schemas.intent_schema import IntentType
from app.services.nlp.intent_classifier import intent_classifier
from app.services.nlp.prompt_manager import prompt_service
from app.services.nlp.context_builder import context_builder
from app.services.rag.vector_store import get_vector_store
from app.services.agent.tools.device_tools import query_device_status

logger = logging.getLogger(__name__)

class RagService:
    """
    RAG 与 Agent 混合调度引擎
    职责: 接收用户问题，进行意图多级路由，动态组装上下文，并以异步流的形式返回大模型推演结果。
    对应 Java: RagServiceImpl.java
    """

    def __init__(self):
        # 预编译正则表达式，匹配类似 "ATM-SN-001" 的工业设备编号 (1:1 复刻)
        self.device_code_pattern = re.compile(r"([A-Z0-9]+-[A-Z0-9]+-[A-Z0-9]+)")
        
        # 初始化 LLM 引擎，开启 streaming 模式
        self.llm = ChatOpenAI(
            temperature=0.1,
            model=settings.CHAT_MODEL,
            openai_api_key=settings.ZHIPU_API_KEY,
            openai_api_base=settings.ZHIPU_BASE_URL,
            streaming=True
        )
        
        self.vector_store = get_vector_store()
        
        # 挂载可供大模型使用的工具集
        self.tools = [query_device_status]

    async def smart_chat_stream(self, question: str) -> AsyncGenerator[str, None]:
        """
        主入口：智能对话流式分发
        :param question: 用户原始提问
        """
        print("======  接收到用户指令，进入网关分发系统 ======")

        # 1. 调用独立的意图分类引擎
        matched_intents = intent_classifier.classify(question)

        # 2. 分级路由拦截机制
        # 【判定 1】: 混合意图 (识别出 2 个及以上的意图)，直接降级给 Agent 引擎自主判断
        if len(matched_intents) > 1:
            print(" [路由层] 检测到混合意图，进入 Agent 兜底")
            async for chunk in self._handle_agent_fallback(question): yield chunk
            return
            
        # 【判定 2】: 单一意图精准命中
        if len(matched_intents) == 1:
            main_intent = matched_intents[0].intent_type
            if main_intent == IntentType.STATUS_QUERY:
                async for chunk in self._handle_status_query(question): yield chunk
                return
            elif main_intent == IntentType.FAULT_RAG:
                async for chunk in self._handle_fault_rag(question): yield chunk
                return

        # 【判定 3】: 未命中任何意图（得分低于阈值），走长尾兜底
        print(" [路由层] 意图不明确，进入 Agent 兜底")
        async for chunk in self._handle_agent_fallback(question): yield chunk

    # =====================================================================
    # 【第一级路由】：正则与规则引擎拦截 (最快、最稳、0 Token成本)
    # =====================================================================
    async def _handle_status_query(self, question: str) -> AsyncGenerator[str, None]:
        match = self.device_code_pattern.search(question)
        if match:
            extract_code = match.group(1)
            print(f" [路由层] 命中第一级规则拦截，提取到设备编号：{extract_code}")
            
            try:
                # 穿透到 Java 核心系统的内网接口
                api_url = f"{settings.JAVA_BACKEND_URL}/api/internal/device/status?code={extract_code}"
                res = requests.get(api_url, timeout=3)
                
                if res.status_code == 200:
                    data = res.json().get("data", {})
                    # 规则拦截成功，直接流式输出固定话术，完全绕开大模型
                    status_str = "异常/高温报警" if data.get("status") == 1 else "运行正常"
                    temp_str = data.get('temperature', '未知')
                    response_text = f"查询成功：设备 [{extract_code}] 当前状态为 {status_str}，实时温度为 {temp_str}℃。"
                    yield response_text
                    return
            except Exception as e:
                print(f" [路由层] 规则直连 Java 失败: {e}，准备降级")
                
        # 未匹配到设备编号或直连失败，降级给 Agent
        async for chunk in self._handle_agent_fallback(question): yield chunk

    # =====================================================================
    # 【第二级路由】：垂直 RAG 专线路由 (无 Agent 幻觉风险)
    # =====================================================================
    async def _handle_fault_rag(self, question: str) -> AsyncGenerator[str, None]:
        print(" [路由层] 命中第二级知识库专线，进入纯净 RAG 模式")
        
        context = self._search_vector_store(question)
        dynamic_template = prompt_service.get_prompt_content("device_rag")
        params = context_builder.build(question, context)
        
        # 纯净模式：不挂载任何 Function Calling
        prompt = ChatPromptTemplate.from_messages([
            ("system", dynamic_template),
            ("human", "{input}")
        ])
        
        chain = prompt | self.llm
        
        # LangChain 的异步流式输出
        async for chunk in chain.astream(params):
            if chunk.content:
                yield chunk.content

    # =====================================================================
    # 【第三级路由】：大模型 Agent 兜底路由 (应对复杂与长尾提问)
    # =====================================================================
    async def _handle_agent_fallback(self, question: str) -> AsyncGenerator[str, None]:
        print(" [路由层] 未命中规则，进入第三级 Agent 兜底模式，交由大模型自行推理")
        
        context = self._search_vector_store(question)
        dynamic_template = prompt_service.get_prompt_content("device_rag")
        params = context_builder.build(question, context)
        
        # Agent 模式：必须留出 agent_scratchpad 供模型写“草稿”
        prompt = ChatPromptTemplate.from_messages([
            ("system", dynamic_template),
            ("human", "{input}"),
            ("placeholder", "{agent_scratchpad}"),
        ])
        
        agent = create_tool_calling_agent(self.llm, self.tools, prompt)
        agent_executor = AgentExecutor(agent=agent, tools=self.tools)
        
        # astream_events 是目前 LangChain 官方推荐的最优雅的 Agent 流式追踪方法
        async for event in agent_executor.astream_events(params, version="v1"):
            # 我们只把模型真正输出给用户的文本发送到前端，屏蔽掉它内部调用工具的思考过程
            if event["event"] == "on_chat_model_stream":
                content = event["data"]["chunk"].content
                if content:
                    yield content

    def _search_vector_store(self, question: str) -> str:
        """抽取出的私有方法：向量检索逻辑"""
        try:
            # 默认 Top-K = 3
            docs = self.vector_store.similarity_search(question, k=3)
            return "\n".join([doc.page_content for doc in docs])
        except Exception as e:
            print(f" [向量检索异常]: {e}")
            return ""

# 导出服务单例
rag_service = RagService()