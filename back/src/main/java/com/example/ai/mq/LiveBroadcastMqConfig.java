package com.example.ai.mq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "app.live.broadcast.mode", havingValue = "rabbit")
public class LiveBroadcastMqConfig {

    @Bean
    public DirectExchange liveBroadcastExchange(
            @Value("${app.live.broadcast.exchange:app.live.broadcast.exchange}") String name) {
        return new DirectExchange(name, true, false);
    }

    @Bean
    public Queue liveBroadcastQueue(
            @Value("${app.live.broadcast.queue:app.live.broadcast.queue}") String name) {
        return new Queue(name, true);
    }

    @Bean
    public Binding liveBroadcastBinding(
            @Qualifier("liveBroadcastQueue") Queue queue,
            @Qualifier("liveBroadcastExchange") DirectExchange exchange,
            @Value("${app.live.broadcast.routing-key:live.broadcast}") String routingKey) {
        return BindingBuilder.bind(queue).to(exchange).with(routingKey);
    }
}