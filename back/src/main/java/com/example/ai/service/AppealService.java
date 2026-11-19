package com.example.ai.service;

import com.example.ai.dto.AppealDTO;
import com.example.ai.dto.AppealHandleDTO;
import com.example.ai.dto.AppealVO;

import java.util.List;

public interface AppealService {

    void submitAppeal(Long userId, AppealDTO dto);

    List<AppealVO> getMyAppeals(Long userId);

    List<AppealVO> getAllAppeals();

    void handleAppeal(AppealHandleDTO dto);
}