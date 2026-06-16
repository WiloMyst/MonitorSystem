package org.example.monitorsystem.modules.system.prompt.config;

/**
 * 提示词缓存 Pub/Sub 常量定义
 * 用于集群节点间 Caffeine 本地缓存一致性广播
 */
public final class PromptCacheChannel {

    /** Redis Pub/Sub 频道：提示词更新/删除时广播 */
    public static final String TOPIC = "prompt:update:topic";

    /** 消息体前缀：表示需要失效指定 key 的本地缓存 */
    public static final String INVALIDATE_PREFIX = "invalidate:";

    /** 消息体：表示需要清空所有本地缓存 */
    public static final String INVALIDATE_ALL = "invalidate_all";

    private PromptCacheChannel() {}
}
