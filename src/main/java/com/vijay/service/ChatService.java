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
    private final SimpleRAGService simpleRAGService;
    
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
        
        // Check if RAG is requested
        String enhancedMessage = request.getMessage();
        if (request.getMessage().toLowerCase().contains("@rag") || request.getMessage().toLowerCase().contains("@document")) {
            enhancedMessage = enhanceWithRAG(request.getMessage());
            log.info("🔍 RAG Enhancement applied: {}", enhancedMessage);
        }
        
        // Update the request with enhanced message
        ChatRequest enhancedRequest = new ChatRequest();
        enhancedRequest.setMessage(enhancedMessage);
        enhancedRequest.setProvider(request.getProvider());
        enhancedRequest.setModel(request.getModel());
        enhancedRequest.setConversationId(request.getConversationId());
        enhancedRequest.setGeminiApiKey(request.getGeminiApiKey());
        enhancedRequest.setOpenaiApiKey(request.getOpenaiApiKey());
        enhancedRequest.setClaudeApiKey(request.getClaudeApiKey());
        
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
        
        return provider.generateResponse(enhancedRequest);
    }
    
    /**
     * Enhance message with RAG context
     */
    private String enhanceWithRAG(String originalMessage) {
        try {
            // Remove RAG trigger words
            String cleanMessage = originalMessage.replaceAll("(?i)@rag|@document", "").trim();
            
            // Try to get RAG context from available documents
            String ragContext = "";
            
            // Check if Vijay's profile is loaded
            if (simpleRAGService.isRAGAvailable()) {
                String vijayContext = simpleRAGService.chatWithPDF(cleanMessage, "vijay-rathod-profile.txt");
                if (!vijayContext.contains("not found") && !vijayContext.contains("couldn't find")) {
                    ragContext += "\n\n📄 Document Context (Vijay's Profile):\n" + vijayContext;
                }
            }
            
            // If we have RAG context, enhance the message
            if (!ragContext.isEmpty()) {
                return cleanMessage + ragContext;
            } else {
                return cleanMessage + "\n\n📄 Note: No relevant document context found. You can ask about Vijay's profile, experience, or skills.";
            }
            
        } catch (Exception e) {
            log.error("Error enhancing message with RAG: {}", e.getMessage());
            return originalMessage.replaceAll("(?i)@rag|@document", "").trim();
        }
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
