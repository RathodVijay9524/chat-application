package com.vijay.service;

import com.vijay.dto.ChatRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;

@Service
public class DynamicApiKeyService {
    
    /**
     * Get the appropriate API key for a provider from the request
     */
    public String getApiKeyForProvider(String provider, ChatRequest request) {
        if (request == null) {
            return null;
        }
        
        return switch (provider.toLowerCase()) {
            case "openai" -> request.getOpenaiApiKey();
            case "claude" -> request.getClaudeApiKey();
            case "groq" -> request.getGroqApiKey();
            case "gemini" -> request.getGeminiApiKey();
            case "openrouter" -> request.getOpenrouterApiKey();
            case "huggingface" -> request.getHuggingfaceApiKey();
            default -> null;
        };
    }
    
    /**
     * Check if a provider has a valid API key in the request
     */
    public boolean hasValidApiKey(String provider, ChatRequest request) {
        String apiKey = getApiKeyForProvider(provider, request);
        return apiKey != null && !apiKey.trim().isEmpty() && !apiKey.equals("test-key");
    }
    
    /**
     * Create a WebClient with dynamic API key for a provider
     */
    public WebClient createWebClientWithApiKey(String provider, String apiKey, String baseUrl, WebClient.Builder webClientBuilder) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("API key cannot be null or empty for provider: " + provider);
        }
        
        return webClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
    
    /**
     * Get all API keys from request as a map
     */
    public Map<String, String> getAllApiKeys(ChatRequest request) {
        Map<String, String> apiKeys = new HashMap<>();
        if (request != null) {
            apiKeys.put("openai", request.getOpenaiApiKey());
            apiKeys.put("claude", request.getClaudeApiKey());
            apiKeys.put("groq", request.getGroqApiKey());
            apiKeys.put("gemini", request.getGeminiApiKey());
            apiKeys.put("openrouter", request.getOpenrouterApiKey());
            apiKeys.put("huggingface", request.getHuggingfaceApiKey());
        }
        return apiKeys;
    }
}
