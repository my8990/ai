package com.example.ai.audit;

import com.example.ai.entity.AuditLog;
import com.example.ai.mapper.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mq.audit.enabled", havingValue = "false", matchIfMissing = true)
public class AuditRecorder implements AuditWriter {

    private final AuditLogMapper auditLogMapper;

    @Override
    @Async("auditExecutor")
    public void save(AuditLog record) {
        try {
            auditLogMapper.insert(record);
        } catch (Exception e) {
            log.error("审计日志入库失败: module={}, action={}, uri={}", record.getModule(), record.getAction(), record.getUri(), e);
        }
    }
}