package org.example.monitorsystem.modules.device.service;

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

@Service
@Transactional(rollbackFor = Exception.class) // 发生任何异常全部回滚
public class DeviceInfoServiceImpl extends ServiceImpl<DeviceInfoMapper, DeviceInfo> implements IDeviceInfoService {

    // 注入 Redis 操作模板
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // 注入强大的 Redisson 客户端
    @Autowired
    private RedissonClient redissonClient;

    // 注入 JSON 转换工具
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
                // 1. 第一次尝试从 Redis 获取
                String cacheJson = stringRedisTemplate.opsForValue().get(redisKey);
                if (cacheJson != null) {
                    return parseJsonToPage(cacheJson);
                }

                // ================= 核心改造：Redisson 分布式锁 =================
                // 2. 定义这把锁的名字 (企业规范：lock:业务名:具体标识)
                String lockKey = "lock:device:dashboard:rebuild";
                RLock lock = redissonClient.getLock(lockKey);

                // 3. 尝试获取锁
                // 参数1：等待时间 (2秒内拿不到锁就放弃)
                // 参数2：不传！(不传默认会开启看门狗机制，自动续期)
                // 参数3：时间单位
                if (lock.tryLock(2, TimeUnit.SECONDS)) {
                    try {
                        System.out.println("====== 🚀 拿到分布式锁，准备重建大屏缓存 ======");

                        // 拿到锁后，必须再查一次缓存 (DCL: Double Check Lock)
                        cacheJson = stringRedisTemplate.opsForValue().get(redisKey);
                        if (cacheJson != null) {
                            return parseJsonToPage(cacheJson);
                        }

                        // 穿透到 MySQL 查询
                        Page<DeviceVO> voPage = queryFromDatabase(queryDTO);

                        // 写入 Redis，设置 30 秒过期
                        stringRedisTemplate.opsForValue().set(redisKey, toJson(voPage), 30, TimeUnit.SECONDS);
                        return voPage;

                    } finally {
                        // 4. 极其关键的释放锁逻辑：必须放在 finally 里！
                        // 并且要判断这把锁当前是不是当前线程持有的（防止误删别人的锁）
                        if (lock.isHeldByCurrentThread()) {
                            lock.unlock();
                            System.out.println("====== 🔓 缓存重建完毕，释放分布式锁 ======");
                        }
                    }
                } else {
                    // 5. 没拿到锁的线程走这里
                    // 说明有另一个节点正在疯狂读 MySQL 重建缓存，这时候我们没必要再去排队了
                    // 直接抛出一个业务异常，前端拦截到后会提示用户，或者快速失败。
                    System.out.println("====== ✋ 没拿到锁，触发限流降级 ======");
                    throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR);
                }
                // ==============================================================

            } catch (InterruptedException e) {
                // 处理线程中断异常
                Thread.currentThread().interrupt();
                throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR);
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                System.err.println("❌ 缓存处理异常，降级直连数据库：" + e.getMessage());
            }
        }

        // 非大屏请求，直接查数据库
        return queryFromDatabase(queryDTO);
    }

    /**
     * 【企业级优化 2】：抽取出的私有方法
     * 纯粹的查库与 VO 转换逻辑，保持主流程的整洁
     */
    private Page<DeviceVO> queryFromDatabase(DeviceQueryDTO queryDTO) {
        Page<DeviceInfo> pageParam = new Page<>(queryDTO.getCurrent(), queryDTO.getSize());
        LambdaQueryWrapper<DeviceInfo> wrapper = new LambdaQueryWrapper<>();

        // 【企业级优化 3】：MyBatis-Plus 的优雅条件构造，告别臃肿的 if 语句嵌套
        wrapper.like(StringUtils.hasText(queryDTO.getKeyword()), DeviceInfo::getDeviceCode, queryDTO.getKeyword())
                .eq(queryDTO.getStatus() != null, DeviceInfo::getStatus, queryDTO.getStatus())
                .orderByDesc(DeviceInfo::getUpdateTime);

        // 执行分页查询
        Page<DeviceInfo> entityPage = this.page(pageParam, wrapper);

        // 实体类转 VO
        List<DeviceVO> voList = entityPage.getRecords().stream().map(entity -> {
            DeviceVO vo = new DeviceVO();
            BeanUtils.copyProperties(entity, vo);
            if (entity.getUpdateTime() != null) {
                DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                vo.setLastUpdateTime(dtf.format(entity.getUpdateTime()));
            }
            return vo;
        }).collect(Collectors.toList());

        // 组装新的分页返回对象
        Page<DeviceVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(voList);

        return voPage;
    }

    /**
     * 工具方法：JSON 转 Page 对象
     */
    private Page<DeviceVO> parseJsonToPage(String json) throws JsonProcessingException {
        return objectMapper.readValue(json, new TypeReference<Page<DeviceVO>>() {});
    }

    /**
     * 工具方法：Page 对象转 JSON
     */
    private String toJson(Page<DeviceVO> page) throws JsonProcessingException {
        return objectMapper.writeValueAsString(page);
    }
}