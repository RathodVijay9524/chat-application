package com.vijay.dto;

import lombok.Data;

@Data
public class ChatRequest {
    private String message;
    private String provider;
    
    private String model;
    private Double temperature = 0.7;
    private Integer maxTokens = 1000;
    private String conversationId;
    
    // API Keys for dynamic provider configuration
    private String openaiApiKey;
    private String claudeApiKey;
    private String groqApiKey;
    private String geminiApiKey;
    private String openrouterApiKey;
    private String huggingfaceApiKey;
}
