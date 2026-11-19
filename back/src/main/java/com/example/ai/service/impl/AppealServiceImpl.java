package com.example.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ai.common.BizException;
import com.example.ai.common.ErrorCode;
import com.example.ai.dto.AppealDTO;
import com.example.ai.dto.AppealHandleDTO;
import com.example.ai.dto.AppealVO;
import com.example.ai.entity.Appeal;
import com.example.ai.entity.User;
import com.example.ai.mapper.AppealMapper;
import com.example.ai.mapper.UserMapper;
import com.example.ai.service.AppealService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AppealServiceImpl implements AppealService {

    private final AppealMapper appealMapper;
    private final UserMapper userMapper;

    @Override
    public void submitAppeal(Long userId, AppealDTO dto) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        if (user.getStatus() != 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "账号未被禁用，无需申诉");
        }

        Long pendingCount = appealMapper.selectCount(
                new LambdaQueryWrapper<Appeal>()
                        .eq(Appeal::getUserId, userId)
                        .eq(Appeal::getStatus, 0)
        );
        if (pendingCount > 0) {
            throw new BizException(ErrorCode.CONFLICT, "您已有待处理的申诉，请等待管理员审核");
        }

        Appeal appeal = new Appeal();
        appeal.setUserId(userId);
        appeal.setReason(dto.getReason());
        appeal.setStatus(0);
        appealMapper.insert(appeal);
    }

    @Override
    public List<AppealVO> getMyAppeals(Long userId) {
        List<Appeal> appeals = appealMapper.selectList(
                new LambdaQueryWrapper<Appeal>()
                        .eq(Appeal::getUserId, userId)
                        .orderByDesc(Appeal::getCreateTime)
        );
        List<AppealVO> result = new ArrayList<>();
        for (Appeal a : appeals) {
            result.add(toVO(a));
        }
        return result;
    }

    @Override
    public List<AppealVO> getAllAppeals() {
        List<Appeal> appeals = appealMapper.selectList(
                new LambdaQueryWrapper<Appeal>()
                        .orderByAsc(Appeal::getStatus)
                        .orderByDesc(Appeal::getCreateTime)
        );
        List<AppealVO> result = new ArrayList<>();
        for (Appeal a : appeals) {
            result.add(toVO(a));
        }
        return result;
    }

    @Override
    public void handleAppeal(AppealHandleDTO dto) {
        Appeal appeal = appealMapper.selectById(dto.getAppealId());
        if (appeal == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "申诉不存在");
        }
        if (appeal.getStatus() != 0) {
            throw new BizException(ErrorCode.CONFLICT, "该申诉已处理");
        }

        appeal.setStatus(dto.getStatus());
        appeal.setReply(dto.getReply());
        appealMapper.updateById(appeal);

        if (dto.getStatus() == 1) {
            User user = userMapper.selectById(appeal.getUserId());
            if (user != null) {
                user.setStatus(1);
                user.setBanReason(null);
                userMapper.updateById(user);
            }
        }
    }

    private AppealVO toVO(Appeal appeal) {
        AppealVO vo = new AppealVO();
        vo.setId(appeal.getId());
        vo.setUserId(appeal.getUserId());
        vo.setReason(appeal.getReason());
        vo.setStatus(appeal.getStatus());
        vo.setReply(appeal.getReply());
        vo.setCreateTime(appeal.getCreateTime());
        vo.setUpdateTime(appeal.getUpdateTime());

        User user = userMapper.selectById(appeal.getUserId());
        if (user != null) {
            vo.setUsername(user.getUsername());
            vo.setNickname(user.getNickname());
        }
        return vo;
    }
}