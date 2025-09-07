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
public class ConversationMessage {
    private String id;
    private String role; // "user" or "assistant"
    private String content;
    private String provider;
    private String model;
    private LocalDateTime timestamp;
    private Long tokensUsed;
}
