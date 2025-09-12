package com.vijay.service;

import com.vijay.dto.ChatRequest;
import com.vijay.dto.ChatResponse;
import com.vijay.dto.ProviderInfo;
import com.vijay.provider.AIProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {
    
    private final AIProviderFactory providerFactory;
    
    public ChatResponse generateResponse(ChatRequest request) {
        System.out.println("🔍 ChatService.generateResponse called");
        System.out.println("🔍 Request in ChatService: " + request.toString());
        System.out.println("🔍 geminiApiKey in ChatService: " + (request.getGeminiApiKey() != null ? request.getGeminiApiKey().substring(0, Math.min(8, request.getGeminiApiKey().length())) + "..." : "NULL"));
        System.out.println("🔍 Request hash in ChatService: " + request.hashCode());
        System.out.println("🔍 Request class in ChatService: " + request.getClass().getName());
        
        log.info("Generating response for provider: {}, model: {}", request.getProvider(), request.getModel());
        
        // Generate conversation ID if not provided
        if (request.getConversationId() == null) {
            request.setConversationId(UUID.randomUUID().toString());
        }
        
        AIProvider provider = providerFactory.getProvider(request.getProvider());
        if (provider == null) {
            return ChatResponse.builder()
                    .response("Provider not found: " + request.getProvider())
                    .provider(request.getProvider())
                    .conversationId(request.getConversationId())
                    .error("Provider not supported")
                    .build();
        }
        
        if (!provider.isAvailable()) {
            return ChatResponse.builder()
                    .response("Provider is currently unavailable: " + request.getProvider())
                    .provider(request.getProvider())
                    .conversationId(request.getConversationId())
                    .error("Provider unavailable")
                    .build();
        }
        
        return provider.generateResponse(request);
    }
    
    public List<ProviderInfo> getAvailableProviders() {
        return providerFactory.getAllProviders().stream()
                .map(AIProvider::getProviderInfo)
                .collect(java.util.stream.Collectors.toList());
    }
    
    public List<String> getProviderNames() {
        return providerFactory.getAvailableProviderNames();
    }
    
    public List<String> getModelsForProvider(String providerName) {
        AIProvider provider = providerFactory.getProvider(providerName);
        if (provider != null) {
            return provider.getAvailableModels();
        }
        return List.of();
    }
}
