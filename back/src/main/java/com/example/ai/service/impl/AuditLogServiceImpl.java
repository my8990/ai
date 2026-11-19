package com.example.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.ai.common.BizException;
import com.example.ai.common.ErrorCode;
import com.example.ai.common.PageResult;
import com.example.ai.dto.AuditLogQueryDTO;
import com.example.ai.dto.AuditLogVO;
import com.example.ai.entity.AuditLog;
import com.example.ai.mapper.AuditLogMapper;
import com.example.ai.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogMapper auditLogMapper;

    @Override
    public PageResult<AuditLogVO> page(AuditLogQueryDTO query) {
        if (query.getStartDate() != null && query.getEndDate() != null
                && query.getStartDate().isAfter(query.getEndDate())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "开始日期不能晚于结束日期");
        }

        LocalDateTime startTime = query.getStartDate() == null ? null : query.getStartDate().atStartOfDay();
        LocalDateTime endTime = query.getEndDate() == null ? null : query.getEndDate().plusDays(1).atStartOfDay();

        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<AuditLog>()
                .eq(query.getUserId() != null, AuditLog::getUserId, query.getUserId())
                .eq(StringUtils.hasText(query.getModule()), AuditLog::getModule, query.getModule())
                .eq(StringUtils.hasText(query.getAction()), AuditLog::getAction, query.getAction())
                .eq(query.getSuccess() != null, AuditLog::getSuccess, query.getSuccess())
                .ge(startTime != null, AuditLog::getCreateTime, startTime)
                .lt(endTime != null, AuditLog::getCreateTime, endTime)
                .orderByDesc(AuditLog::getCreateTime)
                .orderByDesc(AuditLog::getId);

        Page<AuditLog> page = new Page<>(query.getPageNum(), query.getPageSize());
        return PageResult.of(auditLogMapper.selectPage(page, wrapper), this::toVO);
    }

    private AuditLogVO toVO(AuditLog log) {
        AuditLogVO vo = new AuditLogVO();
        BeanUtils.copyProperties(log, vo);
        return vo;
    }
}