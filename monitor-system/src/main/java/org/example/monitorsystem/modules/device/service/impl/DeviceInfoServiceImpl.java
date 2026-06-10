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
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.example.monitorsystem.core.exception.BusinessException;
import org.example.monitorsystem.core.exception.ErrorCodeEnum;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 设备信息服务实现
 *
 * 大屏首页采用 Redis 缓存 + Redisson 分布式锁防缓存击穿:
 *   1. 首次请求查 Redis，命中则直接返回
 *   2. 未命中则通过分布式锁保证只有一个线程重建缓存 (DCL 双重检查)
 *   3. 获取锁失败则触发限流降级，抛出 BusinessException
 *   4. 非大屏请求直接查数据库
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class DeviceInfoServiceImpl extends ServiceImpl<DeviceInfoMapper, DeviceInfo> implements IDeviceInfoService {

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

        String redisKey = "monitor:device:dashboard:page1";

        if (isDashboard) {
            try {
                String cacheJson = stringRedisTemplate.opsForValue().get(redisKey);
                if (cacheJson != null) {
                    return parseJsonToPage(cacheJson);
                }

                String lockKey = "lock:device:dashboard:rebuild";
                RLock lock = redissonClient.getLock(lockKey);

                if (lock.tryLock(2, TimeUnit.SECONDS)) {
                    try {
                        cacheJson = stringRedisTemplate.opsForValue().get(redisKey);
                        if (cacheJson != null) {
                            return parseJsonToPage(cacheJson);
                        }

                        Page<DeviceVO> voPage = queryFromDatabase(queryDTO);
                        stringRedisTemplate.opsForValue().set(redisKey, toJson(voPage), 30, TimeUnit.SECONDS);
                        return voPage;

                    } finally {
                        if (lock.isHeldByCurrentThread()) {
                            lock.unlock();
                        }
                    }
                } else {
                    throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR);
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                // 缓存异常时降级直连数据库
            }
        }

        return queryFromDatabase(queryDTO);
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