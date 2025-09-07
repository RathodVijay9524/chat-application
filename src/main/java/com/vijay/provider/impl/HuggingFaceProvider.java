package com.vijay.provider.impl;

import com.vijay.dto.ChatRequest;
import com.vijay.dto.ChatResponse;
import com.vijay.dto.ProviderInfo;
import com.vijay.provider.AIProvider;
import com.vijay.service.SystemMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class HuggingFaceProvider implements AIProvider {

    private final WebClient webClient;
    private final String apiKey;
    private final SystemMessageService systemMessageService;
    private final ToolCallbackProvider toolCallbackProvider;

    public HuggingFaceProvider(@Value("${spring.ai.huggingface.chat.api-key:}") String apiKey,
                               @Value("${spring.ai.huggingface.chat.url:https://api-inference.huggingface.co/models}") String baseUrl,
                               WebClient.Builder webClientBuilder,
                               SystemMessageService systemMessageService,
                               ToolCallbackProvider toolCallbackProvider) {
        this.apiKey = apiKey != null ? apiKey : "";
        this.systemMessageService = systemMessageService;
        this.toolCallbackProvider = toolCallbackProvider;
        
        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + this.apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
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
            String currentModel = originalModel;
            
            // Load system message
            String systemMessage = systemMessageService.getSystemMessage();
            
            // Enhanced system message with MCP tool information
            int mcpToolCount = getMCPToolCount();
            String toolInfo = getMCPToolInfo();
            String enhancedSystemMessage = systemMessage + "\n\nAvailable MCP Tools (" + mcpToolCount + "):\n" + toolInfo;
            
            // Use WebClient API call with enhanced system message
            String enhancedInput = enhancedSystemMessage + "\n\nUser: " + request.getMessage();
            
            // Create the request payload for Hugging Face API (simple format)
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("inputs", enhancedInput);
            requestBody.put("parameters", Map.of(
                "max_new_tokens", 100,
                "temperature", 0.7,
                "return_full_text", false,
                "do_sample", true
            ));
            
            // Log the request details for debugging
            log.info("HuggingFace API call - Model: {}, URL: /{}", currentModel, currentModel);
            log.debug("Request body: {}", requestBody);
            
            Object response = null;
            String actualModelUsed = currentModel;
            
            try {
                // Make the API call with dynamic model in URI
                response = webClient.post()
                        .uri("/" + currentModel)
                        .bodyValue(requestBody)
                        .retrieve()
                        .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                                clientResponse -> {
                                    log.error("HuggingFace API error - Status: {}, Model: {}", clientResponse.statusCode(), currentModel);
                                    return clientResponse.bodyToMono(String.class)
                                            .flatMap(errorBody -> {
                                                log.error("HuggingFace API error body: {}", errorBody);
                                                return Mono.error(new RuntimeException("HuggingFace API Error [" + clientResponse.statusCode() + "]: " + errorBody));
                                            });
                                })
                        .bodyToMono(Object.class)
                        .block();
            } catch (Exception e) {
                log.error("Failed to call HuggingFace API with model {}: {}", currentModel, e.getMessage());
                // Try with a different model as fallback
                if (!originalModel.equals("gpt2")) {
                    log.info("Trying fallback model: gpt2");
                    String fallbackModel = "gpt2";
                    actualModelUsed = fallbackModel;
                    response = webClient.post()
                            .uri("/" + fallbackModel)
                            .bodyValue(requestBody)
                            .retrieve()
                            .bodyToMono(Object.class)
                            .block();
                } else {
                    throw e;
                }
            }
            
            // Extract the response content
            String content = extractContentFromResponse(response);
            
            // Check if AI is requesting MCP tool usage and execute if needed
            content = processAIToolRequests(content, request.getMessage());
            
            long responseTime = System.currentTimeMillis() - startTime;
            
            return ChatResponse.builder()
                    .response(content)
                    .provider(getProviderName())
                    .model(actualModelUsed)
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

    private String extractContentFromResponse(Object response) {
        try {
            // HuggingFace API typically returns an array of objects
            if (response instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> responseList = (List<Map<String, Object>>) response;
                if (!responseList.isEmpty()) {
                    Map<String, Object> firstItem = responseList.get(0);
                    if (firstItem != null && firstItem.containsKey("generated_text")) {
                        return (String) firstItem.get("generated_text");
                    }
                }
            } else if (response instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> responseMap = (Map<String, Object>) response;
                if (responseMap.containsKey("generated_text")) {
                    return (String) responseMap.get("generated_text");
                }
            }
            
            log.warn("Unexpected response format from HuggingFace: {}", response);
            return "No response generated";
        } catch (Exception e) {
            log.error("Error extracting content from Hugging Face response: {}", e.getMessage());
            return "Error processing response";
        }
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