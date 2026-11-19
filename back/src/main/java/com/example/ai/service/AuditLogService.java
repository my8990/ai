package com.example.ai.service;

import com.example.ai.common.PageResult;
import com.example.ai.dto.AuditLogQueryDTO;
import com.example.ai.dto.AuditLogVO;

public interface AuditLogService {

    PageResult<AuditLogVO> page(AuditLogQueryDTO query);
}