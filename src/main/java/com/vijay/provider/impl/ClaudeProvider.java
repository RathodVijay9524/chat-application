package com.vijay.provider.impl;

import com.vijay.dto.ChatRequest;
import com.vijay.dto.ChatResponse;
import com.vijay.dto.ProviderInfo;
import com.vijay.provider.AIProvider;
import com.vijay.service.SystemMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
public class ClaudeProvider implements AIProvider {

    private final ChatClient chatClient;
    private final SystemMessageService systemMessageService;
    
    public ClaudeProvider(@Qualifier("anthropicChatClient") ChatClient chatClient,
                          SystemMessageService systemMessageService) {
        this.chatClient = chatClient;
        this.systemMessageService = systemMessageService;
    }
    
    @Override
    public String getProviderName() {
        return "claude";
    }
    
    @Override
    public ProviderInfo getProviderInfo() {
        return ProviderInfo.builder()
                .name("claude")
                .displayName("Anthropic Claude")
                .description("Anthropic Claude models")
                .availableModels(getAvailableModels())
                .isAvailable(isAvailable())
                .status(isAvailable() ? "active" : "inactive")
                .build();
    }
    
    @Override
    public ChatResponse generateResponse(ChatRequest request) {
        long startTime = System.currentTimeMillis();
        
        try {
            // Generate response using Spring AI ChatClient with system message
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
                    .model(request.getModel() != null ? request.getModel() : "claude-3-haiku-20240307")
                    .conversationId(request.getConversationId())
                    .timestamp(LocalDateTime.now())
                    .tokensUsed(50L) // Approximate token count
                    .responseTimeMs(responseTime)
                    .build();
                    
        } catch (Exception e) {
            log.error("Error generating response with Claude: {}", e.getMessage(), e);
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
                "claude-3-sonnet-20240229",
                "claude-3-opus-20240229",
                "claude-3-haiku-20240307",
                "claude-3-5-sonnet-20241022"
        );
    }
    
    @Override
    public boolean isAvailable() {
        try {
            // Simple availability check
            return true;
        } catch (Exception e) {
            log.warn("Claude provider is not available: {}", e.getMessage());
            return false;
        }
    }
}