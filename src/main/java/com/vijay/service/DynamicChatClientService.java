package com.vijay.service;

import com.vijay.dto.ChatRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class DynamicChatClientService {

    private final WebClient.Builder webClientBuilder;
    private final DynamicApiKeyService dynamicApiKeyService;

    // Default API keys from application.properties
    @Value("${spring.ai.openai.api-key:}")
    private String defaultOpenaiApiKey;
    
    @Value("${spring.ai.anthropic.api-key:}")
    private String defaultAnthropicApiKey;
    
    @Value("${groq.api-key:}")
    private String defaultGroqApiKey;
    
    @Value("${gemini.api-key:}")
    private String defaultGeminiApiKey;
    
    @Value("${spring.ai.openrouter.api-key:}")
    private String defaultOpenrouterApiKey;
    
    @Value("${spring.ai.huggingface.chat.api-key:}")
    private String defaultHuggingfaceApiKey;

    @Autowired
    public DynamicChatClientService(WebClient.Builder webClientBuilder,
                                   DynamicApiKeyService dynamicApiKeyService) {
        this.webClientBuilder = webClientBuilder;
        this.dynamicApiKeyService = dynamicApiKeyService;
    }

    /**
     * Create a dynamic WebClient for OpenAI with the API key from the request
     */
    public WebClient createOpenAiWebClient(ChatRequest request) {
        String apiKey = dynamicApiKeyService.getApiKeyForProvider("openai", request);
        if (apiKey == null || apiKey.trim().isEmpty()) {
            apiKey = defaultOpenaiApiKey;
        }

        // Create a fresh WebClient.Builder to avoid header conflicts
        return WebClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /**
     * Create a dynamic WebClient for Anthropic Claude with the API key from the request
     */
    public WebClient createAnthropicWebClient(ChatRequest request) {
        String apiKey = dynamicApiKeyService.getApiKeyForProvider("claude", request);
        if (apiKey == null || apiKey.trim().isEmpty()) {
            apiKey = defaultAnthropicApiKey;
        }

        // Create a fresh WebClient.Builder to avoid header conflicts
        return WebClient.builder()
                .baseUrl("https://api.anthropic.com/v1")
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /**
     * Create a dynamic WebClient for Groq with the API key from the request
     */
    public WebClient createGroqWebClient(ChatRequest request) {
        String apiKey = dynamicApiKeyService.getApiKeyForProvider("groq", request);
        if (apiKey == null || apiKey.trim().isEmpty()) {
            apiKey = defaultGroqApiKey;
        }

        // Create a fresh WebClient.Builder to avoid header conflicts
        return WebClient.builder()
                .baseUrl("https://api.groq.com/openai/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /**
     * Create a dynamic WebClient for Google Gemini with the API key from the request
     */
    public WebClient createGeminiWebClient(ChatRequest request) {
        String apiKey = dynamicApiKeyService.getApiKeyForProvider("gemini", request);
        if (apiKey == null || apiKey.trim().isEmpty()) {
            apiKey = defaultGeminiApiKey;
        }

        // Create a fresh WebClient.Builder to avoid header conflicts
        return WebClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .defaultHeader("x-goog-api-key", apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /**
     * Create a dynamic WebClient for OpenRouter with the API key from the request
     */
    public WebClient createOpenRouterWebClient(ChatRequest request) {
        String apiKey = dynamicApiKeyService.getApiKeyForProvider("openrouter", request);
        if (apiKey == null || apiKey.trim().isEmpty()) {
            apiKey = defaultOpenrouterApiKey;
        }

        // Create a fresh WebClient.Builder to avoid header conflicts
        return WebClient.builder()
                .baseUrl("https://openrouter.ai/api/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /**
     * Create a dynamic WebClient for Hugging Face with the API key from the request
     */
    public WebClient createHuggingFaceWebClient(ChatRequest request) {
        String apiKey = dynamicApiKeyService.getApiKeyForProvider("huggingface", request);
        if (apiKey == null || apiKey.trim().isEmpty()) {
            apiKey = defaultHuggingfaceApiKey;
        }

        // Create a fresh WebClient.Builder to avoid header conflicts
        return WebClient.builder()
                .baseUrl("https://api-inference.huggingface.co/models")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /**
     * Get the appropriate WebClient for a provider with dynamic API key
     */
    public WebClient getDynamicWebClient(String provider, ChatRequest request) {
        return switch (provider.toLowerCase()) {
            case "openai" -> createOpenAiWebClient(request);
            case "claude" -> createAnthropicWebClient(request);
            case "groq" -> createGroqWebClient(request);
            case "gemini" -> createGeminiWebClient(request);
            case "openrouter" -> createOpenRouterWebClient(request);
            case "huggingface" -> createHuggingFaceWebClient(request);
            default -> throw new IllegalArgumentException("Unsupported provider: " + provider);
        };
    }
}
