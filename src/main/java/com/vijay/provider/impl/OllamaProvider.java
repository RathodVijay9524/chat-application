package com.vijay.provider.impl;

import com.vijay.dto.ChatRequest;
import com.vijay.dto.ChatResponse;
import com.vijay.dto.ProviderInfo;
import com.vijay.provider.AIProvider;
import com.vijay.service.SystemMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
public class OllamaProvider implements AIProvider {

    private final ChatClient chatClient;
    private final SystemMessageService systemMessageService;
    
    public OllamaProvider(@Qualifier("ollamaChatClient") ChatClient chatClient,
                          SystemMessageService systemMessageService) {
        this.chatClient = chatClient;
        this.systemMessageService = systemMessageService;
    }
    
    @Override
    public String getProviderName() {
        return "ollama";
    }
    
    @Override
    public ProviderInfo getProviderInfo() {
        return ProviderInfo.builder()
                .name("ollama")
                .displayName("Ollama (Local)")
                .description("Local Ollama models")
                .availableModels(getAvailableModels())
                .isAvailable(isAvailable())
                .status(isAvailable() ? "active" : "inactive")
                .build();
    }
    
    @Override
    public ChatResponse generateResponse(ChatRequest request) {
        long startTime = System.currentTimeMillis();
        
        try {
            String systemMessage = systemMessageService.getSystemMessage();
            String response = chatClient.prompt()
                    .system(systemMessage)
                    .user(request.getMessage())
                    .call()
                    .content();
            
            long responseTime = System.currentTimeMillis() - startTime;
            
            return ChatResponse.builder()
                    .response(response)
                    .provider(getProviderName())
                    .model(request.getModel() != null ? request.getModel() : "qwen2.5-coder:7b")
                    .conversationId(request.getConversationId())
                    .timestamp(LocalDateTime.now())
                    .tokensUsed(50L) // Approximate token count
                    .responseTimeMs(responseTime)
                    .build();
                    
        } catch (Exception e) {
            log.error("Error generating response with Ollama: {}", e.getMessage(), e);
            return ChatResponse.builder()
                    .response("Sorry, I encountered an error while processing your request.")
                    .provider(getProviderName())
                    .model(request.getModel())
                    .conversationId(request.getConversationId())
                    .timestamp(LocalDateTime.now())
                    .responseTimeMs(System.currentTimeMillis() - startTime)
                    .error(e.getMessage())
                    .build();
        }
    }
    
    @Override
    public List<String> getAvailableModels() {
        return Arrays.asList(
                "qwen2.5-coder:7b",
                "qwen2.5-coder:3b",
                "qwen2.5-coder:1.5b",
                "deepseek-coder:6.7b",
                "deepseek-r1:8b",
                "deepseek-r1:1.5b",
                "mxbai-embed-large:latest"
        );
    }
    
    @Override
    public boolean isAvailable() {
        try {
            // Test if Ollama is available by making a simple call
            String systemMessage = systemMessageService.getSystemMessage();
            chatClient.prompt().system(systemMessage).user("test").call().content();
            return true;
        } catch (Exception e) {
            log.warn("Ollama is not available: {}", e.getMessage());
            return false;
        }
    }
}