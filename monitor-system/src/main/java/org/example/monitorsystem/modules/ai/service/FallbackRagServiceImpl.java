package org.example.monitorsystem.modules.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 降级 RAG 服务实现
 * 当 AI 微服务不可用时，返回预设的降级提示信息，引导用户手动操作。
 */
@Service
public class FallbackRagServiceImpl implements IRagService {

    private static final Logger log = LoggerFactory.getLogger(FallbackRagServiceImpl.class);

    @Override
    public Flux<String> smartChat(String question) {
        log.info("[降级模式] AI微服务不可用，返回降级提示");
        return Flux.just(
                "AI 服务当前处于降级模式，暂时无法提供智能分析。 ",
                "您可以尝试以下操作：\n",
                "1. 稍后重试\n",
                "2. 手动查看设备监控面板获取设备状态\n",
                "3. 联系运维人员处理紧急故障\n"
        );
    }
}
