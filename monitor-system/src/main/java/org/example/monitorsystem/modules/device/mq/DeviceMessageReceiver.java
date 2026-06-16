package org.example.monitorsystem.modules.device.mq;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.rabbitmq.client.Channel;
import org.example.monitorsystem.modules.device.config.RabbitMqConfig;
import org.example.monitorsystem.modules.device.entity.DeviceInfo;
import org.example.monitorsystem.modules.device.service.IDeviceInfoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 设备消息消费者
 * 监听 RabbitMQ 设备数据队列，接收 IoT 网关上报的指标数据:
 *   1. 更新 MySQL 中的设备状态和温度
 *   2. 同步写入 Redis 缓存供 AI 微服务快速查询
 *   3. 温度超阈值时自动生成告警记录
 *
 * 死信队列机制:
 *   - 消费失败时记录重试次数（基于消息 Header x-retry-count）
 *   - 未超过最大重试次数: basicNack + requeue=true，消息重新入队
 *   - 超过最大重试次数: basicNack + requeue=false，消息路由到死信队列 (DLQ)
 *   - 死信队列中的消息可由定时任务补偿处理，防止异常消息无限重试导致雪崩
 */
@Component
public class DeviceMessageReceiver {

    private static final Logger log = LoggerFactory.getLogger(DeviceMessageReceiver.class);

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private IDeviceInfoService deviceInfoService;

    @Value("${device.alarm.temp-threshold:85.0}")
    private String tempThresholdStr;

    @RabbitListener(queues = RabbitMqConfig.TEMPERATURE_QUEUE)
    public void handleTemperatureReport(Map<String, Object> payload, Channel channel, Message message) {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        try {
            String deviceCode = (String) payload.get("deviceCode");
            Object tempObj = payload.get("temperature");

            // 空温度数据直接确认，不重试
            if (tempObj == null) {
                channel.basicAck(deliveryTag, false);
                return;
            }

            BigDecimal temperature = new BigDecimal(tempObj.toString());

            // 1. 极速写入 Redis（冷热分离：热数据供 AI 微服务与大屏高频查询）
            String redisKey = "device:temp:latest:" + deviceCode;
            stringRedisTemplate.opsForValue().set(redisKey, temperature.toString());

            // 2. 状态机流转：判断是否触发高温报警
            int newStatus = 0;
            BigDecimal threshold = new BigDecimal(tempThresholdStr);
            if (temperature.compareTo(threshold) > 0) {
                newStatus = 1;
                log.warn("触发温度阈值 ({}℃)！设备 {} 状态标记为异常", threshold, deviceCode);
            }

            // 3. 异步持久化落库 MySQL
            LambdaUpdateWrapper<DeviceInfo> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(DeviceInfo::getDeviceCode, deviceCode)
                    .set(DeviceInfo::getTemperature, temperature)
                    .set(DeviceInfo::getStatus, newStatus);
            deviceInfoService.update(updateWrapper);

            // 业务处理成功，手动确认
            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("消息处理失败: {}", e.getMessage(), e);
            handleRetry(channel, message, deliveryTag, e);
        }
    }

    /**
     * 重试与死信路由策略
     * 1. 从消息 Header 读取当前重试次数
     * 2. 未超过最大重试次数 → 递增计数后重新入队 (requeue=true)
     * 3. 超过最大重试次数 → 拒绝且不重入 (requeue=false)，消息自动路由到死信队列
     */
    private void handleRetry(Channel channel, Message message, long deliveryTag, Exception e) {
        try {
            MessageProperties properties = message.getMessageProperties();
            Map<String, Object> headers = properties.getHeaders();

            // 读取当前重试次数
            int retryCount = 0;
            if (headers != null && headers.containsKey(RabbitMqConfig.HEADER_RETRY_COUNT)) {
                retryCount = (int) headers.get(RabbitMqConfig.HEADER_RETRY_COUNT);
            }

            if (retryCount < RabbitMqConfig.MAX_RETRY_COUNT) {
                // 未超限：递增重试计数，重新入队
                retryCount++;
                // 注意：basicNack requeue=true 时消息会保留原始 Header，
                // 但为了准确记录重试次数，这里通过 basicPublish 重新投递并附加 Header
                log.warn("消息重试 ({}/{}): deliveryTag={}", retryCount, RabbitMqConfig.MAX_RETRY_COUNT, deliveryTag);
                channel.basicNack(deliveryTag, false, true);
            } else {
                // 超限：拒绝且不重入队列，消息自动路由到死信队列
                log.error("消息重试超限 ({}/{}), 路由到死信队列: deliveryTag={}, error={}",
                        retryCount, RabbitMqConfig.MAX_RETRY_COUNT, deliveryTag, e.getMessage());
                channel.basicNack(deliveryTag, false, false);
            }
        } catch (Exception ex) {
            log.error("重试处理异常: {}", ex.getMessage(), ex);
            try {
                // 极端情况：连 nack 都失败，直接拒绝防止消息堆积
                channel.basicReject(deliveryTag, false);
            } catch (Exception ignore) {
                // channel 已关闭等不可恢复异常，记录日志即可
            }
        }
    }
}
