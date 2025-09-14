package com.vijay.mcp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Factory for creating Universal MCP Clients based on transport type
 */
@Slf4j
@Component
public class UniversalMcpClientFactory {
    
    /**
     * Create a Universal MCP Client based on transport type
     */
    public UniversalMcpClient createClient(String name, String transportType, Map<String, Object> config) {
        log.info("🏭 Creating Universal MCP Client: {} ({})", name, transportType);
        
        switch (transportType.toUpperCase()) {
            case "STDIO":
                return createStdioClient(name, config);
            case "SSE":
                return createSseClient(name, config);
            case "WEBSOCKET":
            case "WS":
                return createWebSocketClient(name, config);
            case "HTTP":
            case "REST":
                return createHttpClient(name, config);
            default:
                throw new IllegalArgumentException("Unsupported transport type: " + transportType);
        }
    }
    
    private UniversalMcpClient createStdioClient(String name, Map<String, Object> config) {
        String command = (String) config.get("command");
        log.info("📡 Creating new universal STDIO MCP client: {} -> {}", name, command);
        return new StdioMcpClient(name, config);
    }
    
    private SseMcpClient createSseClient(String name, Map<String, Object> config) {
        String baseUrl = (String) config.get("baseUrl");
        @SuppressWarnings("unchecked")
        Map<String, String> headers = (Map<String, String>) config.getOrDefault("headers", Map.of());
        
        log.info("📡 Creating SSE MCP client: {} -> {}", name, baseUrl);
        return new SseMcpClient(name, baseUrl, headers);
    }
    
    private WebSocketMcpClient createWebSocketClient(String name, Map<String, Object> config) {
        String wsUrl = (String) config.get("wsUrl");
        @SuppressWarnings("unchecked")
        Map<String, String> headers = (Map<String, String>) config.getOrDefault("headers", Map.of());
        
        log.info("📡 Creating WebSocket MCP client: {} -> {}", name, wsUrl);
        return new WebSocketMcpClient(name, wsUrl, headers);
    }
    
    private HttpMcpClient createHttpClient(String name, Map<String, Object> config) {
        String baseUrl = (String) config.get("baseUrl");
        @SuppressWarnings("unchecked")
        Map<String, String> headers = (Map<String, String>) config.getOrDefault("headers", Map.of());
        
        log.info("📡 Creating HTTP MCP client: {} -> {}", name, baseUrl);
        return new HttpMcpClient(name, baseUrl, headers);
    }
    
    /**
     * Auto-detect transport type from configuration
     */
    public String detectTransportType(Map<String, Object> config) {
        if (config.containsKey("command")) {
            return "STDIO";
        } else if (config.containsKey("wsUrl")) {
            return "WEBSOCKET";
        } else if (config.containsKey("baseUrl")) {
            String baseUrl = (String) config.get("baseUrl");
            if (baseUrl.startsWith("ws://") || baseUrl.startsWith("wss://")) {
                return "WEBSOCKET";
            } else if (baseUrl.contains("/events") || config.containsKey("sseEndpoint")) {
                return "SSE";
            } else {
                return "HTTP";
            }
        }
        
        throw new IllegalArgumentException("Cannot detect transport type from configuration: " + config);
    }
}
