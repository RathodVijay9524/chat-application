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
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@CrossOrigin(
        origins = {"http://localhost:5173", "http://localhost:3000"},
        allowedHeaders = "*",
        allowCredentials = "true",
        methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
                RequestMethod.DELETE, RequestMethod.OPTIONS, RequestMethod.HEAD}
)
public class ChatController {
    
    private final ChatService chatService;
    
    // Cache for providers to avoid repeated availability checks
    private final Map<String, List<ProviderInfo>> providersCache = new ConcurrentHashMap<>();
    private volatile long lastCacheUpdate = 0;
    private static final long CACHE_TTL_MS = 30000; // 30 seconds cache
    
    @PostMapping(value = "/message", consumes = "application/json", produces = "application/json")
    public ResponseEntity<ChatResponse> sendMessage(@RequestBody ChatRequest request) {
        System.out.println("🔍 MESSAGE ENDPOINT HIT!");
        System.out.println("🔍 REQUEST RECEIVED: " + request.toString());
        System.out.println("🔍 REQUEST HASHCODE: " + request.hashCode());
        System.out.println("🔍 REQUEST CLASS: " + request.getClass().getName());
        log.info("Received chat request: provider={}, model={}, message={}", 
                request.getProvider(), request.getModel(), request.getMessage());
        
        // Debug logging for API keys
        System.out.println("🔍 ChatController Debug - Received Request:");
        System.out.println("   Provider: " + request.getProvider());
        System.out.println("   Message: " + request.getMessage());
        System.out.println("   geminiApiKey: " + (request.getGeminiApiKey() != null ? request.getGeminiApiKey().substring(0, Math.min(8, request.getGeminiApiKey().length())) + "..." : "NULL"));
        System.out.println("   openaiApiKey: " + (request.getOpenaiApiKey() != null ? request.getOpenaiApiKey().substring(0, Math.min(8, request.getOpenaiApiKey().length())) + "..." : "NULL"));
        System.out.println("   groqApiKey: " + (request.getGroqApiKey() != null ? request.getGroqApiKey().substring(0, Math.min(8, request.getGroqApiKey().length())) + "..." : "NULL"));
        System.out.println("   Request object hash: " + request.hashCode());
        System.out.println("   Request object class: " + request.getClass().getName());
        
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
            
            System.out.println("🔍 About to call ChatService.generateResponse");
            System.out.println("🔍 Request before ChatService: " + request.toString());
            System.out.println("🔍 geminiApiKey before ChatService: " + (request.getGeminiApiKey() != null ? request.getGeminiApiKey().substring(0, Math.min(8, request.getGeminiApiKey().length())) + "..." : "NULL"));
            
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
        
        // Check if cache is still valid
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCacheUpdate < CACHE_TTL_MS && providersCache.containsKey("providers")) {
            log.debug("Returning cached providers");
            return ResponseEntity.ok(providersCache.get("providers"));
        }
        
        // Fetch fresh data and update cache
        List<ProviderInfo> providers = chatService.getAvailableProviders();
        providersCache.put("providers", providers);
        lastCacheUpdate = currentTime;
        
        log.info("Providers fetched and cached: {} providers", providers.size());
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
            // Force refresh by clearing cache
            providersCache.clear();
            lastCacheUpdate = 0;
            
