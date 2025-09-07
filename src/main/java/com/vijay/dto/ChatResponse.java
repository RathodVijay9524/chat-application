package com.vijay.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {
    private String response;
    private String provider;
    private String model;
    private String conversationId;
    private LocalDateTime timestamp;
    private Long tokensUsed;
    private Long responseTimeMs;
    private String error;
}
