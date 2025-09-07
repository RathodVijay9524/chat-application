package com.vijay.provider.impl;

import com.vijay.dto.ChatRequest;
import com.vijay.dto.ChatResponse;
import com.vijay.dto.ProviderInfo;
import com.vijay.provider.AIProvider;
import com.vijay.service.MCPService;
import com.vijay.service.RAGService;
import com.vijay.service.SystemMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
public class OpenAIProvider implements AIProvider {

    private final ChatClient chatClient;
    private final MCPService mcpService;
    private final RAGService ragService;
    private final SystemMessageService systemMessageService;
    
    public OpenAIProvider(@Qualifier("openAiChatClient") ChatClient chatClient,
                          MCPService mcpService,
                          RAGService ragService,
                          SystemMessageService systemMessageService) {
        this.chatClient = chatClient;
        this.mcpService = mcpService;
        this.ragService = ragService;
        this.systemMessageService = systemMessageService;
    }
    
    @Override
    public String getProviderName() {
        return "openai";
    }
    
    @Override
    public ProviderInfo getProviderInfo() {
        return ProviderInfo.builder()
                .name("openai")
                .displayName("OpenAI")
                .description("OpenAI GPT models")
                .availableModels(getAvailableModels())
                .isAvailable(isAvailable())
                .status(isAvailable() ? "active" : "inactive")
                .build();
    }
    
    @Override
    public ChatResponse generateResponse(ChatRequest request) {
        long startTime = System.currentTimeMillis();
        
        try {
            // Generate RAG context
            String ragContext = ragService.generateRAGContext(request.getMessage());
            
            // Build enhanced prompt with RAG context
            String enhancedPrompt = buildEnhancedPrompt(request.getMessage(), ragContext);
            
            // Generate response using Spring AI ChatClient with system message
            String systemMessage = systemMessageService.getSystemMessage();
            String response = chatClient.prompt()
                    .system(systemMessage)
                    .user(enhancedPrompt)
                    .call()
                    .content();
            
            
            long responseTime = System.currentTimeMillis() - startTime;
            
            return ChatResponse.builder()
                    .response(response)
                    .provider(getProviderName())
                    .model(request.getModel() != null ? request.getModel() : "gpt-3.5-turbo")
                    .conversationId(request.getConversationId())
                    .timestamp(LocalDateTime.now())
                    .tokensUsed(40L) // Approximate token count
                    .responseTimeMs(responseTime)
                    .build();
                    
        } catch (Exception e) {
            log.error("Error generating response with OpenAI: {}", e.getMessage(), e);
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
    
    private String buildEnhancedPrompt(String userMessage, String ragContext) {
        StringBuilder prompt = new StringBuilder();
        
        if (!ragContext.isEmpty()) {
            prompt.append(ragContext).append("\n\n");
        }
        
        prompt.append("User Question: ").append(userMessage);
        
        return prompt.toString();
    }
    
    @Override
    public List<String> getAvailableModels() {
        return Arrays.asList(
                "gpt-3.5-turbo",
                "gpt-3.5-turbo-16k",
                "gpt-4",
                "gpt-4-turbo",
                "gpt-4o"
        );
    }
    
    @Override
    public boolean isAvailable() {
        try {
            // Simple availability check
            return true;
        } catch (Exception e) {
            log.warn("OpenAI provider is not available: {}", e.getMessage());
            return false;
        }
    }
}