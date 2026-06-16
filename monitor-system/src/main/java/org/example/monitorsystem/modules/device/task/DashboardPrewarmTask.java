package org.example.monitorsystem.modules.device.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.monitorsystem.modules.device.entity.DeviceInfo;
import org.example.monitorsystem.modules.device.mapper.DeviceInfoMapper;
import org.example.monitorsystem.modules.device.model.DeviceQueryDTO;
import org.example.monitorsystem.modules.device.model.DeviceVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 大屏监控数据旁路定时预热任务
 *
 * 设计思路:
 *   监控大屏首页是极高频读场景（前端轮询间隔 3~5 秒），纯靠 Redisson DCL 防缓存击穿
 *   在缓存失效瞬间仍会导致大量线程阻塞等锁。因此采用"旁路预热 + DCL 兜底"双重策略:
 *
 *   Primary（定时预热）: 每隔 5 秒主动将聚合好的大屏数据刷入 Redis，
 *                        常规高并发下所有请求直接命中 Redis，零锁竞争
 *   Fallback（DCL 兜底）: 仅在极端情况（Redis 宕机重启、缓存大面积失效）下触发，
 *                         Redisson 分布式锁 + DCL 保证只有 1 个线程穿透到数据库
 *
 * 线程模型:
 *   @Scheduled 默认在 Spring 的 scheduling 线程池中执行，不占用 Tomcat 工作线程
 *   预热操作为纯 I/O（查库 + 写 Redis），无计算密集型逻辑，单线程即可胜任
 */
@Component
public class DashboardPrewarmTask {

    private static final Logger log = LoggerFactory.getLogger(DashboardPrewarmTask.class);

    private static final String DASHBOARD_REDIS_KEY = "monitor:device:dashboard:page1";
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private DeviceInfoMapper deviceInfoMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 定时预热大屏首页数据
     * 固定间隔 5 秒执行，将大屏首页聚合数据主动刷入 Redis
     */
    @Scheduled(fixedRate = 5000)
    public void prewarmDashboard() {
        try {
            // 构造大屏首页查询条件（第一页、无关键词、无状态过滤）
            DeviceQueryDTO queryDTO = new DeviceQueryDTO();
            queryDTO.setCurrent(1);
            queryDTO.setSize(10);

            Page<DeviceInfo> pageParam = new Page<>(queryDTO.getCurrent(), queryDTO.getSize());
            LambdaQueryWrapper<DeviceInfo> wrapper = new LambdaQueryWrapper<>();
            wrapper.orderByDesc(DeviceInfo::getUpdateTime);

            Page<DeviceInfo> entityPage = deviceInfoMapper.selectPage(pageParam, wrapper);

            List<DeviceVO> voList = entityPage.getRecords().stream().map(entity -> {
                DeviceVO vo = new DeviceVO();
                BeanUtils.copyProperties(entity, vo);
                if (entity.getUpdateTime() != null) {
                    vo.setLastUpdateTime(DTF.format(entity.getUpdateTime()));
                }
                return vo;
            }).collect(Collectors.toList());

            Page<DeviceVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
            voPage.setRecords(voList);

            String json = objectMapper.writeValueAsString(voPage);
            // 预热写入 Redis，TTL 设为 10 秒（略大于定时间隔 5 秒，确保预热间隙仍有缓存可用）
            stringRedisTemplate.opsForValue().set(DASHBOARD_REDIS_KEY, json, 10, TimeUnit.SECONDS);

            log.debug("[预热] 大屏首页数据已刷入 Redis: key={}, records={}", DASHBOARD_REDIS_KEY, voList.size());
        } catch (Exception e) {
            log.error("[预热] 大屏首页数据预热失败: {}", e.getMessage(), e);
            // 预热失败不影响业务，DCL 兜底机制会接管
        }
    }
}
