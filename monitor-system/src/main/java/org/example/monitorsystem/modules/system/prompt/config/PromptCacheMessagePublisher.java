package org.example.monitorsystem.modules.system.prompt.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 提示词缓存消息发布者
 *
 * 在管理员更新/删除提示词时，向 Redis Channel 发布广播消息，
 * 集群中所有订阅该 Channel 的节点收到消息后主动失效本地 Caffeine 缓存，
 * 从而解决分布式集群下本地缓存脏数据问题，保障全局最终一致性。
 *
 * 一致性模型: 最终一致性 —— 管理员操作后，集群各节点在毫秒级内完成本地缓存失效
 */
@Component
public class PromptCacheMessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(PromptCacheMessagePublisher.class);

    private final StringRedisTemplate stringRedisTemplate;

    public PromptCacheMessagePublisher(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 发布指定 key 的缓存失效消息
     * @param promptCode 提示词编码
     */
    public void publishInvalidate(String promptCode) {
        String message = PromptCacheChannel.INVALIDATE_PREFIX + promptCode;
        stringRedisTemplate.convertAndSend(PromptCacheChannel.TOPIC, message);
        log.info("[Pub/Sub] 发布缓存失效广播: channel={}, message={}", PromptCacheChannel.TOPIC, message);
    }

    /**
     * 发布全量缓存失效消息（如批量更新场景）
     */
    public void publishInvalidateAll() {
        stringRedisTemplate.convertAndSend(PromptCacheChannel.TOPIC, PromptCacheChannel.INVALIDATE_ALL);
        log.info("[Pub/Sub] 发布全量缓存失效广播: channel={}", PromptCacheChannel.TOPIC);
    }
}
