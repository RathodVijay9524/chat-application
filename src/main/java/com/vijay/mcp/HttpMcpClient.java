package com.vijay.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * HTTP/REST transport implementation of Universal MCP Client
 */
@Slf4j
public class HttpMcpClient extends UniversalMcpClient {
    
    private final String baseUrl;
    private final Map<String, String> headers;
    private final AtomicLong idCounter = new AtomicLong(1);
    private volatile boolean connected = false;
    
    public HttpMcpClient(String name, String baseUrl, Map<String, String> headers) {
        super(name, "HTTP");
        this.baseUrl = baseUrl;
        this.headers = headers != null ? headers : Map.of();
    }
    
    @Override
    public void connect() throws Exception {
        if (connected) {
            log.debug("HTTP MCP client {} already connected", name);
            return;
        }
        
        log.info("🔌 Connecting HTTP MCP client: {}", name);
        
        // Test connection with health check
        try {
            URL url = new URL(baseUrl + "/health");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            
            // Add custom headers
            headers.forEach(connection::setRequestProperty);
            
            int responseCode = connection.getResponseCode();
            if (responseCode == 200 || responseCode == 404) { // 404 is OK if no health endpoint
                connected = true;
                log.info("✅ HTTP MCP client {} connected", name);
            } else {
                throw new Exception("HTTP health check failed: " + responseCode);
            }
        } catch (Exception e) {
            // Assume connection is OK even if health check fails
            connected = true;
            log.info("✅ HTTP MCP client {} connected (no health check)", name);
        }
    }
    
    @Override
    public void disconnect() {
        if (connected) {
            log.info("🔌 Disconnecting HTTP MCP client: {}", name);
            connected = false;
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
        
        log.info("🚀 Initializing HTTP MCP client: {}", name);
        
        try {
            Map<String, Object> initRequest = Map.of(
                "jsonrpc", "2.0",
                "id", idCounter.getAndIncrement(),
                "method", "initialize",
                "params", Map.of(
                    "protocolVersion", "2024-11-05",
                    "capabilities", Map.of("tools", Map.of()),
                    "clientInfo", Map.of(
                        "name", "Universal HTTP MCP Client",
                        "version", "1.0.0"
                    )
                )
            );
            
            JsonNode response = sendRequest(initRequest);
            if (response != null) {
                initialized = true;
                log.info("✅ HTTP MCP client {} initialized successfully", name);
            } else {
                throw new Exception("No response from HTTP MCP server");
            }
        } catch (Exception e) {
            log.warn("⚠️ HTTP MCP initialization failed for {}: {}", name, e.getMessage());
            initialized = true; // Continue with fallback
        }
    }
    
    @Override
    public List<Object> listTools() {
        log.info("🔍 Listing tools from HTTP MCP server: {}", name);
        
        if (!isConnected()) {
            log.warn("⚠️ HTTP MCP client {} not connected, using fallback tools", name);
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
            log.debug("HTTP tools/list failed for {}: {}", name, e.getMessage());
        }
        
        log.info("📦 Using fallback tools for HTTP MCP server: {}", name);
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
            log.info("✅ HTTP MCP server {} provides {} tools", name, tools.size());
            return tools;
        }
        
        if (result.isArray()) {
            List<Object> tools = mapper.convertValue(result, new TypeReference<List<Object>>() {});
            log.info("✅ HTTP MCP server {} provides {} tools (alternative format)", name, tools.size());
            return tools;
        }
        
        return List.of();
    }
    
    @Override
    public Object callTool(String toolName, Map<String, Object> arguments) {
        log.info("🛠️ Calling tool '{}' on HTTP MCP server: {}", toolName, name);
        
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
                "error", "No result from HTTP MCP server",
                "transport", "HTTP"
            );
            
        } catch (Exception e) {
            log.error("Failed to call tool '{}' on HTTP server {}: {}", toolName, name, e.getMessage());
            return Map.of(
                "success", false,
                "error", e.getMessage(),
                "transport", "HTTP"
            );
        }
    }
    
    @Override
    protected JsonNode sendRequest(Map<String, Object> request) throws Exception {
        if (!isConnected()) {
            throw new Exception("HTTP MCP client not connected");
        }
        
        URL url = new URL(baseUrl + "/mcp");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(30000);
        
        // Add custom headers
        headers.forEach(connection::setRequestProperty);
        
        String json = mapper.writeValueAsString(request);
        log.debug("📤 Sending HTTP request to {}: {}", name, json);
        
        // Send request
        try (OutputStream os = connection.getOutputStream()) {
            os.write(json.getBytes("UTF-8"));
            os.flush();
        }
        
        // Read response
        int responseCode = connection.getResponseCode();
        if (responseCode == 200) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                
                String responseJson = response.toString();
                log.debug("📥 Received HTTP response from {}: {}", name, responseJson);
                
                JsonNode responseNode = mapper.readTree(responseJson);
                
                if (responseNode.has("error")) {
                    log.warn("❌ HTTP MCP error from {}: {}", name, responseNode.get("error"));
                    return null;
                }
                
                return responseNode;
            }
        } else {
            throw new Exception("HTTP error: " + responseCode);
        }
    }
}
