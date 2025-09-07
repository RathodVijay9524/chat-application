package com.vijay.controller;

import com.vijay.dto.ChatRequest;
import com.vijay.dto.ChatResponse;
import com.vijay.dto.ProviderInfo;
import com.vijay.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001"})
public class ChatController {
    
    private final ChatService chatService;
    
    @PostMapping(value = "/message", consumes = "application/json", produces = "application/json")
    public ResponseEntity<ChatResponse> sendMessage(@RequestBody ChatRequest request) {
        log.info("Received chat request: provider={}, model={}, message={}", 
                request.getProvider(), request.getModel(), request.getMessage());
        
        try {
            // Validate required fields
            if (request.getMessage() == null || request.getMessage().trim().isEmpty()) {
                log.warn("Empty message received");
                return ResponseEntity.badRequest().body(ChatResponse.builder()
                        .response("Message cannot be empty")
                        .provider(request.getProvider())
                        .conversationId(request.getConversationId())
                        .error("Empty message")
                        .build());
            }
            
            if (request.getProvider() == null || request.getProvider().trim().isEmpty()) {
                log.warn("No provider specified");
                return ResponseEntity.badRequest().body(ChatResponse.builder()
                        .response("Provider must be specified")
                        .provider(request.getProvider())
                        .conversationId(request.getConversationId())
                        .error("No provider specified")
                        .build());
            }
            
            ChatResponse response = chatService.generateResponse(request);
            log.info("Generated response successfully for provider: {}", request.getProvider());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error processing chat request", e);
            ChatResponse errorResponse = ChatResponse.builder()
                    .response("An error occurred while processing your request.")
                    .provider(request.getProvider())
                    .conversationId(request.getConversationId())
                    .error(e.getMessage())
                    .build();
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
    
    @GetMapping("/providers")
    public ResponseEntity<List<ProviderInfo>> getProviders() {
        log.info("Fetching available providers");
        List<ProviderInfo> providers = chatService.getAvailableProviders();
        return ResponseEntity.ok(providers);
    }
    
    @GetMapping("/providers/{providerName}/models")
    public ResponseEntity<List<String>> getModelsForProvider(@PathVariable String providerName) {
        log.info("Fetching models for provider: {}", providerName);
        List<String> models = chatService.getModelsForProvider(providerName);
        return ResponseEntity.ok(models);
    }
    
    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("Chat service is running");
    }
    
    @PostMapping(value = "/test", consumes = "application/json", produces = "application/json")
    public ResponseEntity<String> testPost(@RequestBody String body) {
        log.info("Test POST received: {}", body);
        return ResponseEntity.ok("Test POST successful: " + body);
    }
    
    @PostMapping(value = "/test-providers", consumes = "application/json", produces = "application/json")
    public ResponseEntity<Map<String, Object>> testProviders() {
        log.info("Testing providers");
        Map<String, Object> response = new HashMap<>();
        try {
            List<ProviderInfo> providers = chatService.getAvailableProviders();
            response.put("status", "success");
            response.put("providers", providers);
            response.put("count", providers.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error testing providers", e);
            response.put("status", "error");
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    @PostMapping(value = "/test-chat", consumes = "application/json", produces = "application/json")
    public ResponseEntity<ChatResponse> testChat(@RequestBody ChatRequest request) {
        log.info("Test chat request: provider={}, message={}", request.getProvider(), request.getMessage());
        
        // Create a mock response for testing
        ChatResponse mockResponse = ChatResponse.builder()
                .response("This is a test response from " + request.getProvider() + ". Your message was: " + request.getMessage())
                .provider(request.getProvider())
                .model(request.getModel() != null ? request.getModel() : "test-model")
                .conversationId(request.getConversationId() != null ? request.getConversationId() : "test-conversation")
                .timestamp(java.time.LocalDateTime.now())
                .tokensUsed(50L)
                .responseTimeMs(100L)
                .build();
        
        return ResponseEntity.ok(mockResponse);
    }
}
