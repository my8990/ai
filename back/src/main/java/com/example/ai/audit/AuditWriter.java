package com.example.ai.audit;

import com.example.ai.entity.AuditLog;

public interface AuditWriter {

    void save(AuditLog record);
}