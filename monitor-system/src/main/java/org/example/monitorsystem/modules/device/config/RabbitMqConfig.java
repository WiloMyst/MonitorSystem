package org.example.monitorsystem.modules.device.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置类
 * 定义设备数据上报的消息队列拓扑:
 *   Exchange: device.topic.exchange (Topic 类型)
 *   Queue: device.temperature.queue (持久化)
 *   BindingKey: device.temp.# (匹配所有 device.temp 开头的路由键)
 */
@Configuration
public class RabbitMqConfig {

    public static final String DEVICE_EXCHANGE = "device.topic.exchange";
    public static final String TEMPERATURE_QUEUE = "device.temperature.queue";
    public static final String BINDING_KEY = "device.temp.#";

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public Queue temperatureQueue() {
        return new Queue(TEMPERATURE_QUEUE, true);
    }

    @Bean
    public TopicExchange deviceExchange() {
        return new TopicExchange(DEVICE_EXCHANGE);
    }

    @Bean
    public Binding bindingTemperatureQueue() {
        return BindingBuilder.bind(temperatureQueue()).to(deviceExchange()).with(BINDING_KEY);
    }
}