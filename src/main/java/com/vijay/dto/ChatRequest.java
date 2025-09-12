package com.vijay.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ChatRequest {
    private String message;
    private String provider;
    
    private String model;
    private Double temperature = 0.7;
    private Integer maxTokens = 1000;
    private String conversationId;
    
    // User session management
    @JsonProperty("userId")
    @JsonAlias({"user_id", "user-id", "user"})
    private String userId;
    
    // API Keys for dynamic provider configuration
    @JsonProperty("openaiApiKey")
    @JsonAlias({"openai_api_key", "openai-api-key", "openai_key"})
    private String openaiApiKey;
    
    @JsonProperty("claudeApiKey")
    @JsonAlias({"claude_api_key", "claude-api-key", "claude_key", "anthropicApiKey", "anthropic_api_key"})
    private String claudeApiKey;
    
    @JsonProperty("groqApiKey")
    @JsonAlias({"groq_api_key", "groq-api-key", "groq_key"})
    private String groqApiKey;
    
    @JsonProperty("geminiApiKey")
    @JsonAlias({"gemini_api_key", "gemini-api-key", "gemini_key", "googleApiKey", "google_api_key"})
    private String geminiApiKey;
    
    @JsonProperty("openrouterApiKey")
    @JsonAlias({"openrouter_api_key", "openrouter-api-key", "openrouter_key"})
    private String openrouterApiKey;
    
    @JsonProperty("huggingfaceApiKey")
    @JsonAlias({"huggingface_api_key", "huggingface-api-key", "huggingface_key", "hfApiKey", "hf_api_key"})
    private String huggingfaceApiKey;
}
