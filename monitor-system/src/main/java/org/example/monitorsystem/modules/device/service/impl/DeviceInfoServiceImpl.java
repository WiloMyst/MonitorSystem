package org.example.monitorsystem.modules.device.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.monitorsystem.modules.device.model.DeviceQueryDTO;
import org.example.monitorsystem.modules.device.entity.DeviceInfo;
import org.example.monitorsystem.modules.device.mapper.DeviceInfoMapper;
import org.example.monitorsystem.modules.device.model.DeviceVO;
import org.example.monitorsystem.modules.device.service.IDeviceInfoService;
import org.example.monitorsystem.core.exception.BusinessException;
import org.example.monitorsystem.core.exception.ErrorCodeEnum;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 设备信息服务实现
 *
 * 大屏首页采用"旁路预热 + DCL 兜底"双重防缓存击穿策略:
 *
 *   Primary（旁路预热）:
 *     DashboardPrewarmTask 每 5 秒将聚合数据刷入 Redis，
 *     常规高并发下所有请求直接命中 Redis，零锁竞争
 *
 *   Fallback（DCL 兜底）:
 *     仅在极端情况（Redis 宕机重启、缓存大面积失效、预热任务异常）下触发，
 *     Redisson 分布式锁 + DCL 保证只有 1 个线程穿透到数据库，
 *     Watchdog 自动续期防止业务超时锁释放
 *
 *   非大屏请求直接查数据库，不占用缓存资源
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class DeviceInfoServiceImpl extends ServiceImpl<DeviceInfoMapper, DeviceInfo> implements IDeviceInfoService {

    private static final Logger log = LoggerFactory.getLogger(DeviceInfoServiceImpl.class);

    private static final String DASHBOARD_REDIS_KEY = "monitor:device:dashboard:page1";
    private static final String DASHBOARD_LOCK_KEY = "lock:device:dashboard:rebuild";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public Page<DeviceVO> getDevicePage(DeviceQueryDTO queryDTO) {
        boolean isDashboard = (queryDTO.getCurrent() == 1 &&
                !StringUtils.hasText(queryDTO.getKeyword()) &&
                queryDTO.getStatus() == null);

        if (!isDashboard) {
            // 非大屏请求直接查数据库
            return queryFromDatabase(queryDTO);
        }

        // ==================== 大屏首页：旁路预热优先 + DCL 兜底 ====================

        // 第一层：优先读取预热 Redis Key（常规路径，零锁竞争）
        try {
            String cacheJson = stringRedisTemplate.opsForValue().get(DASHBOARD_REDIS_KEY);
            if (cacheJson != null) {
                return parseJsonToPage(cacheJson);
            }
        } catch (Exception e) {
            log.warn("[大屏] Redis 读取异常，降级到 DCL 兜底: {}", e.getMessage());
        }

        // 第二层：DCL 分布式锁兜底（仅在预热缓存失效时触发，如 Redis 宕机重启）
        RLock lock = redissonClient.getLock(DASHBOARD_LOCK_KEY);

        try {
            // tryLock: 等待 2 秒获取锁，Watchdog 自动续期（默认 30 秒）
            if (lock.tryLock(2, TimeUnit.SECONDS)) {
                try {
                    // DCL: 拿到锁后再查一次 Redis，可能已被预热任务或其他节点重建
                    String cacheJson = stringRedisTemplate.opsForValue().get(DASHBOARD_REDIS_KEY);
                    if (cacheJson != null) {
                        return parseJsonToPage(cacheJson);
                    }

                    // 缓存确实不存在，唯一线程穿透到数据库
                    log.warn("[大屏] 预热缓存未命中，DCL 兜底查库重建");
                    Page<DeviceVO> voPage = queryFromDatabase(queryDTO);
                    stringRedisTemplate.opsForValue().set(DASHBOARD_REDIS_KEY, toJson(voPage), 30, TimeUnit.SECONDS);
                    return voPage;

                } finally {
                    // 安全释放：只释放当前线程持有的锁，避免误删其他线程的锁
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            } else {
                // 获取锁超时：说明已有其他线程在重建缓存，限流降级
                log.warn("[大屏] DCL 获取锁超时，触发限流降级");
                throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // Redis 异常时降级直连数据库
            log.error("[大屏] 缓存异常，降级直连数据库: {}", e.getMessage());
            return queryFromDatabase(queryDTO);
        }
    }

    /**
     * 查库并转换为 VO
     */
    private Page<DeviceVO> queryFromDatabase(DeviceQueryDTO queryDTO) {
        Page<DeviceInfo> pageParam = new Page<>(queryDTO.getCurrent(), queryDTO.getSize());
        LambdaQueryWrapper<DeviceInfo> wrapper = new LambdaQueryWrapper<>();

        wrapper.like(StringUtils.hasText(queryDTO.getKeyword()), DeviceInfo::getDeviceCode, queryDTO.getKeyword())
                .eq(queryDTO.getStatus() != null, DeviceInfo::getStatus, queryDTO.getStatus())
                .orderByDesc(DeviceInfo::getUpdateTime);

        Page<DeviceInfo> entityPage = this.page(pageParam, wrapper);

        List<DeviceVO> voList = entityPage.getRecords().stream().map(entity -> {
            DeviceVO vo = new DeviceVO();
            BeanUtils.copyProperties(entity, vo);
            if (entity.getUpdateTime() != null) {
                DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                vo.setLastUpdateTime(dtf.format(entity.getUpdateTime()));
            }
            return vo;
        }).collect(Collectors.toList());

        Page<DeviceVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(voList);

        return voPage;
    }

    private Page<DeviceVO> parseJsonToPage(String json) throws JsonProcessingException {
        return objectMapper.readValue(json, new TypeReference<Page<DeviceVO>>() {});
    }

    private String toJson(Page<DeviceVO> page) throws JsonProcessingException {
        return objectMapper.writeValueAsString(page);
    }
}
