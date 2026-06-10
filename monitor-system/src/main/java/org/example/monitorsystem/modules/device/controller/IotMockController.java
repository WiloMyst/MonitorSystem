package org.example.monitorsystem.modules.device.controller;

import org.example.monitorsystem.core.web.Result;
import org.example.monitorsystem.modules.system.log.aop.annotation.Log;
import org.example.monitorsystem.modules.device.config.RabbitMqConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/**
 * IoT 模拟数据上报控制器
 * 模拟硬件网关向 RabbitMQ 上报设备温度、振动等指标数据，
 * 用于开发和测试阶段验证数据链路。
 */
@CrossOrigin
@RestController
@RequestMapping("/api/iot")
public class IotMockController {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    // 模拟硬件网关上报设备温度
    @Log(title = "设备数据上报", businessType = "INSERT")
    @PostMapping("/report")
    public Result<String> mockHardwareReport(@RequestBody Map<String, Object> payload) {
        // 在实际工业中，硬件网关并发量极大，这里绝对不能写数据库！
        // 做法：直接把硬件报上来的 JSON 扔进 RabbitMQ，立马返回成功，让硬件断开连接。

        // 1. 提取设备类型（假设硬件传了，没传可以默认为 unknown）
        // payload 里面应该加一个 deviceType 字段，比如 "atm" 或 "server"
        String deviceType = (String) payload.getOrDefault("deviceType", "default");

        // 2. 动态拼装 Routing Key
        // 生成的格式如： "device.temp.atm" 或 "device.temp.server"
        String dynamicRoutingKey = "device.temp." + deviceType;

        // 3. 发送给 Topic 交换机
        rabbitTemplate.convertAndSend(
                RabbitMqConfig.DEVICE_EXCHANGE, // 交换机常量
                dynamicRoutingKey,              // 动态生成的精确路由键
                payload
        );

        return Result.success("硬件数据已成功推入 MQ 缓冲池");
    }
}