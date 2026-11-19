package com.example.ai.mq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "app.mq.audit.enabled", havingValue = "true")
public class AuditMqConfig {

    @Bean
    public DirectExchange auditExchange(
            @Value("${app.mq.audit.exchange:app.audit.exchange}") String name) {
        return new DirectExchange(name, true, false);
    }

    @Bean
    public Queue auditQueue(
            @Value("${app.mq.audit.queue:app.audit.queue}") String name,
            @Value("${app.mq.audit.dlx:app.audit.dlx}") String dlx,
            @Value("${app.mq.audit.dlq-routing-key:audit.dlq}") String dlqRoutingKey) {
        return QueueBuilder.durable(name)
                .withArgument("x-dead-letter-exchange", dlx)
                .withArgument("x-dead-letter-routing-key", dlqRoutingKey)
                .build();
    }

    @Bean
    public Binding auditBinding(
            @Qualifier("auditQueue") Queue queue,
            @Qualifier("auditExchange") DirectExchange exchange,
            @Value("${app.mq.audit.routing-key:audit.log}") String routingKey) {
        return BindingBuilder.bind(queue).to(exchange).with(routingKey);
    }

    @Bean
    public DirectExchange auditDeadLetterExchange(
            @Value("${app.mq.audit.dlx:app.audit.dlx}") String name) {
        return new DirectExchange(name, true, false);
    }

    @Bean
    public Queue auditDeadLetterQueue(
            @Value("${app.mq.audit.dlq:app.audit.dlq}") String name) {
        return new Queue(name, true);
    }

    @Bean
    public Binding auditDeadLetterBinding(
            @Qualifier("auditDeadLetterQueue") Queue queue,
            @Qualifier("auditDeadLetterExchange") DirectExchange exchange,
            @Value("${app.mq.audit.dlq-routing-key:audit.dlq}") String routingKey) {
        return BindingBuilder.bind(queue).to(exchange).with(routingKey);
    }
}