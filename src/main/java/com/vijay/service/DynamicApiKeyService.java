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
            System.out.println("🔍 DynamicApiKeyService: request is null");
            return null;
        }
        
        String apiKey = switch (provider.toLowerCase()) {
            case "openai" -> request.getOpenaiApiKey();
            case "claude" -> request.getClaudeApiKey();
            case "groq" -> request.getGroqApiKey();
            case "gemini" -> request.getGeminiApiKey();
            case "openrouter" -> request.getOpenrouterApiKey();
            case "huggingface" -> request.getHuggingfaceApiKey();
            default -> null;
        };
        
        System.out.println("🔍 DynamicApiKeyService.getApiKeyForProvider:");
        System.out.println("   Provider: " + provider);
        System.out.println("   Request geminiApiKey: " + (request.getGeminiApiKey() != null ? request.getGeminiApiKey().substring(0, Math.min(8, request.getGeminiApiKey().length())) + "..." : "NULL"));
        System.out.println("   Request openaiApiKey: " + (request.getOpenaiApiKey() != null ? request.getOpenaiApiKey().substring(0, Math.min(8, request.getOpenaiApiKey().length())) + "..." : "NULL"));
        System.out.println("   Request claudeApiKey: " + (request.getClaudeApiKey() != null ? request.getClaudeApiKey().substring(0, Math.min(8, request.getClaudeApiKey().length())) + "..." : "NULL"));
        System.out.println("   Request groqApiKey: " + (request.getGroqApiKey() != null ? request.getGroqApiKey().substring(0, Math.min(8, request.getGroqApiKey().length())) + "..." : "NULL"));
        System.out.println("   Request openrouterApiKey: " + (request.getOpenrouterApiKey() != null ? request.getOpenrouterApiKey().substring(0, Math.min(8, request.getOpenrouterApiKey().length())) + "..." : "NULL"));
        System.out.println("   Request huggingfaceApiKey: " + (request.getHuggingfaceApiKey() != null ? request.getHuggingfaceApiKey().substring(0, Math.min(8, request.getHuggingfaceApiKey().length())) + "..." : "NULL"));
        System.out.println("   Returned API key: " + (apiKey != null ? apiKey.substring(0, Math.min(8, apiKey.length())) + "..." : "NULL"));
        
        return apiKey;
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
