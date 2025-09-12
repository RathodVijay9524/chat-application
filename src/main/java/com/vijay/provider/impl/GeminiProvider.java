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
public class GeminiProvider implements AIProvider {

    private final String defaultApiKey;
    private final DynamicApiKeyService dynamicApiKeyService;
    private final DynamicChatClientService dynamicChatClientService;
    private final RAGService ragService;
    private final ChatClient chatClient;
    private final ToolCallbackProvider toolCallbackProvider;

    public GeminiProvider(@Value("${gemini.api-key:}") String apiKey,
                          DynamicApiKeyService dynamicApiKeyService,
                          DynamicChatClientService dynamicChatClientService,
                          RAGService ragService,
                          @Qualifier("geminiChatClient") ChatClient chatClient,
                          ToolCallbackProvider toolCallbackProvider) {
        this.defaultApiKey = apiKey != null ? apiKey : "";
        this.dynamicApiKeyService = dynamicApiKeyService;
        this.dynamicChatClientService = dynamicChatClientService;
        this.ragService = ragService;
        this.chatClient = chatClient;
        this.toolCallbackProvider = toolCallbackProvider;
        
        System.out.println("🔧 Gemini Provider Initialization:");
        System.out.println("   Default API Key: " + (this.defaultApiKey != null && !this.defaultApiKey.isEmpty() ? this.defaultApiKey.substring(0, Math.min(8, this.defaultApiKey.length())) + "..." : "NULL"));
        System.out.println("   Using ChatClient with MessageChatMemoryAdvisor for memory management");
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
        System.out.println("🔍 GeminiProvider.generateResponse called");
        System.out.println("🔍 Request in GeminiProvider: " + request.toString());
        System.out.println("🔍 geminiApiKey in GeminiProvider: " + (request.getGeminiApiKey() != null ? request.getGeminiApiKey().substring(0, Math.min(8, request.getGeminiApiKey().length())) + "..." : "NULL"));
        System.out.println("🔍 Request hash in GeminiProvider: " + request.hashCode());
        System.out.println("🔍 Request class in GeminiProvider: " + request.getClass().getName());
        
        long startTime = System.currentTimeMillis();
        try {
            // Generate RAG context
            String ragContext = ragService.generateRAGContext(request.getMessage());
            
            // Build enhanced prompt with RAG context
            String enhancedPrompt = buildEnhancedPrompt(request.getMessage(), ragContext);
            
            // Get API key for logging purposes
            String dynamicApiKey = dynamicApiKeyService.getApiKeyForProvider("gemini", request);
            System.out.println("🔍 API Key Debug in GeminiProvider:");
            System.out.println("   Dynamic API Key from request: " + (dynamicApiKey != null ? dynamicApiKey.substring(0, Math.min(8, dynamicApiKey.length())) + "..." : "NULL"));
            System.out.println("   Default API Key from properties: " + (defaultApiKey != null ? defaultApiKey.substring(0, Math.min(8, defaultApiKey.length())) + "..." : "NULL"));
            System.out.println("   Request geminiApiKey field: " + (request.getGeminiApiKey() != null ? request.getGeminiApiKey().substring(0, Math.min(8, request.getGeminiApiKey().length())) + "..." : "NULL"));
            
            String apiKey = dynamicApiKey;
            if (apiKey == null || apiKey.trim().isEmpty()) {
                apiKey = defaultApiKey;
                System.out.println("   Using DEFAULT API key (dynamic was null/empty)");
            } else {
                System.out.println("   Using DYNAMIC API key from frontend");
            }
            
            // Load system message from resources
            String systemMessage = loadSystemMessage();
            
            // Enhanced system message with MCP tool information
            int mcpToolCount = getMCPToolCount();
            String toolInfo = getMCPToolInfo();
            String enhancedSystemMessage = systemMessage + "\n\nAvailable MCP Tools (" + mcpToolCount + "):\n" + toolInfo;
            
            // Use dynamic WebClient with the API key from frontend for API calls
            WebClient dynamicWebClient = dynamicChatClientService.createGeminiWebClient(request);
            
            // Create the request payload for Gemini API
            Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                    Map.of("parts", List.of(
                        Map.of("text", enhancedSystemMessage + "\n\n" + enhancedPrompt)
                    ))
                ),
                "generationConfig", Map.of(
                    "temperature", request.getTemperature() != null ? request.getTemperature() : 0.7,
                    "maxOutputTokens", request.getMaxTokens() != null ? request.getMaxTokens() : 1000
                ),
                "safetySettings", List.of(
                    Map.of("category", "HARM_CATEGORY_HARASSMENT", "threshold", "BLOCK_MEDIUM_AND_ABOVE"),
                    Map.of("category", "HARM_CATEGORY_HATE_SPEECH", "threshold", "BLOCK_MEDIUM_AND_ABOVE"),
                    Map.of("category", "HARM_CATEGORY_SEXUALLY_EXPLICIT", "threshold", "BLOCK_MEDIUM_AND_ABOVE"),
                    Map.of("category", "HARM_CATEGORY_DANGEROUS_CONTENT", "threshold", "BLOCK_MEDIUM_AND_ABOVE")
                )
            );
            
