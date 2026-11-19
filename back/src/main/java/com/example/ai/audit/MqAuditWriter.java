package com.example.ai.audit;

import com.example.ai.entity.AuditLog;
import com.example.ai.mapper.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mq.audit.enabled", havingValue = "true")
public class MqAuditWriter implements AuditWriter {

    private final RabbitTemplate rabbitTemplate;
    private final AuditLogMapper auditLogMapper;

    @Value("${app.mq.audit.exchange}")
    private String exchange;

    @Value("${app.mq.audit.routing-key}")
    private String routingKey;

    @Override
    public void save(AuditLog record) {
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, record);
        } catch (Exception e) {
            log.error("审计日志投递 MQ 失败，降级直写数据库: module={}, action={}, uri={}",
                    record.getModule(), record.getAction(), record.getUri(), e);
            fallbackSave(record);
        }
    }

    private void fallbackSave(AuditLog record) {
        try {
            auditLogMapper.insert(record);
        } catch (Exception e) {
            log.error("审计日志降级入库失败: module={}, action={}, uri={}",
                    record.getModule(), record.getAction(), record.getUri(), e);
        }
    }
}