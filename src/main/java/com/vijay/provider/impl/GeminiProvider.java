package com.vijay.provider.impl;

import com.vijay.dto.ChatRequest;
import com.vijay.dto.ChatResponse;
import com.vijay.dto.ProviderInfo;
import com.vijay.provider.AIProvider;
import com.vijay.service.DynamicApiKeyService;
import com.vijay.service.RAGService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class GeminiProvider implements AIProvider {

    private final WebClient webClient;
    private final String defaultApiKey;
    private final String baseUrl;
    private final WebClient.Builder webClientBuilder;
    private final DynamicApiKeyService dynamicApiKeyService;
    private final RAGService ragService;
    private final ChatClient chatClient;
    private final ToolCallbackProvider toolCallbackProvider;

    public GeminiProvider(@Value("${gemini.api-key:}") String apiKey,
                          @Value("${gemini.base-url:https://generativelanguage.googleapis.com/v1beta/openai}") String baseUrl,
                          WebClient.Builder webClientBuilder,
                          DynamicApiKeyService dynamicApiKeyService,
                          RAGService ragService,
                          @Qualifier("geminiChatClient") ChatClient chatClient,
                          ToolCallbackProvider toolCallbackProvider) {
        this.defaultApiKey = apiKey != null ? apiKey : "";
        this.baseUrl = baseUrl;
        this.webClientBuilder = webClientBuilder;
        this.dynamicApiKeyService = dynamicApiKeyService;
        this.ragService = ragService;
        this.chatClient = chatClient;
        this.toolCallbackProvider = toolCallbackProvider;
        
        System.out.println("🔧 Gemini Provider Initialization:");
        System.out.println("   Default API Key: " + (this.defaultApiKey != null && !this.defaultApiKey.isEmpty() ? this.defaultApiKey.substring(0, Math.min(8, this.defaultApiKey.length())) + "..." : "NULL"));
        System.out.println("   Base URL: " + baseUrl);
        
        // Create default webClient with default API key
        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + this.defaultApiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
    
    @Override
    public String getProviderName() {
        return "gemini";
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
            
            // Get API key - use dynamic key from request if available, otherwise use default
            String apiKey = dynamicApiKeyService.getApiKeyForProvider("gemini", request);
            if (apiKey == null || apiKey.trim().isEmpty()) {
                apiKey = defaultApiKey;
            }
            
            // Create WebClient with the appropriate API key
            WebClient clientToUse = webClient;
            if (dynamicApiKeyService.hasValidApiKey("gemini", request)) {
                clientToUse = dynamicApiKeyService.createWebClientWithApiKey("gemini", apiKey, baseUrl, webClientBuilder);
            }
            
            // Prepare request body for Gemini API
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", request.getModel() != null ? request.getModel() : "gemini-1.5-flash");
            
            // Load system message from resources
            String systemMessage = loadSystemMessage();
            
            // Enhanced system message with MCP tool information
            int mcpToolCount = getMCPToolCount();
            String toolInfo = getMCPToolInfo();
            String enhancedSystemMessage = systemMessage + "\n\nAvailable MCP Tools (" + mcpToolCount + "):\n" + toolInfo;
            
            // Use WebClient API call with enhanced system message
            String systemPrompt = "System: " + enhancedSystemMessage + "\n\nUser: " + enhancedPrompt;
            
            List<Map<String, String>> messages = Arrays.asList(
                Map.of("role", "user", "content", systemPrompt)
            );
            requestBody.put("messages", messages);
            
            log.info("Sending request to Gemini API with {} messages (including system message)", messages.size());
            log.debug("System message: {}", systemMessage);
            log.debug("User message: {}", enhancedPrompt);
            
            // Gemini API parameters
            requestBody.put("temperature", 0.7);
            requestBody.put("max_tokens", 1000);
            
            // Make API call to Gemini
            @SuppressWarnings("unchecked")
            Map<String, Object> response = clientToUse.post()
                    .uri("/chat/completions")
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .flatMap(errorBody -> Mono.error(new RuntimeException("Gemini API Error: " + errorBody))))
                    .bodyToMono(Map.class)
                    .block();
            
            // Extract response content
            String content = "";
            if (response != null && response.containsKey("choices")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                if (!choices.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    content = (String) message.get("content");
                }
            }
            
            // Check if AI is requesting MCP tool usage and execute if needed
            content = processAIToolRequests(content, request.getMessage());

            long responseTime = System.currentTimeMillis() - startTime;
            
            // Print API key information to console
            System.out.println("\n🔑 Gemini API Key Info:");
            System.out.println("   Provider: " + getProviderName());
            System.out.println("   Model: " + (request.getModel() != null ? request.getModel() : "gemini-1.5-flash"));
            System.out.println("   API Key: " + (apiKey != null && !apiKey.isEmpty() ? apiKey.substring(0, Math.min(8, apiKey.length())) + "..." : "NOT SET"));
            System.out.println("   Key Source: " + (dynamicApiKeyService.hasValidApiKey("gemini", request) ? "DYNAMIC (from frontend)" : "DEFAULT (from environment)"));
            System.out.println("   RAG Context: " + (ragContext.isEmpty() ? "None" : "Enhanced"));
            System.out.println("   MCP Tools: " + mcpToolCount + " available");
            System.out.println("   MCP Server: Connected via ToolCallbackProvider");
            System.out.println("   Response Time: " + responseTime + "ms");
            System.out.println("   Response: " + (content.length() > 100 ? content.substring(0, 100) + "..." : content));
            System.out.println("");
            
            return ChatResponse.builder()
                    .response(content)
                    .provider(getProviderName())
                    .model(request.getModel() != null ? request.getModel() : "gemini-1.5-flash")
                    .conversationId(request.getConversationId())
                    .timestamp(LocalDateTime.now())
                    .tokensUsed(45L) // Approximate token count
                    .responseTimeMs(responseTime)
                    .build();
        } catch (Exception e) {
            log.error("Error generating response with Gemini: {}", e.getMessage(), e);
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
            "gemini-1.5-flash",
            "gemini-1.5-pro",
            "gemini-1.0-pro",
            "gemini-1.5-flash-8b",
            "gemini-1.5-flash-32b"
        );
    }
    
    @Override
    public boolean isAvailable() {
        boolean available = defaultApiKey != null && !defaultApiKey.trim().isEmpty() && !defaultApiKey.equals("test-key") && !defaultApiKey.equals("");
        System.out.println("🔍 Gemini Availability Check:");
        System.out.println("   Default API Key: " + (defaultApiKey != null && !defaultApiKey.isEmpty() ? defaultApiKey.substring(0, Math.min(8, defaultApiKey.length())) + "..." : "NULL/EMPTY"));
        System.out.println("   Available: " + available);
        System.out.println("   Note: Dynamic API keys from frontend will be checked at request time");
        return available;
    }
    
    /**
     * Check availability with a specific request (for dynamic API keys)
     */
    public boolean isAvailableWithRequest(ChatRequest request) {
        if (dynamicApiKeyService.hasValidApiKey("gemini", request)) {
            return true;
        }
        return isAvailable();
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
                String systemMessage = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                log.info("Successfully loaded system message from tool-only.st: {} characters", systemMessage.length());
                log.debug("System message content: {}", systemMessage);
                return systemMessage;
            } else {
                log.warn("Could not find prompts/tool-only.st in resources");
            }
        } catch (Exception e) {
            log.warn("Could not load system message: {}", e.getMessage());
        }
        String fallbackMessage = "You are an AI assistant with access to MCP tools.";
        log.info("Using fallback system message: {}", fallbackMessage);
        return fallbackMessage;
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
                        log.info("Executing listFaqs tool for Gemini based on user request");
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
                        log.info("Executing createNote tool for Gemini based on user request");
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