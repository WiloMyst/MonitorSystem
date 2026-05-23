package org.example.monitorsystem.modules.system.prompt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.monitorsystem.modules.system.prompt.entity.SysPrompt;
import org.example.monitorsystem.modules.system.prompt.mapper.SysPromptMapper;
import org.example.monitorsystem.modules.system.prompt.service.ISysPromptService;
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

    // Redis 缓存的 Key 前缀
    private static final String PROMPT_CACHE_PREFIX = "sys:prompt:";

    /**
     * 根据 Code 获取动态提示词（优先查 Redis，没有再查 MySQL 并写入 Redis）
     */
    @Override
    public String getPromptContentByCode(String promptCode) {
        String redisKey = PROMPT_CACHE_PREFIX + promptCode;

        // 1. 查 Redis 缓存
        String cachedPrompt = stringRedisTemplate.opsForValue().get(redisKey);
        if (cachedPrompt != null) {
            return cachedPrompt; // 命中缓存，直接起飞
        }

        // 2. 缓存没命中，查 MySQL
        LambdaQueryWrapper<SysPrompt> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysPrompt::getPromptCode, promptCode);
        SysPrompt sysPrompt = sysPromptMapper.selectOne(wrapper);

        if (sysPrompt == null) {
            throw new RuntimeException("系统提示词配置丢失: " + promptCode);
        }

        // 3. 写入 Redis 缓存 (设置个比较长的过期时间，比如 24 小时)
        stringRedisTemplate.opsForValue().set(redisKey, sysPrompt.getContent(), 24, TimeUnit.HOURS);

        return sysPrompt.getContent();
    }

    /**
     * 【热更新机制】：当后台管理员修改了数据库里的提示词后，调用此方法清空 Redis 缓存
     */
    @Override
    public void refreshPromptCache(String promptCode) {
        String redisKey = PROMPT_CACHE_PREFIX + promptCode;
        stringRedisTemplate.delete(redisKey);
        System.out.println("提示词 [" + promptCode + "] 的 Redis 缓存已清空，下次调用将热加载最新配置！");
    }
}