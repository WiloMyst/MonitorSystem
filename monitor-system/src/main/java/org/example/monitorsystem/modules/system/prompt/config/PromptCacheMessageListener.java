package org.example.monitorsystem.modules.system.prompt.config;

import com.github.benmanes.caffeine.cache.Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.stereotype.Component;

/**
 * 提示词缓存消息监听器
 *
 * 订阅 Redis Channel (prompt:update:topic)，当收到其他节点发布的缓存失效消息时，
 * 主动失效本节点的 Caffeine 本地缓存，保证分布式集群的最终一致性。
 *
 * 注意: 本监听器通过 RedisMessageListenerContainer 注册，
 *       不处理自己发布的消息（因为发布方已在本地直接清除了缓存）
 */
@Component
public class PromptCacheMessageListener extends MessageListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(PromptCacheMessageListener.class);

    private final Cache<String, String> localCache;

    public PromptCacheMessageListener(Cache<String, String> promptLocalCache) {
        this.localCache = promptLocalCache;
    }

    /**
     * Redis 消息回调：收到广播后失效本地 Caffeine 缓存
     *
     * 线程模型说明:
     *   Redis 监听器回调在 Redis 连接池的 I/O 线程中执行，
     *   Caffeine.invalidate() 是 O(1) 操作且无阻塞 I/O，
     *   因此直接在回调线程中执行，无需额外线程池切换
     */
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody());
        String channel = new String(message.getChannel());

        log.info("[Pub/Sub] 收到缓存失效广播: channel={}, body={}", channel, body);

        if (PromptCacheChannel.INVALIDATE_ALL.equals(body)) {
            // 全量失效
            localCache.invalidateAll();
            log.info("[Pub/Sub] 本地 Caffeine 缓存已全量失效");
        } else if (body.startsWith(PromptCacheChannel.INVALIDATE_PREFIX)) {
            // 指定 key 失效
            String promptCode = body.substring(PromptCacheChannel.INVALIDATE_PREFIX.length());
            String cacheKey = "sys:prompt:" + promptCode;
            localCache.invalidate(cacheKey);
            log.info("[Pub/Sub] 本地 Caffeine 缓存已失效: key={}", cacheKey);
        }
    }
}