            List<ProviderInfo> providers = chatService.getAvailableProviders();
            response.put("status", "success");
            response.put("providers", providers);
            response.put("count", providers.size());
            response.put("cache_cleared", true);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error testing providers", e);
            response.put("status", "error");
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    @PostMapping("/providers/refresh")
    public ResponseEntity<Map<String, Object>> refreshProviders() {
        log.info("Refreshing providers cache");
        Map<String, Object> response = new HashMap<>();
        try {
            // Clear cache to force refresh
            providersCache.clear();
            lastCacheUpdate = 0;
            
            // Fetch fresh data
            List<ProviderInfo> providers = chatService.getAvailableProviders();
            providersCache.put("providers", providers);
            lastCacheUpdate = System.currentTimeMillis();
            
            response.put("status", "success");
            response.put("message", "Providers cache refreshed");
            response.put("count", providers.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error refreshing providers", e);
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
    
    @PostMapping(value = "/test-raw-json", consumes = "application/json", produces = "application/json")
    public ResponseEntity<Map<String, Object>> testRawJson(@RequestBody Map<String, Object> rawRequest) {
        System.out.println("🔍 TEST-RAW-JSON ENDPOINT HIT!");
        System.out.println("🔍 RAW REQUEST: " + rawRequest);
        System.out.println("🔍 RAW REQUEST KEYS: " + rawRequest.keySet());
        
        Map<String, Object> response = new HashMap<>();
        response.put("rawRequest", rawRequest);
        response.put("keys", rawRequest.keySet());
        return ResponseEntity.ok(response);
    }
    
    @PostMapping(value = "/debug-request", consumes = "application/json", produces = "application/json")
    public ResponseEntity<Map<String, Object>> debugRequest(@RequestBody ChatRequest request) {
        Map<String, Object> response = new HashMap<>();
        response.put("provider", request.getProvider());
        response.put("message", request.getMessage());
        response.put("geminiApiKey", request.getGeminiApiKey() != null ? request.getGeminiApiKey().substring(0, Math.min(8, request.getGeminiApiKey().length())) + "..." : "NULL");
        response.put("openaiApiKey", request.getOpenaiApiKey() != null ? request.getOpenaiApiKey().substring(0, Math.min(8, request.getOpenaiApiKey().length())) + "..." : "NULL");
        response.put("groqApiKey", request.getGroqApiKey() != null ? request.getGroqApiKey().substring(0, Math.min(8, request.getGroqApiKey().length())) + "..." : "NULL");
        response.put("requestHash", request.hashCode());
        response.put("requestClass", request.getClass().getName());
        response.put("requestToString", request.toString());
        return ResponseEntity.ok(response);
    }
    
    @PostMapping(value = "/test-api-keys", consumes = "application/json", produces = "application/json")
    public ResponseEntity<Map<String, Object>> testApiKeys(@RequestBody ChatRequest request) {
        System.out.println("🔍 TEST-API-KEYS ENDPOINT HIT!");
        System.out.println("🔍 TEST-API-KEYS REQUEST: " + request.toString());
        System.out.println("🔍 TEST-API-KEYS geminiApiKey: " + (request.getGeminiApiKey() != null ? request.getGeminiApiKey().substring(0, Math.min(8, request.getGeminiApiKey().length())) + "..." : "NULL"));
        log.info("Testing API keys from request: provider={}", request.getProvider());
        
        Map<String, Object> response = new HashMap<>();
        response.put("provider", request.getProvider());
        response.put("openaiApiKey", request.getOpenaiApiKey() != null ? request.getOpenaiApiKey().substring(0, Math.min(8, request.getOpenaiApiKey().length())) + "..." : "NOT SET");
        response.put("claudeApiKey", request.getClaudeApiKey() != null ? request.getClaudeApiKey().substring(0, Math.min(8, request.getClaudeApiKey().length())) + "..." : "NOT SET");
        response.put("groqApiKey", request.getGroqApiKey() != null ? request.getGroqApiKey().substring(0, Math.min(8, request.getGroqApiKey().length())) + "..." : "NOT SET");
        response.put("geminiApiKey", request.getGeminiApiKey() != null ? request.getGeminiApiKey().substring(0, Math.min(8, request.getGeminiApiKey().length())) + "..." : "NOT SET");
        response.put("openrouterApiKey", request.getOpenrouterApiKey() != null ? request.getOpenrouterApiKey().substring(0, Math.min(8, request.getOpenrouterApiKey().length())) + "..." : "NOT SET");
        response.put("huggingfaceApiKey", request.getHuggingfaceApiKey() != null ? request.getHuggingfaceApiKey().substring(0, Math.min(8, request.getHuggingfaceApiKey().length())) + "..." : "NOT SET");
        
        return ResponseEntity.ok(response);
    }
}
