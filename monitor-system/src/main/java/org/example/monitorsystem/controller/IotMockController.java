package org.example.monitorsystem.controller;

import org.example.monitorsystem.common.Result;
import org.example.monitorsystem.common.annotation.Log;
import org.example.monitorsystem.config.RabbitMqConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

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

        rabbitTemplate.convertAndSend(
                RabbitMqConfig.DEVICE_EXCHANGE,
                RabbitMqConfig.ROUTING_KEY,
                payload // Spring 会自动把 Map 序列化成 JSON
        );

        return Result.success("硬件数据已成功推入 MQ 缓冲池");
    }
}