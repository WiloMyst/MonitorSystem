package org.example.monitorsystem.modules.device.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ 配置类
 * 定义设备数据上报的消息队列拓扑，并引入死信队列机制防止异常消息无限重试:
 *
 *   业务队列: device.temperature.queue (持久化, 绑定 DLX)
 *     └─ Exchange: device.topic.exchange (Topic 类型)
 *     └─ BindingKey: device.temp.#
 *
 *   死信队列: device.temperature.dlq (持久化)
 *     └─ Exchange: device.dlx.exchange (Direct 类型)
 *     └─ RoutingKey: device.temperature.dlq (与队列同名)
 *
 *   消息流转: 业务队列消费失败超限 → 消息进入 DLX → 死信队列 → 定时任务补偿处理
 */
@Configuration
public class RabbitMqConfig {

    // ==================== 业务队列 ====================
    public static final String DEVICE_EXCHANGE = "device.topic.exchange";
    public static final String TEMPERATURE_QUEUE = "device.temperature.queue";
    public static final String BINDING_KEY = "device.temp.#";

    // ==================== 死信队列 ====================
    public static final String DLX_EXCHANGE = "device.dlx.exchange";
    public static final String DLQ_QUEUE = "device.temperature.dlq";
    public static final String DLQ_ROUTING_KEY = "device.temperature.dlq";

    /** 最大重试次数，超过后消息进入死信队列 */
    public static final int MAX_RETRY_COUNT = 3;

    /** 消息 Header 中记录重试次数的 Key */
    public static final String HEADER_RETRY_COUNT = "x-retry-count";

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // ==================== 业务队列定义 ====================

    /**
     * 业务队列：绑定死信交换机，当消息被拒绝且 requeue=false 时自动路由到死信队列
     */
    @Bean
    public Queue temperatureQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", DLX_EXCHANGE);
        args.put("x-dead-letter-routing-key", DLQ_ROUTING_KEY);
        return new Queue(TEMPERATURE_QUEUE, true, false, false, args);
    }

    @Bean
    public TopicExchange deviceExchange() {
        return new TopicExchange(DEVICE_EXCHANGE);
    }

    @Bean
    public Binding bindingTemperatureQueue() {
        return BindingBuilder.bind(temperatureQueue()).to(deviceExchange()).with(BINDING_KEY);
    }

    // ==================== 死信队列定义 ====================

    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue dlqQueue() {
        return new Queue(DLQ_QUEUE, true);
    }

    @Bean
    public Binding bindingDlq() {
        return BindingBuilder.bind(dlqQueue()).to(dlxExchange()).with(DLQ_ROUTING_KEY);
    }
}
