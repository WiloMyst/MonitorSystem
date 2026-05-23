package org.example.monitorsystem.modules.device.mq;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.rabbitmq.client.Channel;
import org.example.monitorsystem.modules.device.config.RabbitMqConfig;
import org.example.monitorsystem.modules.device.entity.DeviceInfo;
import org.example.monitorsystem.modules.device.service.IDeviceInfoService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class DeviceMessageReceiver {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private IDeviceInfoService deviceInfoService;

    // 从配置文件动态读取阈值。"85.0" 是默认值
    @Value("${device.alarm.temp-threshold:85.0}")
    private String tempThresholdStr;

    @RabbitListener(queues = RabbitMqConfig.TEMPERATURE_QUEUE)
    public void handleTemperatureReport(Map<String, Object> payload, Channel channel, Message message) {
        try {
            String deviceCode = (String) payload.get("deviceCode");
            Object tempObj = payload.get("temperature");
            if (tempObj == null) {
                channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
                return;
            }
            BigDecimal temperature = new BigDecimal(tempObj.toString());

            // 1. 极速写入 Redis
            String redisKey = "device:temp:latest:" + deviceCode;
            stringRedisTemplate.opsForValue().set(redisKey, temperature.toString());

            // 2. 状态机流转：判断是否触发高温报警
            int newStatus = 0;

            // 使用注入进来的配置变量进行比较
            BigDecimal threshold = new BigDecimal(tempThresholdStr);
            if (temperature.compareTo(threshold) > 0) {
                newStatus = 1;
                System.out.println("⚠️ 触发阈值 (" + threshold + "℃)！设备 " + deviceCode + " 状态已被标记为异常！");
            }

            // 3. 异步持久化落库 MySQL
            LambdaUpdateWrapper<DeviceInfo> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(DeviceInfo::getDeviceCode, deviceCode)
                    .set(DeviceInfo::getTemperature, temperature)
                    .set(DeviceInfo::getStatus, newStatus);

            deviceInfoService.update(updateWrapper);

            // 手动确认 (ACK)
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);

        } catch (Exception e) {
            System.err.println("❌ 消息处理失败：" + e.getMessage());
            try {
                channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }
}