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

@Service
public class SysPromptServiceImpl implements ISysPromptService {

    @Autowired
    private SysPromptMapper sysPromptMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedissonClient redissonClient; // 引入强大的分布式锁

    // 1. 初始化 Caffeine JVM 本地缓存 (作为 L2 兜底缓存)
    // 设一个比较长的过期时间，因为我们的策略是靠 Redis 热更新来覆盖它
    private final Cache<String, String> localCache = Caffeine.newBuilder()
            .expireAfterWrite(24, TimeUnit.HOURS)
            .maximumSize(100) // 提示词数量不多，100条绝对够了
            .build();

    // Redis 缓存的 Key 前缀
    private static final String PROMPT_CACHE_PREFIX = "sys:prompt:";
    private static final String LOCK_PREFIX = "lock:prompt:rebuild:";

    /**
     * 根据 Code 获取动态提示词（优先查 Redis，没有再查 MySQL 并写入 Redis）
     */
    @Override
    public String getPromptContentByCode(String promptCode) {
        String redisKey = PROMPT_CACHE_PREFIX + promptCode;

        // 2. 常规提交流程：尝试从 Redis 获取最新数据
        String cachedPrompt = stringRedisTemplate.opsForValue().get(redisKey);
        if (cachedPrompt != null) {
            // 每次从 Redis 拿到最新数据，都顺手同步一份给本地 Caffeine 留作备胎
            localCache.put(redisKey, cachedPrompt);
            return cachedPrompt;
        }

        // 3. Redis 没命中（说明被管理员清空触发热重载了） -> 准备抢锁防击穿
        String lockKey = LOCK_PREFIX + promptCode;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 4. 尝试获取分布式锁，最多只等 500 毫秒！
            if (lock.tryLock(500, TimeUnit.MILLISECONDS)) {
                try {
                    System.out.println("====== 拿到 Prompt 重建锁：" + promptCode + " ======");

                    // 5. DCL 双重检查 (Double-Check Lock)，防止排队的线程重复查库
                    cachedPrompt = stringRedisTemplate.opsForValue().get(redisKey);
                    if (cachedPrompt != null) {
                        return cachedPrompt;
                    }

                    // 6. 穿透到 MySQL 查询最新配置
                    LambdaQueryWrapper<SysPrompt> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(SysPrompt::getPromptCode, promptCode);
                    SysPrompt sysPrompt = sysPromptMapper.selectOne(wrapper);

                    if (sysPrompt == null) {
                        throw new RuntimeException("系统提示词配置丢失: " + promptCode);
                    }

                    // 7. 重建 Redis，并更新 Caffeine 缓存
                    stringRedisTemplate.opsForValue().set(redisKey, sysPrompt.getContent(), 24, TimeUnit.HOURS);
                    localCache.put(redisKey, sysPrompt.getContent());

                    return sysPrompt.getContent();

                } finally {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                        System.out.println("====== 释放 Prompt 重建锁：" + promptCode + " ======");
                    }
                }
            } else {
                // =====================================================================
                // 8. 【降级逻辑】：没抢到锁的 999 个高并发线程，绝不死等，立刻走兜底方案！
                // =====================================================================
                System.out.println("====== 并发极高，获取锁超时，触发 Caffeine 本地兜底机制 ======");

                // 兜底方案 A：去本地 Caffeine 找历史旧数据顶一阵子
                String fallbackPrompt = localCache.getIfPresent(redisKey);
                if (fallbackPrompt != null) {
                    System.out.println("====== 使用 Caffeine 旧版本 Prompt 成功兜底，用户无感 ======");
                    return fallbackPrompt;
                }

                // 兜底方案 B：如果连本地缓存都没有（比如刚启动系统就被打满并发），返回硬编码的终极兜底模板
                System.out.println("====== 使用系统内置通用 Prompt 兜底 ======");
                return "你是一个专业的工业设备排障AI助手，根据【用户问题】来回答问题。\n\n【知识库上下文】\n{context}\n\n【用户问题】\n{question}";
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("获取提示词被中断", e);
        }
    }

    /**
     * 【热重载机制】：当后台管理员修改了数据库里的提示词后，调用此方法清空 Redis 缓存
     */
    @Override
    public void refreshPromptCache(String promptCode) {
        String redisKey = PROMPT_CACHE_PREFIX + promptCode;
        stringRedisTemplate.delete(redisKey);
        System.out.println("提示词 [" + promptCode + "] 的 Redis 缓存已清空，下次调用将热加载最新配置！");
    }
}