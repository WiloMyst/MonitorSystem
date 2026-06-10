package org.example.monitorsystem.modules.ai.service;

import reactor.core.publisher.Flux;

/**
 * RAG 服务接口
 * 定义本地降级 RAG 能力，当 AI 微服务不可用时提供基础问答。
 */
public interface IRagService {
    Flux<String> smartChat(String question);
}
