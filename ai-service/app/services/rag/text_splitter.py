from typing import List
from langchain_text_splitters import TokenTextSplitter
from langchain_core.documents import Document

class KnowledgeTextSplitter:
    """
    知识库结构化切片引擎
    职责: 针对大段文本进行基于 Token 或字符长度的滑动窗口切片，防止超出大模型上下文限制，并保持语义连贯。
    """

    def __init__(self, chunk_size: int = 800, chunk_overlap: int = 350):
        """
        初始化切片器
        :param chunk_size: 单个切片的最大 Token 数量 (对齐 Java 参数 800)
        :param chunk_overlap: 滑动窗口重叠的 Token 数量，用于解决语义断层 (对齐 Java 参数 350)
        """
        # 平替 Java 的 TokenTextSplitter
        self.splitter = TokenTextSplitter(
            chunk_size=chunk_size,
            chunk_overlap=chunk_overlap,
            # keep_separator 的逻辑在 Python 中默认已优化处理
        )

    def apply(self, documents: List[Document]) -> List[Document]:
        """
        执行切片操作
        :param documents: 原始 Document 列表
        :return: 切片并结构化后的 Document 列表
        """
        print(f" [文本切片引擎] 开始进行结构化切片 (ChunkSize={self.splitter._chunk_size}, Overlap={self.splitter._chunk_overlap})")
        
        chunked_documents = self.splitter.split_documents(documents)
        
        print(f" [文本切片引擎] 切片完成，共生成 {len(chunked_documents)} 个上下文分块。")
        return chunked_documents