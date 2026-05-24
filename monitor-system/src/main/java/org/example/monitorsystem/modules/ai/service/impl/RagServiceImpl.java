package org.example.monitorsystem.modules.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.monitorsystem.modules.ai.component.ContextBuilder;
import org.example.monitorsystem.modules.ai.component.IntentClassifier;
import org.example.monitorsystem.modules.ai.component.IntentType;
import org.example.monitorsystem.modules.device.entity.DeviceInfo;
import org.example.monitorsystem.modules.ai.service.IRagService;
import org.example.monitorsystem.modules.device.service.IDeviceInfoService;
import org.example.monitorsystem.modules.system.prompt.service.ISysPromptService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class RagServiceImpl implements IRagService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    @Autowired
    private ISysPromptService sysPromptService;

    // 为了实现第一级高速路由，注入数据库查询服务
    @Autowired
    private IDeviceInfoService deviceInfoService;

    // 注入意图分类引擎
    @Autowired
    private IntentClassifier intentClassifier;

    @Autowired
    private ContextBuilder contextBuilder;

    // 预编译正则表达式，匹配类似 "ATM-SN-001" 或 "5G-BS-SB-045" 的工业设备编号
    private static final Pattern DEVICE_CODE_PATTERN = Pattern.compile("([A-Z0-9]+-[A-Z0-9]+-[A-Z0-9]+)");

    public RagServiceImpl(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
    }

    @Override
    public Flux<String> smartChat(String question) {
        System.out.println("====== 接收到用户指令，进入网关分发系统 ======");

        // 拿到排序后的意图列表
        List<IntentClassifier.IntentMatch> matchedIntents = intentClassifier.classify(question);

        // 分级路由
        // 1. 混合意图判定（如果识别出 2 个及以上的意图）
        if (matchedIntents.size() > 1) {
            return handleAgentFallback(question);
        }

        // 2. 单一意图判定
        if (matchedIntents.size() == 1) {
            IntentType mainIntent = matchedIntents.get(0).type();
            if (mainIntent == IntentType.STATUS_QUERY) {
                return handleStatusQuery(question);
            } else if (mainIntent == IntentType.FAULT_RAG) {
                return handleFaultRag(question);
            }
        }

        // 3. 未命中任何意图（低于阈值），走长尾兜底
        return handleAgentFallback(question);
    }

    // =====================================================================
    // 【第一级路由】：正则与规则引擎拦截 (最快、最稳、0 Token成本)
    // 场景：针对明确查询设备状态的高频指令，直接穿透到 MySQL，完全不经过大模型
    // =====================================================================
    private Flux<String> handleStatusQuery(String question) {
        Matcher matcher = DEVICE_CODE_PATTERN.matcher(question);
        if (matcher.find()) {
            String extractCode = matcher.group(1);
            System.out.println(" [路由层] 命中第一级规则拦截，提取到设备编号：" + extractCode);

            LambdaQueryWrapper<DeviceInfo> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(DeviceInfo::getDeviceCode, extractCode);
            DeviceInfo device = deviceInfoService.getOne(wrapper);

            if (device != null) {
                String statusText = device.getStatus() == 1 ? "异常/高温报警" : "运行正常";
                String response = String.format("查询成功：设备 [%s] 当前状态为 %s，实时温度为 %s℃。",
                        device.getDeviceCode(), statusText, device.getTemperature());
                return Flux.just(response);
            }
        }
        // 未匹配到实体，降级给 Agent
        return handleAgentFallback(question);
    }

    // =====================================================================
    // 【第二级路由】：垂直 RAG 专线路由
    // 场景：明确的故障代码或排障求助，直接走向量检索 + 纯文本总结，不挂载任何 Function Calling 工具
    // 优势：减少大模型判断是否需要调用工具的开销，避免乱调 API 产生幻觉
    // =====================================================================
    private Flux<String> handleFaultRag(String question) {
        System.out.println(" [路由层] 命中第二级知识库专线，进入纯净 RAG 模式");
        List<String> contextChunks = searchVectorStore(question);
        String dynamicTemplate = sysPromptService.getPromptContentByCode("device_rag");
        Map<String, Object> params = contextBuilder.build(question, contextChunks);
        return this.chatClient.prompt()
                .system(u -> u.text(dynamicTemplate).params(params))
                // 这里没有 .functions("queryDeviceStatus")
                .stream()
                .content();
    }

    // =====================================================================
    // 【第三级路由】：大模型 Agent 兜底路由 (最耗时，应对复杂与长尾提问)
    // 场景：用户提问极其模糊，例如：“帮我分析一下最近系统有什么异常？”
    // =====================================================================
    private Flux<String> handleAgentFallback(String question) {
        System.out.println(" [路由层] 未命中规则，进入第三级 Agent 兜底模式，交由大模型自行推理");
        List<String> contextChunks = searchVectorStore(question);
        // 3. 从数据库获取纯净的模板 (里面包括 {context} {question})
        String dynamicTemplate = sysPromptService.getPromptContentByCode("device_rag");
        // 4. 使用 ContextBuilder 机制组装参数，并防止长尾对话导致大模型 API 撑爆崩溃
        Map<String, Object> params = contextBuilder.build(question, contextChunks);
        // 5. 发送 Prompt。暂时无奈抛弃 .user()，因为报文会丢失
        return this.chatClient.prompt()
                .system(u -> u.text(dynamicTemplate).params(params))
                // 挂载所有工具，让大模型自主决定
                .functions("queryDeviceStatus")
                .stream()
                .content();
    }

    /**
     * 向量检索逻辑
     * @return 返回严格按照相似度从高到低排序的 Chunk 字符串列表
     */
    private List<String> searchVectorStore(String question) {
        // 1. 向量检索 (TopK 限制)
        List<Document> similarDocuments = vectorStore.similaritySearch(
                SearchRequest.query(question).withTopK(3)
        );

        // 2. 提取上下文：保持独立块结构返回
        return similarDocuments.stream()
                .map(Document::getContent)
                .collect(Collectors.toList());
    }
}