            // Debug logging
            System.out.println("🔍 Gemini API Debug:");
            System.out.println("   API Key: " + (apiKey != null && !apiKey.isEmpty() ? apiKey.substring(0, Math.min(8, apiKey.length())) + "..." : "NOT SET"));
            System.out.println("   API Key Length: " + (apiKey != null ? apiKey.length() : 0));
            System.out.println("   Key Source: " + (dynamicApiKeyService.hasValidApiKey("gemini", request) ? "DYNAMIC (from frontend)" : "DEFAULT (from environment)"));
            System.out.println("   Request Body: " + requestBody);
            System.out.println("   Full API Key: " + apiKey);
            
            // Make API call with dynamic API key
            String response;
            try {
                response = dynamicWebClient.post()
                        .uri("/models/gemini-1.5-flash:generateContent")
                        .bodyValue(requestBody)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .map(responseMap -> {
                            // Extract the response text from Gemini API response
                            Map<String, Object> candidates = (Map<String, Object>) ((List<?>) responseMap.get("candidates")).get(0);
                            Map<String, Object> content = (Map<String, Object>) candidates.get("content");
                            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
                            return (String) parts.get(0).get("text");
                        })
                        .block();
            } catch (WebClientResponseException e) {
                System.out.println("❌ Gemini API Error Details:");
                System.out.println("   Error: " + e.getStatusCode() + " " + e.getStatusText() + " from " + e.getRequest().getURI());
                System.out.println("   API Key Used: " + (apiKey != null && !apiKey.isEmpty() ? apiKey.substring(0, Math.min(8, apiKey.length())) + "..." : "NOT SET"));
                System.out.println("   Key Source: " + (dynamicApiKeyService.hasValidApiKey("gemini", request) ? "DYNAMIC (from frontend)" : "DEFAULT (from environment)"));
                System.out.println("   Response Body: " + e.getResponseBodyAsString());
                System.out.println("   Request Headers: " + e.getRequest().getHeaders());
                throw new RuntimeException("Error generating response with Gemini: " + e.getStatusCode() + " " + e.getStatusText(), e);
            } catch (Exception e) {
                System.out.println("❌ Gemini API Error Details:");
                System.out.println("   Error: " + e.getMessage());
                System.out.println("   API Key Used: " + (apiKey != null && !apiKey.isEmpty() ? apiKey.substring(0, Math.min(8, apiKey.length())) + "..." : "NOT SET"));
                System.out.println("   Key Source: " + (dynamicApiKeyService.hasValidApiKey("gemini", request) ? "DYNAMIC (from frontend)" : "DEFAULT (from environment)"));
                throw e;
            }
            
            // Check if AI is requesting MCP tool usage and execute if needed
            String content = processAIToolRequests(response, request.getMessage());

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