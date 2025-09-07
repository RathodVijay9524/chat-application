package com.vijay.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.HashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class MCPService {

    private final WebClient.Builder webClientBuilder;
    
    @Value("${spring.ai.mcp.client.sse.connections.my-mcp-server.url:http://localhost:8081}")
    private String mcpServerUrl;

    /**
     * Connect to MCP server and check availability
     */
    public boolean connectToMCPServer(String serverName) {
        try {
            String serverUrl = getAvailableMCPServers().get(serverName);
            if (serverUrl == null) {
                log.warn("MCP server {} not found in configuration", serverName);
                return false;
            }
            
            WebClient webClient = webClientBuilder.baseUrl(serverUrl).build();
            
            // Test connection with a simple health check
            Mono<String> response = webClient.get()
                    .uri("/health")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(java.time.Duration.ofSeconds(5));
            
            response.block(); // Test the connection
            log.info("Successfully connected to MCP server: {}", serverName);
            return true;
            
        } catch (Exception e) {
            log.warn("Failed to connect to MCP server {}: {}", serverName, e.getMessage());
            return false;
        }
    }

    /**
     * Send message to MCP server
     */
    public Flux<String> sendToMCPServer(String serverName, String message) {
        try {
            String serverUrl = getAvailableMCPServers().get(serverName);
            if (serverUrl == null) {
                log.warn("MCP server {} not found in configuration", serverName);
                return Flux.empty();
            }
            
            WebClient webClient = webClientBuilder.baseUrl(serverUrl).build();
            
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("message", message);
            requestBody.put("timestamp", String.valueOf(System.currentTimeMillis()));
            
            return webClient.post()
                    .uri("/mcp/process")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .onErrorResume(throwable -> {
                        log.error("Error sending message to MCP server {}: {}", serverName, throwable.getMessage());
                        return Flux.just("Error: " + throwable.getMessage());
                    });
                    
        } catch (Exception e) {
            log.error("Failed to send message to MCP server {}: {}", serverName, e.getMessage());
            return Flux.just("Error: " + e.getMessage());
        }
    }

    /**
     * Get available MCP servers from Spring properties
     */
    public Map<String, String> getAvailableMCPServers() {
        // Read from Spring properties instead of hardcoding
        Map<String, String> servers = new HashMap<>();
        
        // Use the injected MCP server URL from properties
        servers.put("my-mcp-server", mcpServerUrl);
        
        return servers;
    }

    /**
     * Check if MCP server is available
     */
    public boolean isMCPServerAvailable(String serverName) {
        return connectToMCPServer(serverName);
    }
    
    /**
     * Get MCP server status with detailed information
     */
    public Map<String, Object> getMCPServerStatus(String serverName) {
        Map<String, Object> status = new HashMap<>();
        String serverUrl = getAvailableMCPServers().get(serverName);
        
        status.put("name", serverName);
        status.put("url", serverUrl);
        status.put("available", false);
        status.put("lastChecked", System.currentTimeMillis());
        
        if (serverUrl != null) {
            try {
                WebClient webClient = webClientBuilder.baseUrl(serverUrl).build();
                Mono<String> response = webClient.get()
                        .uri("")
                        .retrieve()
                        .bodyToMono(String.class)
                        .timeout(java.time.Duration.ofSeconds(3));
                
                String healthResponse = response.block();
                status.put("available", true);
                status.put("healthResponse", healthResponse);
                status.put("status", "healthy");
                
            } catch (Exception e) {
                status.put("available", false);
                status.put("error", e.getMessage());
                status.put("status", "unhealthy");
            }
        } else {
            status.put("error", "Server not configured");
            status.put("status", "not_configured");
        }
        
        return status;
    }
}
