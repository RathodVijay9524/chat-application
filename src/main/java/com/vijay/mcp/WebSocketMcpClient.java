package com.vijay.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.*;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * WebSocket transport implementation of Universal MCP Client using Spring WebSocket
 */
@Slf4j
public class WebSocketMcpClient extends UniversalMcpClient implements WebSocketHandler {
    
    private final String wsUrl;
    private final Map<String, String> headers;
    private final AtomicLong idCounter = new AtomicLong(1);
    private final Map<Long, CompletableFuture<JsonNode>> pendingRequests = new ConcurrentHashMap<>();
    
    private WebSocketSession session;
    private volatile boolean connected = false;
    private final WebSocketClient client = new StandardWebSocketClient();
    
    public WebSocketMcpClient(String name, String wsUrl, Map<String, String> headers) {
        super(name, "WebSocket");
        this.wsUrl = wsUrl;
        this.headers = headers != null ? headers : Map.of();
    }
    
    @Override
    public void connect() throws Exception {
        if (connected) {
            log.debug("WebSocket MCP client {} already connected", name);
            return;
        }
        
        log.info("🔌 Connecting WebSocket MCP client: {}", name);
        
        WebSocketHttpHeaders httpHeaders = new WebSocketHttpHeaders();
        headers.forEach(httpHeaders::add);
        
        session = client.execute(this, httpHeaders, URI.create(wsUrl)).get();
        connected = true;
        
        log.info("✅ WebSocket MCP client {} connected", name);
    }
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.debug("WebSocket connection established for MCP client: {}", name);
        this.session = session;
        connected = true;
    }
    
    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        String payload = message.getPayload().toString();
        log.debug("📥 Received WebSocket message from {}: {}", name, payload);
        
        try {
            JsonNode response = mapper.readTree(payload);
            
            if (response.has("id")) {
                long id = response.get("id").asLong();
                CompletableFuture<JsonNode> future = pendingRequests.remove(id);
                if (future != null) {
                    future.complete(response);
                }
            }
        } catch (Exception e) {
            log.error("Failed to process WebSocket message for {}: {}", name, e.getMessage());
        }
    }
    
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket transport error for MCP client {}: {}", name, exception.getMessage());
        connected = false;
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        log.info("WebSocket closed for MCP client {}: {}", name, closeStatus.toString());
        connected = false;
        this.session = null;
        initialized = false;
        
        // Complete any pending requests with error
        pendingRequests.values().forEach(future -> 
            future.completeExceptionally(new RuntimeException("WebSocket connection closed")));
        pendingRequests.clear();
    }
    
    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
    
    @Override
    public void disconnect() {
        if (connected && session != null) {
            log.info("🔌 Disconnecting WebSocket MCP client: {}", name);
            try {
                session.close();
            } catch (Exception e) {
                log.debug("Error closing WebSocket session: {}", e.getMessage());
            }
            connected = false;
            initialized = false;
        }
    }
    
    @Override
    public boolean isConnected() {
        return connected && session != null && session.isOpen();
    }
    
    @Override
    public void initialize() throws Exception {
        if (initialized) {
            return;
        }
        
        if (!isConnected()) {
            connect();
        }
        
        log.info("🚀 Initializing WebSocket MCP client: {}", name);
        
        try {
            Map<String, Object> initRequest = Map.of(
                "jsonrpc", "2.0",
                "id", idCounter.getAndIncrement(),
                "method", "initialize",
                "params", Map.of(
                    "protocolVersion", "2024-11-05",
                    "capabilities", Map.of("tools", Map.of()),
                    "clientInfo", Map.of(
                        "name", "Universal WebSocket MCP Client",
                        "version", "1.0.0"
                    )
                )
            );
            
            JsonNode response = sendRequest(initRequest);
            if (response != null) {
                initialized = true;
                log.info("✅ WebSocket MCP client {} initialized successfully", name);
            } else {
                throw new Exception("No response from WebSocket MCP server");
            }
        } catch (Exception e) {
            log.warn("⚠️ WebSocket MCP initialization failed for {}: {}", name, e.getMessage());
            initialized = true; // Continue with fallback
        }
    }
    
    @Override
    public List<Object> listTools() {
        log.info("🔍 Listing tools from WebSocket MCP server: {}", name);
        
        if (!isConnected()) {
            log.warn("⚠️ WebSocket MCP client {} not connected, using fallback tools", name);
            return createUniversalFallbackTools();
        }
        
        try {
            Map<String, Object> request = Map.of(
                "jsonrpc", "2.0",
                "id", idCounter.getAndIncrement(),
                "method", "tools/list",
                "params", Map.of()
            );
            
            JsonNode response = sendRequest(request);
            List<Object> tools = parseToolsResponse(response);
            
            if (!tools.isEmpty()) {
                return tools;
            }
        } catch (Exception e) {
            log.debug("WebSocket tools/list failed for {}: {}", name, e.getMessage());
        }
        
        log.info("📦 Using fallback tools for WebSocket MCP server: {}", name);
        return createUniversalFallbackTools();
    }
    
    private List<Object> parseToolsResponse(JsonNode response) {
        if (response == null) {
            return List.of();
        }
        
        JsonNode result = response.get("result");
        if (result == null) {
            return List.of();
        }
        
        JsonNode toolsNode = result.get("tools");
        if (toolsNode != null && toolsNode.isArray()) {
            List<Object> tools = mapper.convertValue(toolsNode, new TypeReference<List<Object>>() {});
            log.info("✅ WebSocket MCP server {} provides {} tools", name, tools.size());
            return tools;
        }
        
        if (result.isArray()) {
            List<Object> tools = mapper.convertValue(result, new TypeReference<List<Object>>() {});
            log.info("✅ WebSocket MCP server {} provides {} tools (alternative format)", name, tools.size());
            return tools;
        }
        
        return List.of();
    }
    
    @Override
    public Object callTool(String toolName, Map<String, Object> arguments) {
        log.info("🛠️ Calling tool '{}' on WebSocket MCP server: {}", toolName, name);
        
        try {
            Map<String, Object> request = Map.of(
                "jsonrpc", "2.0",
                "id", idCounter.getAndIncrement(),
                "method", "tools/call",
                "params", Map.of(
                    "name", toolName,
                    "arguments", arguments != null ? arguments : Map.of()
                )
            );
            
            JsonNode response = sendRequest(request);
            
            if (response != null && response.has("result")) {
                return response.get("result");
            }
            
            return Map.of(
                "success", false,
                "error", "No result from WebSocket MCP server",
                "transport", "WebSocket"
            );
            
        } catch (Exception e) {
            log.error("Failed to call tool '{}' on WebSocket server {}: {}", toolName, name, e.getMessage());
            return Map.of(
                "success", false,
                "error", e.getMessage(),
                "transport", "WebSocket"
            );
        }
    }
    
    @Override
    protected JsonNode sendRequest(Map<String, Object> request) throws Exception {
        if (!isConnected()) {
            throw new Exception("WebSocket MCP client not connected");
        }
        
        long id = (Long) request.get("id");
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pendingRequests.put(id, future);
        
        try {
            String json = mapper.writeValueAsString(request);
            log.debug("📤 Sending WebSocket request to {}: {}", name, json);
            
            session.sendMessage(new TextMessage(json));
            
            // Wait for response
            return future.get(30, TimeUnit.SECONDS);
            
        } catch (Exception e) {
            pendingRequests.remove(id);
            throw e;
        }
    }
}
