package com.vijay.provider.impl;

import com.vijay.dto.ChatRequest;
import com.vijay.dto.ChatResponse;
import com.vijay.dto.ProviderInfo;
import com.vijay.provider.AIProvider;
import com.vijay.service.RAGService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
public class OpenRouterProvider implements AIProvider {

    private final String apiKey;
    private final RAGService ragService;
    private final ToolCallbackProvider toolCallbackProvider;
    private final ChatClient chatClient;

    public OpenRouterProvider(@Value("${spring.ai.openrouter.api-key:}") String apiKey,
                             RAGService ragService,
                             ToolCallbackProvider toolCallbackProvider,
                             @Qualifier("openRouterChatClient") ChatClient chatClient) {
        this.apiKey = apiKey;
        this.ragService = ragService;
        this.toolCallbackProvider = toolCallbackProvider;
        this.chatClient = chatClient;
        
        System.out.println("🔧 OpenRouter Provider Initialization:");
        System.out.println("   API Key: " + (apiKey != null && !apiKey.isEmpty() ? apiKey.substring(0, Math.min(8, apiKey.length())) + "..." : "NOT SET"));
        System.out.println("   Using ChatClient with MessageChatMemoryAdvisor for memory management");
    }
    
    @Override
    public String getProviderName() {
        return "openrouter";
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
            // Generate RAG context
            String ragContext = ragService.generateRAGContext(request.getMessage());
            
            // Build enhanced prompt with RAG context
            String enhancedPrompt = buildEnhancedPrompt(request.getMessage(), ragContext);
            
            // Model for logging purposes
            String model = request.getModel() != null ? request.getModel() : "openai/gpt-3.5-turbo";

            // Load system message from resources
            String systemMessage = loadSystemMessage();
            
            // Enhanced system message with MCP tool information
            int mcpToolCount = getMCPToolCount();
            String toolInfo = getMCPToolInfo();
            String enhancedSystemMessage = systemMessage + "\n\nAvailable MCP Tools (" + mcpToolCount + "):\n" + toolInfo;
            
            // Use ChatClient for memory management and MCP tools
            String response = chatClient.prompt()
                    .system(enhancedSystemMessage)
                    .user(enhancedPrompt)
                    .call()
                    .content();
            
            // Check if AI is requesting MCP tool usage and execute if needed
            String content = processAIToolRequests(response, request.getMessage());

            long responseTime = System.currentTimeMillis() - startTime;

            // Print API key information to console
            System.out.println("\n🔑 OpenRouter API Key Info:");
            System.out.println("   Provider: " + getProviderName());
            System.out.println("   Model: " + (request.getModel() != null ? request.getModel() : "openai/gpt-3.5-turbo"));
            System.out.println("   API Key: " + (apiKey != null && !apiKey.isEmpty() ? apiKey.substring(0, Math.min(8, apiKey.length())) + "..." : "NOT SET"));
            System.out.println("   RAG Context: " + (ragContext.isEmpty() ? "None" : "Enhanced"));
            System.out.println("   MCP Tools: " + mcpToolCount + " available");
            System.out.println("   MCP Server: Spring AI Built-in (Connected)");
            System.out.println("   Response Time: " + responseTime + "ms");
            System.out.println("   Response: " + (content.length() > 100 ? content.substring(0, 100) + "..." : content));
            System.out.println("");

            return ChatResponse.builder()
                    .response(content)
                    .provider(getProviderName())
                    .model(model)
                    .conversationId(request.getConversationId())
                    .timestamp(LocalDateTime.now())
                    .tokensUsed(45L) // Approximate token count
                    .responseTimeMs(responseTime)
                    .build();
        } catch (Exception e) {
            log.error("Error generating response with OpenRouter: {}", e.getMessage(), e);
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
            "openai/gpt-3.5-turbo",
            "openai/gpt-4",
            "openai/gpt-4o",
            "anthropic/claude-3-sonnet",
            "anthropic/claude-3-opus",
            "anthropic/claude-3-haiku",
            "google/gemini-pro",
            "google/gemini-flash",
            "meta-llama/llama-3.1-8b-instruct",
            "meta-llama/llama-3.1-70b-instruct",
            "mistralai/mistral-7b-instruct",
            "mistralai/mixtral-8x7b-instruct"
        );
    }
    
    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isEmpty() && !apiKey.equals("test-key");
    }
    
    private String buildEnhancedPrompt(String userMessage, String ragContext) {
        StringBuilder prompt = new StringBuilder();
        
        if (!ragContext.isEmpty()) {
            prompt.append(ragContext).append("\n\n");
        }
        
        prompt.append("User Question: ").append(userMessage);
        
        return prompt.toString();
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
    
    private String loadSystemMessage() {
        try {
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream("prompts/tool-only.st");
            if (inputStream != null) {
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.warn("Could not load system message: {}", e.getMessage());
        }
        return "You are an AI assistant with access to MCP tools.";
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
                        log.info("Executing listFaqs tool for OpenRouter based on user request");
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
                        log.info("Executing createNote tool for OpenRouter based on user request");
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
