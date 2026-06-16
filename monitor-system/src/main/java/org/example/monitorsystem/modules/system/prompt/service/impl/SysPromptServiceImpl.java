package org.example.monitorsystem.modules.system.prompt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.example.monitorsystem.modules.system.prompt.config.PromptCacheMessagePublisher;
import org.example.monitorsystem.modules.system.prompt.entity.SysPrompt;
import org.example.monitorsystem.modules.system.prompt.mapper.SysPromptMapper;
import org.example.monitorsystem.modules.system.prompt.service.ISysPromptService;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 提示词服务实现
 *
 * 三级缓存架构 (L1 → L2 → L3):
 *   L1 Redis:   分布式缓存，管理员修改后清空触发热重载
 *   L2 Caffeine: JVM 本地缓存，Redis 不可用时兜底
 *   L3 MySQL:   持久化存储，缓存全部未命中时回源
 *
 * 防缓存击穿: Redisson 分布式锁 + DCL 双重检查
 * 降级策略:   获取锁超时时走 Caffeine 兜底，再无数据则返回内置通用模板
 *
 * 分布式一致性: Redis Pub/Sub 广播失效
 *   管理员更新/删除提示词时，不仅清空 Redis 和本地 Caffeine，
 *   还向 Redis Channel 发布广播消息，集群各节点监听后主动失效本地缓存
 */
@Service
public class SysPromptServiceImpl implements ISysPromptService {

    private static final Logger log = LoggerFactory.getLogger(SysPromptServiceImpl.class);

    @Autowired
    private SysPromptMapper sysPromptMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private PromptCacheMessagePublisher cacheMessagePublisher;

    @Autowired
    private Cache<String, String> promptLocalCache;

    private static final String PROMPT_CACHE_PREFIX = "sys:prompt:";
    private static final String LOCK_PREFIX = "lock:prompt:rebuild:";

    /**
     * 将 Caffeine 本地缓存注册为 Spring Bean，
     * 以便 PromptCacheMessageListener 注入并执行 invalidate 操作
     */
    @Bean
    public Cache<String, String> promptLocalCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(24, TimeUnit.HOURS)
                .maximumSize(100)
                .build();
    }

    @Override
    public String getPromptContentByCode(String promptCode) {
        String redisKey = PROMPT_CACHE_PREFIX + promptCode;

        // L2: Caffeine 本地缓存（JVM 级，耗时 <1ms）
        String cachedPrompt = promptLocalCache.getIfPresent(redisKey);
        if (cachedPrompt != null) {
            return cachedPrompt;
        }

        // L1: Redis 分布式缓存
        cachedPrompt = stringRedisTemplate.opsForValue().get(redisKey);
        if (cachedPrompt != null) {
            promptLocalCache.put(redisKey, cachedPrompt);
            return cachedPrompt;
        }

        // L3: 回源 MySQL，Redisson DCL 防缓存击穿
        String lockKey = LOCK_PREFIX + promptCode;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            if (lock.tryLock(500, TimeUnit.MILLISECONDS)) {
                try {
                    // DCL: 拿到锁后再查一次 Redis，可能已被其他节点重建
                    cachedPrompt = stringRedisTemplate.opsForValue().get(redisKey);
                    if (cachedPrompt != null) {
                        promptLocalCache.put(redisKey, cachedPrompt);
                        return cachedPrompt;
                    }

                    LambdaQueryWrapper<SysPrompt> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(SysPrompt::getPromptCode, promptCode);
                    SysPrompt sysPrompt = sysPromptMapper.selectOne(wrapper);

                    if (sysPrompt == null) {
                        throw new RuntimeException("系统提示词配置丢失: " + promptCode);
                    }

                    // 回写 L1 + L2
                    stringRedisTemplate.opsForValue().set(redisKey, sysPrompt.getContent(), 24, TimeUnit.HOURS);
                    promptLocalCache.put(redisKey, sysPrompt.getContent());

                    return sysPrompt.getContent();

                } finally {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            } else {
                // 获取锁超时降级：走 Caffeine 兜底
                String fallbackPrompt = promptLocalCache.getIfPresent(redisKey);
                if (fallbackPrompt != null) {
                    return fallbackPrompt;
                }

                return "你是一个专业的工业设备排障AI助手，根据【用户问题】来回答问题。\n\n【知识库上下文】\n{context}\n\n【用户问题】\n{question}";
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("获取提示词被中断", e);
        }
    }

    @Override
    public void refreshPromptCache(String promptCode) {
        String redisKey = PROMPT_CACHE_PREFIX + promptCode;

        // 1. 清空本节点 Redis 缓存
        stringRedisTemplate.delete(redisKey);

        // 2. 清空本节点 Caffeine 本地缓存
        promptLocalCache.invalidate(redisKey);

        // 3. 向集群其他节点广播缓存失效消息（Pub/Sub）
        cacheMessagePublisher.publishInvalidate(promptCode);

        log.info("[提示词缓存] 刷新完成: promptCode={}, 已发布 Pub/Sub 广播", promptCode);
    }
}
