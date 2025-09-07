package com.vijay.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for dynamically updating MCP server configurations
 * This allows us to add/remove MCP servers at runtime and have them
 * properly injected into AI models
 */
@Slf4j
@Service
public class DynamicMcpConfigurationService {
    
    @Autowired
    private ConfigurableEnvironment environment;
    
    @Autowired
    private ApplicationContext applicationContext;
    
    private final Map<String, Map<String, Object>> dynamicConfigs = new ConcurrentHashMap<>();
    
    /**
     * Add a dynamic MCP server configuration
     */
    public boolean addDynamicMcpServer(String serverId, String type, Map<String, Object> config) {
        try {
            log.info("Adding dynamic MCP server configuration: {} (type: {})", serverId, type);
            
            // Store the configuration
            Map<String, Object> serverConfig = new HashMap<>();
            serverConfig.put("type", type);
            serverConfig.put("config", config);
            serverConfig.put("enabled", true);
            serverConfig.put("timestamp", System.currentTimeMillis());
            
            dynamicConfigs.put(serverId, serverConfig);
            
            // Update Spring environment with new MCP server configuration
            updateSpringEnvironment(serverId, type, config);
            
            log.info("✅ Dynamic MCP server configuration added: {}", serverId);
            return true;
            
        } catch (Exception e) {
            log.error("Error adding dynamic MCP server configuration {}: {}", serverId, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Remove a dynamic MCP server configuration
     */
    public boolean removeDynamicMcpServer(String serverId) {
        try {
            log.info("Removing dynamic MCP server configuration: {}", serverId);
            
            if (dynamicConfigs.remove(serverId) != null) {
                // Remove from Spring environment
                removeFromSpringEnvironment(serverId);
                
                log.info("✅ Dynamic MCP server configuration removed: {}", serverId);
                return true;
            } else {
                log.warn("Dynamic MCP server configuration not found: {}", serverId);
                return false;
            }
            
        } catch (Exception e) {
            log.error("Error removing dynamic MCP server configuration {}: {}", serverId, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Update Spring environment with new MCP server configuration
     */
    private void updateSpringEnvironment(String serverId, String type, Map<String, Object> config) {
        try {
            Map<String, Object> properties = new HashMap<>();
            
            if ("STDIO".equals(type)) {
                // Add STDIO configuration
                String command = (String) config.get("command");
                @SuppressWarnings("unchecked")
                java.util.List<String> args = (java.util.List<String>) config.get("args");
                String workingDirectory = (String) config.get("workingDirectory");
                @SuppressWarnings("unchecked")
                Map<String, String> environment = (Map<String, String>) config.get("environment");
                
                if (command != null) {
                    properties.put("spring.ai.mcp.client.stdio.connections." + serverId + ".command", command);
                    
                    if (args != null && !args.isEmpty()) {
                        for (int i = 0; i < args.size(); i++) {
                            properties.put("spring.ai.mcp.client.stdio.connections." + serverId + ".args[" + i + "]", args.get(i));
                        }
                    }
                    
                    if (workingDirectory != null) {
                        properties.put("spring.ai.mcp.client.stdio.connections." + serverId + ".workingDirectory", workingDirectory);
                    }
                    
                    if (environment != null) {
                        for (Map.Entry<String, String> entry : environment.entrySet()) {
                            properties.put("spring.ai.mcp.client.stdio.connections." + serverId + ".env." + entry.getKey(), entry.getValue());
                        }
                    }
                }
                
            } else if ("SSE".equals(type)) {
                // Add SSE configuration
                String url = (String) config.get("url");
                String endpoint = (String) config.getOrDefault("endpoint", "/sse");
                String messageEndpoint = (String) config.getOrDefault("messageEndpoint", "/mcp/message");
                
                if (url != null) {
                    properties.put("spring.ai.mcp.client.sse.connections." + serverId + ".url", url);
                    properties.put("spring.ai.mcp.client.sse.connections." + serverId + ".endpoint", endpoint);
                    properties.put("spring.ai.mcp.client.sse.connections." + serverId + ".messageEndpoint", messageEndpoint);
                }
            }
            
            // Add properties to Spring environment
            if (!properties.isEmpty()) {
                MapPropertySource propertySource = new MapPropertySource("dynamic-mcp-" + serverId, properties);
                environment.getPropertySources().addLast(propertySource);
                
                log.info("Added {} properties to Spring environment for server: {}", properties.size(), serverId);
            }
            
        } catch (Exception e) {
            log.error("Error updating Spring environment for server {}: {}", serverId, e.getMessage(), e);
        }
    }
    
    /**
     * Remove configuration from Spring environment
     */
    private void removeFromSpringEnvironment(String serverId) {
        try {
            String propertySourceName = "dynamic-mcp-" + serverId;
            environment.getPropertySources().remove(propertySourceName);
            
            log.info("Removed property source from Spring environment: {}", propertySourceName);
            
        } catch (Exception e) {
            log.error("Error removing from Spring environment for server {}: {}", serverId, e.getMessage(), e);
        }
    }
    
    /**
     * Get all dynamic MCP server configurations
     */
    public Map<String, Map<String, Object>> getDynamicConfigs() {
        return new HashMap<>(dynamicConfigs);
    }
    
    /**
     * Check if a dynamic MCP server configuration exists
     */
    public boolean hasDynamicConfig(String serverId) {
        return dynamicConfigs.containsKey(serverId);
    }
    
    /**
     * Get dynamic MCP server configuration
     */
    public Map<String, Object> getDynamicConfig(String serverId) {
        return dynamicConfigs.get(serverId);
    }
}
