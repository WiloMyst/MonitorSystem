package org.example.monitorsystem.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    // 1. 定义交换机名称 (路由器)
    public static final String DEVICE_EXCHANGE = "device.direct.exchange";
    // 2. 定义队列名称 (存放温度上报数据的容器)
    public static final String TEMPERATURE_QUEUE = "device.temperature.queue";
    // 3. 定义路由键 (绑定的暗号)
    public static final String ROUTING_KEY = "device.temp.report";

    // 注入 JSON 消息转换器，这样存入 MQ 的就是明文 JSON，再也没有安全拦截和乱码问题
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // 声明队列 (true 表示持久化，重启服务器队列还在)
    @Bean
    public Queue temperatureQueue() {
        return new Queue(TEMPERATURE_QUEUE, true);
    }

    // 声明交换机
    @Bean
    public DirectExchange deviceExchange() {
        return new DirectExchange(DEVICE_EXCHANGE);
    }

    // 将队列和交换机通过 RoutingKey 绑定起来
    @Bean
    public Binding bindingTemperatureQueue() {
        return BindingBuilder.bind(temperatureQueue()).to(deviceExchange()).with(ROUTING_KEY);
    }
}