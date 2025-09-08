package com.vijay.controller;

import com.vijay.dto.McpServerConfig;
import com.vijay.service.DynamicMcpServerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/api/mcp-servers")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001"})
public class McpServerController {
    
    @Autowired
    private DynamicMcpServerService mcpServerService;
    
    /**
     * Get all MCP server configurations
     */
    @GetMapping
    public ResponseEntity<List<McpServerConfig>> getAllServers() {
        try {
            List<McpServerConfig> servers = mcpServerService.getAllServers();
            log.info("Retrieved {} MCP server configurations", servers.size());
            return ResponseEntity.ok(servers);
        } catch (Exception e) {
            log.error("Error retrieving MCP servers: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get server status (running/stopped)
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Boolean>> getServerStatus() {
        try {
            Map<String, Boolean> status = mcpServerService.getServerStatus();
            log.info("Retrieved status for {} MCP servers", status.size());
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Error retrieving server status: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Add a new MCP server
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> addServer(@RequestBody McpServerConfig config) {
        try {
            log.info("Adding MCP server: {} (type: {})", config.getName(), config.getTransportType());
            
            boolean success = mcpServerService.addServer(config);
            
            Map<String, Object> response = Map.of(
                "success", success,
                "message", success ? "Server added successfully" : "Failed to add server",
                "serverId", config.getId()
            );
            
            return success ? 
                ResponseEntity.ok(response) : 
                ResponseEntity.badRequest().body(response);
                
        } catch (Exception e) {
            log.error("Error adding MCP server: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Internal server error: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Start an MCP server
     */
    @PostMapping("/{serverId}/start")
    public ResponseEntity<Map<String, Object>> startServer(@PathVariable String serverId) {
        try {
            log.info("Starting MCP server: {}", serverId);
            
            boolean success = mcpServerService.startServer(serverId);
            
            Map<String, Object> response = Map.of(
                "success", success,
                "message", success ? "Server started successfully" : "Failed to start server",
                "serverId", serverId
            );
            
            return success ? 
                ResponseEntity.ok(response) : 
                ResponseEntity.badRequest().body(response);
                
        } catch (Exception e) {
            log.error("Error starting MCP server {}: {}", serverId, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Internal server error: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Stop an MCP server
     */
    @PostMapping("/{serverId}/stop")
    public ResponseEntity<Map<String, Object>> stopServer(@PathVariable String serverId) {
        try {
            log.info("Stopping MCP server: {}", serverId);
            
            boolean success = mcpServerService.stopServer(serverId);
            
            Map<String, Object> response = Map.of(
                "success", success,
                "message", success ? "Server stopped successfully" : "Failed to stop server",
                "serverId", serverId
            );
            
            return success ? 
                ResponseEntity.ok(response) : 
                ResponseEntity.badRequest().body(response);
                
        } catch (Exception e) {
            log.error("Error stopping MCP server {}: {}", serverId, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Internal server error: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Remove an MCP server
     */
    @DeleteMapping("/{serverId}")
    public ResponseEntity<Map<String, Object>> removeServer(@PathVariable String serverId) {
        try {
            log.info("Removing MCP server: {}", serverId);
            
            boolean success = mcpServerService.removeServer(serverId);
            
            Map<String, Object> response = Map.of(
                "success", success,
                "message", success ? "Server removed successfully" : "Failed to remove server",
                "serverId", serverId
            );
            
            return success ? 
                ResponseEntity.ok(response) : 
                ResponseEntity.badRequest().body(response);
                
        } catch (Exception e) {
            log.error("Error removing MCP server {}: {}", serverId, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "Internal server error: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Get available MCP tools from all servers
     */
    @GetMapping("/tools")
    public ResponseEntity<Map<String, Object>> getAvailableTools() {
        try {
            var toolCallbackProvider = mcpServerService.getToolCallbackProvider();
            if (toolCallbackProvider == null) {
                return ResponseEntity.ok(Map.of(
                    "tools", List.of(),
                    "count", 0,
                    "message", "No MCP servers available"
                ));
            }
            
            var toolCallbacks = toolCallbackProvider.getToolCallbacks();
            List<Map<String, String>> tools = new java.util.ArrayList<>();
            
            for (var toolCallback : toolCallbacks) {
                tools.add(Map.of(
                    "name", toolCallback.getToolDefinition().name(),
                    "description", toolCallback.getToolDefinition().description()
                ));
            }
            
            return ResponseEntity.ok(Map.of(
                "tools", tools,
                "count", tools.size(),
                "message", "Retrieved " + tools.size() + " MCP tools"
            ));
            
        } catch (Exception e) {
            log.error("Error retrieving MCP tools: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "tools", List.of(),
                "count", 0,
                "message", "Error retrieving tools: " + e.getMessage()
            ));
        }
    }
    
    /**
     * Test endpoint to verify the API is working
     */
    @GetMapping("/test")
    public ResponseEntity<Map<String, Object>> testEndpoint() {
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "message", "MCP Server Management API is working!",
            "timestamp", System.currentTimeMillis(),
            "availableEndpoints", List.of(
                "GET /api/mcp-servers - List all servers",
                "POST /api/mcp-servers - Add new server",
                "POST /api/mcp-servers/{id}/start - Start server",
                "POST /api/mcp-servers/{id}/stop - Stop server",
                "DELETE /api/mcp-servers/{id} - Remove server",
                "GET /api/mcp-servers/status - Get server status",
                "GET /api/mcp-servers/tools - Get available tools"
            )
        ));
    }
    
    /**
     * Check which tools are available to AI models
     */
    @GetMapping("/ai-tools")
    public ResponseEntity<Map<String, Object>> getAiAvailableTools() {
        try {
            var toolCallbackProvider = mcpServerService.getToolCallbackProvider();
            if (toolCallbackProvider == null) {
                return ResponseEntity.ok(Map.of(
                    "status", "no_tools",
                    "message", "No MCP tool callback provider available",
                    "tools", List.of(),
                    "count", 0
                ));
            }
            
            var toolCallbacks = toolCallbackProvider.getToolCallbacks();
            List<Map<String, Object>> tools = new ArrayList<>();
            
            // Add static tools
            for (var toolCallback : toolCallbacks) {
                Map<String, Object> toolInfo = new HashMap<>();
                toolInfo.put("name", toolCallback.getToolDefinition().name());
                toolInfo.put("description", toolCallback.getToolDefinition().description());
                toolInfo.put("type", "MCP_TOOL");
                tools.add(toolInfo);
            }
            
            // Add dynamic tools (real and simulated)
            List<String> activeServers = mcpServerService.getActiveServers();
            int dynamicToolsCount = 0;
            
            // Get real tools from active servers
            var activeClients = mcpServerService.getActiveClients();
            for (Object client : activeClients.values()) {
                if (client instanceof com.vijay.service.RealStdioMcpClient) {
                    try {
                        List<Object> realTools = ((com.vijay.service.RealStdioMcpClient) client).listTools();
                        for (Object tool : realTools) {
                            if (tool instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> toolMap = (Map<String, Object>) tool;
                                Map<String, Object> dynamicToolInfo = new HashMap<>();
                                dynamicToolInfo.put("name", toolMap.get("name"));
                                dynamicToolInfo.put("description", toolMap.get("description"));
                                dynamicToolInfo.put("type", "REAL_DYNAMIC_MCP_TOOL");
                                dynamicToolInfo.put("serverId", ((com.vijay.service.RealStdioMcpClient) client).getName());
                                dynamicToolInfo.put("inputSchema", toolMap.get("inputSchema"));
                                tools.add(dynamicToolInfo);
                                dynamicToolsCount++;
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Error getting tools from real client {}: {}", client, e.getMessage());
                    }
                }
                // Skip mock clients - they don't provide real tools
            }
            
            log.info("Total tools: {} ({} static + {} dynamic)", tools.size(), toolCallbacks.length, dynamicToolsCount);
            
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Tools available to AI models",
                "tools", tools,
                "count", tools.size(),
                "staticTools", toolCallbacks.length,
                "dynamicTools", dynamicToolsCount,
                "note", dynamicToolsCount > 0 ? "Static and dynamic tools are injected into all AI models!" : "Only static tools are available",
                "dynamicServers", activeServers.size(),
                "injectionStatus", dynamicToolsCount > 0 ? "Dynamic tools are now available to AI models!" : "No dynamic servers active"
            ));
            
        } catch (Exception e) {
            log.error("Error retrieving AI tools: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Error retrieving AI tools: " + e.getMessage(),
                "tools", List.of(),
                "count", 0
            ));
        }
    }
    
    /**
     * Get information about dynamic server injection
     */
    @GetMapping("/injection-status")
    public ResponseEntity<Map<String, Object>> getInjectionStatus() {
        try {
            Map<String, Object> status = new HashMap<>();
            
            // Get server status
            var serverStatus = mcpServerService.getServerStatus();
            var activeServers = mcpServerService.getActiveServers();
            
            status.put("totalServers", serverStatus.size());
            status.put("activeServers", activeServers.size());
            status.put("serverStatus", serverStatus);
            
            // Get tool count with de-duplication to avoid double-counting
            var toolCallbackProvider = mcpServerService.getToolCallbackProvider();
            int staticToolCount = 0;
            Set<String> knownToolNames = new HashSet<>();
            if (toolCallbackProvider != null) {
                var callbacks = toolCallbackProvider.getToolCallbacks();
                staticToolCount = callbacks.length; // Note: may already include dynamic tools injected into provider
                for (var cb : callbacks) {
                    try {
                        knownToolNames.add(cb.getToolDefinition().name());
                    } catch (Exception ignored) {
                        // ignore any malformed definitions
                    }
                }
            }
            
            // Calculate additional dynamic tools that are NOT already present in the callback provider
            int dynamicToolCount = 0;
            var activeClients = mcpServerService.getActiveClients();
            for (Object client : activeClients.values()) {
                if (client instanceof com.vijay.service.RealStdioMcpClient) {
                    try {
                        List<Object> realTools = ((com.vijay.service.RealStdioMcpClient) client).listTools();
                        for (Object tool : realTools) {
                            if (tool instanceof Map<?, ?> toolMap) {
                                Object nameObj = toolMap.get("name");
                                if (nameObj instanceof String name && !name.isBlank()) {
                                    if (!knownToolNames.contains(name)) {
                                        knownToolNames.add(name);
                                        dynamicToolCount++;
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Error getting tools from real client {}: {}", client, e.getMessage());
                    }
                }
                // Skip mock clients - they don't provide real tools
            }
            
            int totalToolCount = knownToolNames.size();
            
            status.put("availableTools", totalToolCount);
            status.put("staticTools", Math.max(0, totalToolCount - dynamicToolCount));
            status.put("dynamicTools", dynamicToolCount);
            
            // Injection status
            if (dynamicToolCount > 0) {
                status.put("injectionStatus", "Dynamic tools are now available to AI models!");
                status.put("solution", "Dynamic tools successfully injected!");
            } else {
                status.put("injectionStatus", "No dynamic servers active");
                status.put("solution", "Start dynamic servers to inject their tools");
            }
            
            return ResponseEntity.ok(status);
            
        } catch (Exception e) {
            log.error("Error getting injection status: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Error getting injection status: " + e.getMessage()
            ));
        }
    }
    
    
    private String findServerIdForClient(Object client) {
        // Try to find server ID by matching client name or type
        if (client instanceof com.vijay.service.RealStdioMcpClient) {
            return ((com.vijay.service.RealStdioMcpClient) client).getName();
        }
        return "unknown-server";
    }
}
