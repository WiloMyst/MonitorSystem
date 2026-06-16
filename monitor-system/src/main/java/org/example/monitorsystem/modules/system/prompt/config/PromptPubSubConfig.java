package org.example.monitorsystem.modules.system.prompt.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Redis Pub/Sub 容器配置
 * 注册 PromptCacheMessageListener 到 RedisMessageListenerContainer，
 * 监听 prompt:update:topic 频道的缓存失效广播消息
 */
@Configuration
public class PromptPubSubConfig {

    @Bean
    public RedisMessageListenerContainer promptRedisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            PromptCacheMessageListener listener) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listener, new ChannelTopic(PromptCacheChannel.TOPIC));
        return container;
    }
}
