package com.vijay.provider.impl;

import com.vijay.dto.ChatRequest;
import com.vijay.dto.ChatResponse;
import com.vijay.dto.ProviderInfo;
import com.vijay.provider.AIProvider;
import com.vijay.service.DynamicApiKeyService;
import com.vijay.service.DynamicChatClientService;
import com.vijay.service.RAGService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class GroqProvider implements AIProvider {

    private final String defaultApiKey;
    private final DynamicApiKeyService dynamicApiKeyService;
    private final DynamicChatClientService dynamicChatClientService;
    private final RAGService ragService;
    private final ChatClient chatClient;
    private final ToolCallbackProvider toolCallbackProvider;

    public GroqProvider(@Value("${groq.api-key:}") String apiKey,
                       DynamicApiKeyService dynamicApiKeyService,
                       DynamicChatClientService dynamicChatClientService,
                       RAGService ragService,
                       @Qualifier("groqChatClient") ChatClient chatClient,
                       ToolCallbackProvider toolCallbackProvider) {
        this.defaultApiKey = apiKey != null ? apiKey : "";
        this.dynamicApiKeyService = dynamicApiKeyService;
        this.dynamicChatClientService = dynamicChatClientService;
        this.ragService = ragService;
        this.chatClient = chatClient;
        this.toolCallbackProvider = toolCallbackProvider;
        
        System.out.println("🔧 Groq Provider Initialization:");
        System.out.println("   Default API Key: " + (this.defaultApiKey != null ? this.defaultApiKey.substring(0, Math.min(8, this.defaultApiKey.length())) + "..." : "NULL"));
        System.out.println("   Using ChatClient with MessageChatMemoryAdvisor for memory management");
    }

    @Override
    public String getProviderName() {
        return "groq";
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
            String model = request.getModel() != null ? request.getModel() : "llama-3.1-8b-instant";
            
            // Validate model name - if not in available models, use default
            if (!getAvailableModels().contains(model)) {
                log.warn("Model '{}' not available for Groq, using default: llama-3.1-8b-instant", model);
                model = "llama-3.1-8b-instant";
            }
            
            // Additional validation for known problematic models
            if (model.equals("llama-2-13b-chat")) {
                log.warn("Model 'llama-2-13b-chat' is deprecated, using default: llama-3.1-8b-instant");
                model = "llama-3.1-8b-instant";
            }
            
            // Generate RAG context
            String ragContext = ragService.generateRAGContext(request.getMessage());
            
            // Build enhanced prompt with RAG context
            String enhancedPrompt = buildEnhancedPrompt(request.getMessage(), ragContext);
            
            // Get API key for logging purposes
            String apiKey = dynamicApiKeyService.getApiKeyForProvider("groq", request);
            if (apiKey == null || apiKey.trim().isEmpty()) {
                apiKey = defaultApiKey;
            }
            
            // Load system message from resources
            String systemMessage = loadSystemMessage();
            
            // Enhanced system message with MCP tool information
            int mcpToolCount = getMCPToolCount();
            String toolInfo = getMCPToolInfo();
            String enhancedSystemMessage = systemMessage + "\n\nAvailable MCP Tools (" + mcpToolCount + "):\n" + toolInfo;
            
            // Use dynamic WebClient with the API key from frontend for API calls
            WebClient dynamicWebClient = dynamicChatClientService.createGroqWebClient(request);
            
            // Create the request payload for Groq API (OpenAI-compatible)
            Map<String, Object> requestBody = Map.of(
                "model", request.getModel() != null ? request.getModel() : "llama-3.1-8b-instant",
                "messages", List.of(
                    Map.of("role", "system", "content", enhancedSystemMessage),
                    Map.of("role", "user", "content", enhancedPrompt)
                ),
                "temperature", request.getTemperature() != null ? request.getTemperature() : 0.7,
                "max_tokens", request.getMaxTokens() != null ? request.getMaxTokens() : 1000
            );
            
            // Debug logging
            System.out.println("🔍 Groq API Debug:");
            System.out.println("   API Key: " + (apiKey != null && !apiKey.isEmpty() ? apiKey.substring(0, Math.min(8, apiKey.length())) + "..." : "NOT SET"));
            System.out.println("   API Key Length: " + (apiKey != null ? apiKey.length() : 0));
            System.out.println("   Key Source: " + (dynamicApiKeyService.hasValidApiKey("groq", request) ? "DYNAMIC (from frontend)" : "DEFAULT (from environment)"));
            System.out.println("   Model: " + model);
            System.out.println("   Request Body: " + requestBody);
            System.out.println("   Full API Key: " + apiKey);
            
            // Make API call with dynamic API key
            String response;
            try {
                response = dynamicWebClient.post()
                        .uri("/chat/completions")
                        .bodyValue(requestBody)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .map(responseMap -> {
                            // Extract the response text from Groq API response
                            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
                            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                            return (String) message.get("content");
                        })
                        .block();
            } catch (WebClientResponseException e) {
                System.out.println("❌ Groq API Error Details:");
                System.out.println("   Error: " + e.getStatusCode() + " " + e.getStatusText() + " from " + e.getRequest().getURI());
                System.out.println("   API Key Used: " + (apiKey != null && !apiKey.isEmpty() ? apiKey.substring(0, Math.min(8, apiKey.length())) + "..." : "NOT SET"));
                System.out.println("   Key Source: " + (dynamicApiKeyService.hasValidApiKey("groq", request) ? "DYNAMIC (from frontend)" : "DEFAULT (from environment)"));
                System.out.println("   Response Body: " + e.getResponseBodyAsString());
                System.out.println("   Request Headers: " + e.getRequest().getHeaders());
                System.out.println("   Request Body: " + requestBody);
                throw new RuntimeException("Error generating response with Groq: " + e.getStatusCode() + " " + e.getStatusText(), e);
            } catch (Exception e) {
                System.out.println("❌ Groq API Error Details:");
                System.out.println("   Error: " + e.getMessage());
                System.out.println("   API Key Used: " + (apiKey != null && !apiKey.isEmpty() ? apiKey.substring(0, Math.min(8, apiKey.length())) + "..." : "NOT SET"));
                System.out.println("   Key Source: " + (dynamicApiKeyService.hasValidApiKey("groq", request) ? "DYNAMIC (from frontend)" : "DEFAULT (from environment)"));
                throw e;
            }
            
            // Check if AI is requesting MCP tool usage and execute if needed
            String content = processAIToolRequests(response, request.getMessage());
            
            long responseTime = System.currentTimeMillis() - startTime;
            
            // Print API key information to console
            System.out.println("\n🔑 Groq API Key Info:");
            System.out.println("   Provider: " + getProviderName());
            System.out.println("   Model: " + model);
            System.out.println("   API Key: " + (apiKey != null && !apiKey.isEmpty() ? apiKey.substring(0, Math.min(8, apiKey.length())) + "..." : "NOT SET"));
            System.out.println("   Key Source: " + (dynamicApiKeyService.hasValidApiKey("groq", request) ? "DYNAMIC (from frontend)" : "DEFAULT (from environment)"));
            System.out.println("   RAG Context: " + (ragContext.isEmpty() ? "None" : "Enhanced"));
            System.out.println("   MCP Tools: " + mcpToolCount + " available");
            System.out.println("   MCP Server: Connected via ToolCallbackProvider");
            System.out.println("   Response Time: " + responseTime + "ms");
            System.out.println("   Response: " + (content.length() > 100 ? content.substring(0, 100) + "..." : content));
            System.out.println("");
            
            return ChatResponse.builder()
                    .response(content)
                    .provider(getProviderName())
                    .model(model)
                    .conversationId(request.getConversationId())
                    .timestamp(LocalDateTime.now())
                    .tokensUsed(50L) // Approximate token count
                    .responseTimeMs(responseTime)
                    .build();
                    
        } catch (Exception e) {
            log.error("Error generating response with Groq: {}", e.getMessage(), e);
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
            "llama-3.1-8b-instant",
            "llama-3.1-70b-versatile", 
            "llama-3.1-405b-preview",
            "mixtral-8x7b-32768",
            "gemma-7b-it",
            "llama-2-70b-4096",
            "llama-2-7b-2048"
        );
    }

    @Override
    public boolean isAvailable() {
        boolean available = defaultApiKey != null && !defaultApiKey.trim().isEmpty() && !defaultApiKey.equals("test-key") && !defaultApiKey.equals("");
        System.out.println("🔍 Groq Availability Check:");
        System.out.println("   Default API Key: " + (defaultApiKey != null && !defaultApiKey.isEmpty() ? defaultApiKey.substring(0, Math.min(8, defaultApiKey.length())) + "..." : "NULL/EMPTY"));
        System.out.println("   Available: " + available);
        System.out.println("   Note: Dynamic API keys from frontend will be checked at request time");
        return available;
    }
    
    /**
     * Check availability with a specific request (for dynamic API keys)
     */
    public boolean isAvailableWithRequest(ChatRequest request) {
        if (dynamicApiKeyService.hasValidApiKey("groq", request)) {
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
                        log.info("Executing listFaqs tool for Groq based on user request");
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
                        log.info("Executing createNote tool for Groq based on user request");
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