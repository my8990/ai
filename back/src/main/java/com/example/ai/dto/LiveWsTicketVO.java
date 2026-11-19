package com.example.ai.dto;

import lombok.Data;

@Data
public class LiveWsTicketVO {

    private String ticket;

    private Integer expireSeconds;
}