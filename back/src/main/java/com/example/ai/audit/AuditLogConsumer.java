package com.example.ai.audit;

import com.example.ai.entity.AuditLog;
import com.example.ai.mapper.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mq.audit.enabled", havingValue = "true")
public class AuditLogConsumer {

    private final AuditLogMapper auditLogMapper;

    @RabbitListener(queues = "${app.mq.audit.queue}")
    public void onAuditLog(AuditLog record) {
        auditLogMapper.insert(record);
        log.debug("审计日志已消费入库: module={}, action={}, traceId={}",
                record.getModule(), record.getAction(), record.getTraceId());
    }
}