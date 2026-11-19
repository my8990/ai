package com.example.ai.mq;

import com.example.ai.websocket.RedisBroadcastBus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "app.live.broadcast.mode", havingValue = "redis")
public class LiveBroadcastRedisConfig {

    @Bean
    public RedisMessageListenerContainer liveBroadcastListenerContainer(
            RedisConnectionFactory connectionFactory,
            RedisBroadcastBus redisBroadcastBus,
            @Value("${app.live.broadcast.channel:app.live.broadcast}") String channel) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setRecoveryInterval(5000L);
        container.addMessageListener(
                (message, pattern) -> redisBroadcastBus.onMessage(
                        new String(message.getBody(), StandardCharsets.UTF_8)),
                new ChannelTopic(channel));
        log.info("课堂广播已切换到 Redis Pub/Sub: channel={}", channel);
        return container;
    }
}