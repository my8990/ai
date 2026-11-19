package com.example.ai.service;

import com.example.ai.dto.LoginDTO;
import com.example.ai.dto.RegisterDTO;
import com.example.ai.dto.UserVO;

import java.util.List;

public interface UserService {

    UserVO login(LoginDTO dto);

    UserVO register(RegisterDTO dto);

    UserVO getCurrentUser(Long userId);

    void logout(String tokenId, long expireSeconds);

    UserVO wxLogin(String code);

    List<UserVO> getPendingTeachers();

    void approveTeacher(Long userId);

    void rejectTeacher(Long userId);

    List<UserVO> getAllTeachers();

    List<UserVO> getAllUsers();

    void banUser(Long userId, String reason);

    void unbanUser(Long userId);
}