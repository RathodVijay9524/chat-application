package com.vijay.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Server-Sent Events (SSE) transport implementation of Universal MCP Client
 */
@Slf4j
public class SseMcpClient extends UniversalMcpClient {
    
    private final String baseUrl;
    private final Map<String, String> headers;
    private final AtomicLong idCounter = new AtomicLong(1);
    private final Map<Long, CompletableFuture<JsonNode>> pendingRequests = new ConcurrentHashMap<>();
    private BufferedReader sseReader;
    private Thread sseListenerThread;
    private volatile boolean connected = false;
    
    public SseMcpClient(String name, String baseUrl, Map<String, String> headers) {
        super(name, "SSE");
        this.baseUrl = baseUrl;
        this.headers = headers != null ? headers : Map.of();
    }
    
    @Override
    public void connect() throws Exception {
        if (connected) {
            log.debug("SSE MCP client {} already connected", name);
            return;
        }
        
        log.info("🔌 Connecting SSE MCP client: {}", name);
        
        URL url = new URL(baseUrl + "/events");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "text/event-stream");
        connection.setRequestProperty("Cache-Control", "no-cache");
        
        // Add custom headers
        headers.forEach(connection::setRequestProperty);
        
        connection.connect();
        
        if (connection.getResponseCode() == 200) {
            sseReader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            startSseListener();
            connected = true;
            log.info("✅ SSE MCP client {} connected", name);
        } else {
            throw new Exception("Failed to connect SSE client: HTTP " + connection.getResponseCode());
        }
    }
    
    private void startSseListener() {
        sseListenerThread = new Thread(() -> {
            try {
                String line;
                StringBuilder eventData = new StringBuilder();
                
                while (connected && (line = sseReader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        eventData.append(line.substring(6));
                    } else if (line.isEmpty() && eventData.length() > 0) {
                        // End of event, process data
                        processSseEvent(eventData.toString());
                        eventData.setLength(0);
                    }
                }
            } catch (Exception e) {
                if (connected) {
                    log.error("SSE listener error for {}: {}", name, e.getMessage());
                }
            }
        });
        sseListenerThread.setDaemon(true);
        sseListenerThread.start();
    }
    
    private void processSseEvent(String data) {
        try {
            JsonNode event = mapper.readTree(data);
            
            if (event.has("id")) {
                long id = event.get("id").asLong();
                CompletableFuture<JsonNode> future = pendingRequests.remove(id);
                if (future != null) {
                    future.complete(event);
                }
            }
        } catch (Exception e) {
            log.error("Failed to process SSE event for {}: {}", name, e.getMessage());
        }
    }
    
    @Override
    public void disconnect() {
        if (connected) {
            log.info("🔌 Disconnecting SSE MCP client: {}", name);
            connected = false;
            
            if (sseListenerThread != null) {
                sseListenerThread.interrupt();
            }
            
            if (sseReader != null) {
                try {
                    sseReader.close();
                } catch (Exception e) {
                    log.debug("Error closing SSE reader: {}", e.getMessage());
                }
            }
            
            initialized = false;
        }
    }
    
    @Override
    public boolean isConnected() {
        return connected;
    }
    
    @Override
    public void initialize() throws Exception {
        if (initialized) {
            return;
        }
        
        if (!isConnected()) {
            connect();
        }
        
        log.info("🚀 Initializing SSE MCP client: {}", name);
        
        try {
            Map<String, Object> initRequest = Map.of(
                "jsonrpc", "2.0",
                "id", idCounter.getAndIncrement(),
                "method", "initialize",
                "params", Map.of(
                    "protocolVersion", "2024-11-05",
                    "capabilities", Map.of("tools", Map.of()),
                    "clientInfo", Map.of(
                        "name", "Universal SSE MCP Client",
                        "version", "1.0.0"
                    )
                )
            );
            
            JsonNode response = sendRequest(initRequest);
            if (response != null) {
                initialized = true;
                log.info("✅ SSE MCP client {} initialized successfully", name);
            } else {
                throw new Exception("No response from SSE MCP server");
            }
        } catch (Exception e) {
            log.warn("⚠️ SSE MCP initialization failed for {}: {}", name, e.getMessage());
            initialized = true; // Continue with fallback
        }
    }
    
    @Override
    public List<Object> listTools() {
        log.info("🔍 Listing tools from SSE MCP server: {}", name);
        
        if (!isConnected()) {
            log.warn("⚠️ SSE MCP client {} not connected, using fallback tools", name);
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
            log.debug("SSE tools/list failed for {}: {}", name, e.getMessage());
        }
        
        log.info("📦 Using fallback tools for SSE MCP server: {}", name);
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
            log.info("✅ SSE MCP server {} provides {} tools", name, tools.size());
            return tools;
        }
        
        if (result.isArray()) {
            List<Object> tools = mapper.convertValue(result, new TypeReference<List<Object>>() {});
            log.info("✅ SSE MCP server {} provides {} tools (alternative format)", name, tools.size());
            return tools;
        }
        
        return List.of();
    }
    
    @Override
    public Object callTool(String toolName, Map<String, Object> arguments) {
        log.info("🛠️ Calling tool '{}' on SSE MCP server: {}", toolName, name);
        
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
                "error", "No result from SSE MCP server",
                "transport", "SSE"
            );
            
        } catch (Exception e) {
            log.error("Failed to call tool '{}' on SSE server {}: {}", toolName, name, e.getMessage());
            return Map.of(
                "success", false,
                "error", e.getMessage(),
                "transport", "SSE"
            );
        }
    }
    
    @Override
    protected JsonNode sendRequest(Map<String, Object> request) throws Exception {
        if (!isConnected()) {
            throw new Exception("SSE MCP client not connected");
        }
        
        long id = (Long) request.get("id");
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pendingRequests.put(id, future);
        
        try {
            // Send HTTP POST request
            URL url = new URL(baseUrl + "/request");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            
            // Add custom headers
            headers.forEach(connection::setRequestProperty);
            
            String json = mapper.writeValueAsString(request);
            log.debug("📤 Sending SSE request to {}: {}", name, json);
            
            connection.getOutputStream().write(json.getBytes("UTF-8"));
            connection.getOutputStream().flush();
            
            if (connection.getResponseCode() != 200) {
                throw new Exception("HTTP error: " + connection.getResponseCode());
            }
            
            // Wait for response via SSE
            return future.get(30, java.util.concurrent.TimeUnit.SECONDS);
            
        } catch (Exception e) {
            pendingRequests.remove(id);
            throw e;
        }
    }
}
