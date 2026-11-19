package com.example.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class UserVO {

    private Long id;
    private String username;
    private String nickname;
    private String role;
    private String phone;
    private String studentNo;
    private String college;
    private Integer status;
    private String banReason;
    private Integer approved;
    @JsonIgnore
    private String openid;
    private String token;
}