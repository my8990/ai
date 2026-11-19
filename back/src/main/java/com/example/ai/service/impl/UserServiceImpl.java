package com.example.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ai.common.BizException;
import com.example.ai.common.ErrorCode;
import com.example.ai.dto.LoginDTO;
import com.example.ai.dto.RegisterDTO;
import com.example.ai.dto.UserVO;
import com.example.ai.entity.User;
import com.example.ai.mapper.UserMapper;
import com.example.ai.security.LoginAttemptService;
import com.example.ai.security.Roles;
import com.example.ai.security.TokenBlacklistService;
import com.example.ai.service.UserService;
import com.example.ai.util.JwtUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final LoginAttemptService loginAttemptService;
    private final TokenBlacklistService tokenBlacklistService;
    private final RestTemplate restTemplate;

    @Value("${wechat.appid}")
    private String wxAppid;

    @Value("${wechat.secret}")
    private String wxSecret;

    @Override
    public UserVO login(LoginDTO dto) {
        String account = normalizeAccount(dto.getUsername());
        long lockRemaining = loginAttemptService.lockRemainingSeconds(account);
        if (lockRemaining > 0) {
            throw new BizException(ErrorCode.FORBIDDEN,
                    "登录失败次数过多，请 " + lockRemaining + " 秒后再试");
        }

        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername())
        );
        if (user == null || !BCrypt.checkpw(dto.getPassword(), user.getPassword())) {
            long remainingAttempts = loginAttemptService.onFailure(account);
            String message = remainingAttempts > 0
                    ? "账号或密码错误，剩余 " + remainingAttempts + " 次尝试机会"
                    : "账号或密码错误，账号已临时锁定";
            throw new BizException(ErrorCode.BAD_REQUEST, message);
        }
        loginAttemptService.onSuccess(account);
        if (user.getStatus() == 0) {
            String msg = "账号已被禁用";
            if (user.getBanReason() != null && !user.getBanReason().isEmpty()) {
                msg += "，原因：" + user.getBanReason();
            }
            msg += "，可提交申诉";
            throw new BizException(ErrorCode.FORBIDDEN, msg);
        }
        if ("teacher".equals(user.getRole()) && user.getApproved() == 0) {
            throw new BizException(ErrorCode.FORBIDDEN, "账号待管理员审核，请耐心等待");
        }

        UserVO vo = toVO(user);
        vo.setToken(jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole()));
        return vo;
    }

    @Override
    public UserVO register(RegisterDTO dto) {
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername())
        );
        if (count > 0) {
            throw new BizException(ErrorCode.CONFLICT, "用户名已存在");
        }

        if (dto.getPhone() != null && !dto.getPhone().isEmpty()) {
            Long phoneCount = userMapper.selectCount(
                    new LambdaQueryWrapper<User>().eq(User::getPhone, dto.getPhone())
            );
            if (phoneCount > 0) {
                throw new BizException(ErrorCode.CONFLICT, "手机号已注册");
            }
        }

        if (dto.getStudentNo() != null && !dto.getStudentNo().isEmpty()) {
            Long snoCount = userMapper.selectCount(
                    new LambdaQueryWrapper<User>().eq(User::getStudentNo, dto.getStudentNo())
            );
            if (snoCount > 0) {
                throw new BizException(ErrorCode.CONFLICT, "学号已注册");
            }
        }

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(BCrypt.hashpw(dto.getPassword(), BCrypt.gensalt()));
        user.setNickname(dto.getNickname() != null ? dto.getNickname() : dto.getUsername());
        user.setRole(dto.getRole());
        user.setPhone(dto.getPhone());
        user.setStudentNo(dto.getStudentNo());
        user.setCollege(dto.getCollege());
        user.setStatus(1);
        user.setApproved(Roles.TEACHER.equals(dto.getRole()) ? 0 : 1);

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.CONFLICT, "用户名、手机号或学号已被占用，请更换后重试");
        }

        UserVO vo = toVO(user);
        if (Roles.TEACHER.equals(dto.getRole())) {
            vo.setApproved(0);
        }
        return vo;
    }

    @Override
    public UserVO getCurrentUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return toVO(user);
    }

    @Override
    public void logout(String tokenId, long expireSeconds) {
        tokenBlacklistService.blacklist(tokenId, expireSeconds);
    }

    private String normalizeAccount(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private User createWxUser(String openid) {
        String password = BCrypt.hashpw(UUID.randomUUID().toString(), BCrypt.gensalt());
        for (int attempt = 1; attempt <= 5; attempt++) {
            String suffix = attempt == 1
                    ? openid.substring(0, Math.min(8, openid.length()))
                    : UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            User user = new User();
            user.setUsername("wx_" + suffix);
            user.setPassword(password);
            user.setNickname("微信用户");
            user.setRole(Roles.STUDENT);
            user.setOpenid(openid);
            user.setStatus(1);
            user.setApproved(1);
            try {
                userMapper.insert(user);
                return user;
            } catch (DuplicateKeyException e) {
                user.setId(null);
                log.warn("微信用户名冲突，重新生成: attempt={}, username={}", attempt, user.getUsername());
            }
        }
        throw new BizException(ErrorCode.INTERNAL_ERROR, "微信用户创建失败，请重试");
    }

    private UserVO toVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setRole(user.getRole());
        vo.setPhone(user.getPhone());
        vo.setStudentNo(user.getStudentNo());
        vo.setCollege(user.getCollege());
        vo.setStatus(user.getStatus());
        vo.setApproved(user.getApproved());
        vo.setOpenid(user.getOpenid());
        return vo;
    }

    @Override
    public UserVO wxLogin(String code) {
        String url = String.format(
                "https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code",
                wxAppid, wxSecret, code
        );

        String responseBody;
        try {
            responseBody = restTemplate.getForObject(url, String.class);
        } catch (Exception e) {
            log.error("调用微信接口失败", e);
            throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "微信登录服务异常");
        }

        String openid;
        try {
            JsonNode json = new ObjectMapper().readTree(responseBody);
            if (json.has("errcode") && json.get("errcode").asInt() != 0) {
                log.warn("微信接口返回错误: {}", responseBody);
                throw new BizException(ErrorCode.BAD_REQUEST, "微信登录失败: " + json.get("errmsg").asText());
            }
            openid = json.get("openid").asText();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析微信响应失败", e);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "微信登录响应解析失败");
        }

        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getOpenid, openid)
        );

        if (user == null) {
            user = createWxUser(openid);
        }

        if (user.getStatus() == 0) {
            String msg = "账号已被禁用";
            if (user.getBanReason() != null && !user.getBanReason().isEmpty()) {
                msg += "，原因：" + user.getBanReason();
            }
            msg += "，可提交申诉";
            throw new BizException(ErrorCode.FORBIDDEN, msg);
        }

        UserVO vo = toVO(user);
        vo.setToken(jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole()));
        return vo;
    }

    @Override
    public List<UserVO> getPendingTeachers() {
        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .eq(User::getRole, "teacher")
                        .eq(User::getApproved, 0)
                        .eq(User::getStatus, 1)
                        .orderByAsc(User::getCreateTime)
        );
        List<UserVO> result = new ArrayList<>();
        for (User u : users) {
            result.add(toVO(u));
        }
        return result;
    }

    @Override
    public void approveTeacher(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        if (!"teacher".equals(user.getRole())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "该用户不是教师");
        }
        user.setApproved(1);
        userMapper.updateById(user);
    }

    @Override
    public void rejectTeacher(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        if (!"teacher".equals(user.getRole())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "该用户不是教师");
        }
        user.setStatus(0);
        userMapper.updateById(user);
    }

    @Override
    public List<UserVO> getAllTeachers() {
        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .eq(User::getRole, "teacher")
                        .orderByDesc(User::getCreateTime)
        );
        List<UserVO> result = new ArrayList<>();
        for (User u : users) {
            result.add(toVO(u));
        }
        return result;
    }

    @Override
    public List<UserVO> getAllUsers() {
        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .orderByDesc(User::getCreateTime)
        );
        List<UserVO> result = new ArrayList<>();
        for (User u : users) {
            result.add(toVO(u));
        }
        return result;
    }

    @Override
    public void banUser(Long userId, String reason) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        if (Roles.ADMIN.equals(user.getRole())) {
            throw new BizException(ErrorCode.FORBIDDEN, "不能禁用管理员账号");
        }
        user.setStatus(0);
        user.setBanReason(reason);
        userMapper.updateById(user);
    }

    @Override
    public void unbanUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        user.setStatus(1);
        user.setBanReason(null);
        userMapper.updateById(user);
    }
}