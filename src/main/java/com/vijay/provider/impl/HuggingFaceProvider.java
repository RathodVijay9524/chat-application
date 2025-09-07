package com.vijay.provider.impl;

import com.vijay.dto.ChatRequest;
import com.vijay.dto.ChatResponse;
import com.vijay.dto.ProviderInfo;
import com.vijay.provider.AIProvider;
import com.vijay.service.SystemMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
public class HuggingFaceProvider implements AIProvider {

    private final String apiKey;
    private final SystemMessageService systemMessageService;
    private final ToolCallbackProvider toolCallbackProvider;
    private final ChatClient chatClient;

    public HuggingFaceProvider(@Value("${spring.ai.huggingface.chat.api-key:}") String apiKey,
                               SystemMessageService systemMessageService,
                               ToolCallbackProvider toolCallbackProvider,
                               @Qualifier("huggingFaceChatClient") ChatClient chatClient) {
        this.apiKey = apiKey != null ? apiKey : "";
        this.systemMessageService = systemMessageService;
        this.toolCallbackProvider = toolCallbackProvider;
        this.chatClient = chatClient;
        
        System.out.println("🔧 HuggingFace Provider Initialization:");
        System.out.println("   API Key: " + (apiKey != null && !apiKey.isEmpty() ? apiKey.substring(0, Math.min(8, apiKey.length())) + "..." : "NOT SET"));
        System.out.println("   Using ChatClient with MessageChatMemoryAdvisor for memory management");
    }

    @Override
    public String getProviderName() {
        return "huggingface";
    }

    @Override
    public ProviderInfo getProviderInfo() {
        return ProviderInfo.builder()
                .name(getProviderName())
                .isAvailable(isAvailable())
                .availableModels(getAvailableModels())
                .build();
    }

    @Override
    public ChatResponse generateResponse(ChatRequest request) {
        long startTime = System.currentTimeMillis();
        
        try {
            // Use a simple, reliable model for text generation
            final String originalModel = request.getModel() != null ? request.getModel() : "gpt2";
            
            // Load system message
            String systemMessage = systemMessageService.getSystemMessage();
            
            // Enhanced system message with MCP tool information
            int mcpToolCount = getMCPToolCount();
            String toolInfo = getMCPToolInfo();
            String enhancedSystemMessage = systemMessage + "\n\nAvailable MCP Tools (" + mcpToolCount + "):\n" + toolInfo;
            
            // Use ChatClient for memory management and MCP tools
            String response = chatClient.prompt()
                    .system(enhancedSystemMessage)
                    .user(request.getMessage())
                    .call()
                    .content();
            
            // Check if AI is requesting MCP tool usage and execute if needed
            String content = processAIToolRequests(response, request.getMessage());
            
            long responseTime = System.currentTimeMillis() - startTime;
            
            return ChatResponse.builder()
                    .response(content)
                    .provider(getProviderName())
                    .model(originalModel)
                    .conversationId(request.getConversationId())
                    .timestamp(LocalDateTime.now())
                    .tokensUsed(calculateTokens(request.getMessage(), content))
                    .responseTimeMs(responseTime)
                    .build();
                    
        } catch (Exception e) {
            log.error("Error generating response with Hugging Face: {}", e.getMessage(), e);
            return ChatResponse.builder()
                    .response("Sorry, I encountered an error while processing your request with Hugging Face.")
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
            "microsoft/DialoGPT-medium",
            "microsoft/DialoGPT-small", 
            "facebook/blenderbot-400M",
            "facebook/blenderbot-90M",
            "microsoft/DialoGPT-large",
            "distilbert-base-uncased",
            "bert-base-uncased"
        );
    }

    @Override
    public boolean isAvailable() {
        // HuggingFace Inference API appears to be deprecated/changed as of 2024
        // Many models are returning 404 errors even with valid API keys
        System.out.println("🔍 HuggingFace Availability Check:");
        System.out.println("   Status: DISABLED - HuggingFace Inference API appears to be deprecated");
        System.out.println("   Issue: Models returning 404 errors (api-inference.huggingface.co)");
        return false; // Temporarily disabled due to API issues
    }


    private Long calculateTokens(String prompt, String response) {
        // Simple approximation for token count
        return (long) (prompt.split("\\s+").length + response.split("\\s+").length);
    }
    
    private int getMCPToolCount() {
        try {
            // Get MCP tools from the ToolCallbackProvider
            return toolCallbackProvider.getToolCallbacks().length;
        } catch (Exception e) {
            log.warn("Could not get MCP tool count: {}", e.getMessage());
            return 0;
        }
    }
    
    private String getMCPToolInfo() {
        try {
            var toolCallbacks = toolCallbackProvider.getToolCallbacks();
            if (toolCallbacks.length == 0) {
                return "No MCP tools available.";
            }
            
            StringBuilder toolInfo = new StringBuilder();
            for (var toolCallback : toolCallbacks) {
                String toolName = toolCallback.getToolDefinition().name();
                String toolDescription = toolCallback.getToolDefinition().description();
                toolInfo.append("- ").append(toolName).append(": ").append(toolDescription).append("\n");
            }
            return toolInfo.toString();
        } catch (Exception e) {
            log.error("Error getting MCP tool info: {}", e.getMessage());
            return "Error retrieving MCP tool information.";
        }
    }
    
    private String processAIToolRequests(String aiResponse, String userMessage) {
        try {
            // Check if user is asking for FAQs and we have the listFaqs tool
            if (userMessage.toLowerCase().contains("faq") || userMessage.toLowerCase().contains("list sample faqs")) {
                var toolCallbacks = toolCallbackProvider.getToolCallbacks();
                for (var toolCallback : toolCallbacks) {
                    String toolName = toolCallback.getToolDefinition().name();
                    if (toolName.equals("listFaqs")) {
                        log.info("Executing listFaqs tool for HuggingFace based on user request");
                        var result = toolCallback.call("{}");
                        return "Here are the sample FAQs:\n" + result;
                    }
                }
            }
            
            // Check if user is asking to create a note and we have the createNote tool
            if (userMessage.toLowerCase().contains("create note") || userMessage.toLowerCase().contains("note")) {
                var toolCallbacks = toolCallbackProvider.getToolCallbacks();
                for (var toolCallback : toolCallbacks) {
                    String toolName = toolCallback.getToolDefinition().name();
                    if (toolName.equals("createNote")) {
                        log.info("Executing createNote tool for HuggingFace based on user request");
                        var result = toolCallback.call("{\"title\":\"Sample Note\",\"body\":\"" + userMessage + "\"}");
                        return "Note created:\n" + result;
                    }
                }
            }
            
            return aiResponse;
        } catch (Exception e) {
            log.error("Error processing AI tool requests: {}", e.getMessage());
            return aiResponse;
        }
    }
}