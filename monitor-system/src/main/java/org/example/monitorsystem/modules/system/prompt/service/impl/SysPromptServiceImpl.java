package org.example.monitorsystem.modules.system.prompt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.example.monitorsystem.modules.system.prompt.entity.SysPrompt;
import org.example.monitorsystem.modules.system.prompt.mapper.SysPromptMapper;
import org.example.monitorsystem.modules.system.prompt.service.ISysPromptService;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
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
 */
@Service
public class SysPromptServiceImpl implements ISysPromptService {

    @Autowired
    private SysPromptMapper sysPromptMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    private final Cache<String, String> localCache = Caffeine.newBuilder()
            .expireAfterWrite(24, TimeUnit.HOURS)
            .maximumSize(100)
            .build();

    private static final String PROMPT_CACHE_PREFIX = "sys:prompt:";
    private static final String LOCK_PREFIX = "lock:prompt:rebuild:";

    @Override
    public String getPromptContentByCode(String promptCode) {
        String redisKey = PROMPT_CACHE_PREFIX + promptCode;

        String cachedPrompt = stringRedisTemplate.opsForValue().get(redisKey);
        if (cachedPrompt != null) {
            localCache.put(redisKey, cachedPrompt);
            return cachedPrompt;
        }

        String lockKey = LOCK_PREFIX + promptCode;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            if (lock.tryLock(500, TimeUnit.MILLISECONDS)) {
                try {
                    cachedPrompt = stringRedisTemplate.opsForValue().get(redisKey);
                    if (cachedPrompt != null) {
                        return cachedPrompt;
                    }

                    LambdaQueryWrapper<SysPrompt> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(SysPrompt::getPromptCode, promptCode);
                    SysPrompt sysPrompt = sysPromptMapper.selectOne(wrapper);

                    if (sysPrompt == null) {
                        throw new RuntimeException("系统提示词配置丢失: " + promptCode);
                    }

                    stringRedisTemplate.opsForValue().set(redisKey, sysPrompt.getContent(), 24, TimeUnit.HOURS);
                    localCache.put(redisKey, sysPrompt.getContent());

                    return sysPrompt.getContent();

                } finally {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            } else {
                String fallbackPrompt = localCache.getIfPresent(redisKey);
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
        stringRedisTemplate.delete(redisKey);
    }
}