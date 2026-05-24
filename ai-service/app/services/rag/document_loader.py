import os
import logging
from typing import List
from langchain_community.document_loaders import UnstructuredLoader
from langchain_core.documents import Document

logger = logging.getLogger(__name__)

class DocumentLoader:
    """
    物理文档解析引擎
    职责: 完美平替 Java Apache Tika，支持将 PDF, Word, Excel, TXT, MD 等各种格式
          的高复杂度非结构化物理文件，精准解析为 LangChain 标准的 Document 对象。
    """
    
    def __init__(self, file_path: str):
        """
        初始化解析器
        :param file_path: 物理文件的绝对或相对路径 (现在可以传入 .docx, .xlsx 等格式了)
        """
        self.file_path = file_path

    def load(self) -> List[Document]:
        """
        执行读取操作
        :return: 解析后的原始 Document 列表
        :raises FileNotFoundError: 当目标路径文件丢失时触发
        """
        if not os.path.exists(self.file_path):
            raise FileNotFoundError(f" 严重错误: 无法找到知识库原文件: {self.file_path}")
            
        logger.info(f" [文档解析引擎] 开始读取并智能分析物理文件: {self.file_path}")
        
        # ==========================================
        # 使用 UnstructuredLoader
        # ==========================================
        # strategy="fast" 代表优先提取文本，不强行做复杂的图像 OCR 分析，速度最快
        # mode="elements" 会将标题、段落、列表保留为独立的语义块，有利于后续 RAG 切片
        try:
            loader = UnstructuredLoader(
                self.file_path,
                strategy="fast", 
                mode="single" # "single" 将整个文档拼成一个大的长文本，行为上最接近 Tika
            )
            raw_documents = loader.load()
            
            logger.info(f" [文档解析引擎] 解析成功！共提取出 {len(raw_documents)} 个基础结构块。")
            return raw_documents
            
        except Exception as e:
            logger.error(f" [文档解析引擎] 解析文件失败，可能是缺少系统依赖或格式损坏: {e}")
            raise RuntimeError(f"文档解析引擎崩溃: {e}")