package com.vijay.controller;

import com.vijay.dto.McpServerConfig;
import com.vijay.service.DynamicMcpServerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/mcp-servers")
@CrossOrigin(
        origins = {"http://localhost:5173", "http://localhost:3000"},
        allowedHeaders = "*",
        allowCredentials = "true",
        methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
                RequestMethod.DELETE, RequestMethod.OPTIONS, RequestMethod.HEAD}
)
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

            // Add tools and categorize them as static or dynamic
            List<String> activeServers = mcpServerService.getActiveServers();
            int dynamicToolsCount = 0;
            int staticToolsCount = 0;

            for (var toolCallback : toolCallbacks) {
                Map<String, Object> toolInfo = new HashMap<>();
                toolInfo.put("name", toolCallback.getToolDefinition().name());
                toolInfo.put("description", toolCallback.getToolDefinition().description());
                
                if (toolCallback instanceof com.vijay.mcp.DynamicToolCallback) {
                    toolInfo.put("type", "DYNAMIC_MCP_TOOL");
                    dynamicToolsCount++;
                } else {
                    toolInfo.put("type", "STATIC_MCP_TOOL");
                    staticToolsCount++;
                }
                
                tools.add(toolInfo);
            }

            log.info("Total tools: {} ({} static + {} dynamic)", tools.size(), staticToolsCount, dynamicToolsCount);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Tools available to AI models",
                    "tools", tools,
                    "count", tools.size(),
                    "staticTools", staticToolsCount,
                    "dynamicTools", dynamicToolsCount,
                    "dynamicTimeouts", 0,
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
            
            // Get tool count from the enhanced provider (which already includes dynamic tools)
            var toolCallbackProvider = mcpServerService.getToolCallbackProvider();
            int totalToolCount = 0;
            int dynamicToolCount = 0;
            int staticToolCount = 0;
            
            if (toolCallbackProvider != null) {
                var callbacks = toolCallbackProvider.getToolCallbacks();
                totalToolCount = callbacks.length;
                
                // Count dynamic vs static tools by checking callback type
                for (var cb : callbacks) {
                    try {
                        String className = cb.getClass().getName();
                        log.debug("Checking callback type: {} for tool: {}", className, cb.getToolDefinition().name());
                        
                        if (className.contains("DynamicToolCallback")) {
                            dynamicToolCount++;
                            log.debug("Found dynamic tool: {}", cb.getToolDefinition().name());
                        } else {
                            staticToolCount++;
                            log.debug("Found static tool: {}", cb.getToolDefinition().name());
                        }
                    } catch (Exception e) {
                        staticToolCount++; // Default to static if we can't determine
                        log.warn("Error checking callback type: {}", e.getMessage());
                    }
                }
                
                log.info("Injection status tool count: {} total ({} static + {} dynamic)", totalToolCount, staticToolCount, dynamicToolCount);
            }
            
            // Get active server count
            var activeClients = mcpServerService.getActiveClients();
            int activeDynamicServers = 0;
            for (Object client : activeClients.values()) {
                if (client instanceof com.vijay.service.RealStdioMcpClient) {
                    com.vijay.service.RealStdioMcpClient realClient = (com.vijay.service.RealStdioMcpClient) client;
                    if (realClient.isProcessAlive()) {
                        activeDynamicServers++;
                    }
                }
            }
            
            status.put("availableTools", totalToolCount);
            status.put("staticTools", staticToolCount);
            status.put("dynamicTools", dynamicToolCount);
            status.put("activeDynamicServers", activeDynamicServers);
            status.put("dynamicTimeouts", 0); // No timeouts since we're using cached tools
            
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
    
    /**
     * Manually trigger tools/list against a running STDIO MCP server by serverId.
     * This is useful for testing from Postman.
     * Example: GET /api/mcp-servers/stdio/{serverId}/tools
     */
    @GetMapping("/stdio/{serverId}/tools")
    public ResponseEntity<Map<String, Object>> stdioListTools(@PathVariable String serverId) {
        try {
            var active = mcpServerService.getActiveClients();
            Object client = active.get(serverId);
            if (client == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "error",
                        "message", "No active client found for serverId: " + serverId,
                        "serverId", serverId
                ));
            }
            if (!(client instanceof com.vijay.service.RealStdioMcpClient)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "error",
                        "message", "Client for serverId is not a STDIO client",
                        "serverId", serverId,
                        "clientType", client.getClass().getName()
                ));
            }

            com.vijay.service.RealStdioMcpClient stdio = (com.vijay.service.RealStdioMcpClient) client;
            
            // Try to get tools from enhanced provider first (faster, cached)
            var toolCallbackProvider = mcpServerService.getToolCallbackProvider();
            java.util.List<Object> tools = new java.util.ArrayList<>();
            
            if (toolCallbackProvider != null) {
                var callbacks = toolCallbackProvider.getToolCallbacks();
                for (var cb : callbacks) {
                    try {
                        if (cb.getClass().getName().contains("DynamicToolCallback")) {
                            // This is a dynamic tool from our server
                            Map<String, Object> toolInfo = new HashMap<>();
                            toolInfo.put("name", cb.getToolDefinition().name());
                            toolInfo.put("description", cb.getToolDefinition().description());
                            toolInfo.put("type", "DYNAMIC_CACHED");
                            toolInfo.put("source", "enhanced_provider");
                            tools.add(toolInfo);
                        }
                    } catch (Exception ignored) {
                        // Skip malformed tools
                    }
                }
                
                if (!tools.isEmpty()) {
                    return ResponseEntity.ok(Map.of(
                            "status", "success",
                            "serverId", serverId,
                            "serverName", stdio.getName(),
                            "count", tools.size(),
                            "tools", tools,
                            "note", "Tools retrieved from enhanced provider cache (faster than direct MCP query)"
                    ));
                }
            }
            
            // Fallback to direct query with shorter timeout
            java.util.concurrent.CompletableFuture<java.util.List<Object>> fut =
                    java.util.concurrent.CompletableFuture.supplyAsync(stdio::listTools);
            try {
                tools = fut.get(5, java.util.concurrent.TimeUnit.SECONDS); // Reduced timeout
            } catch (java.util.concurrent.TimeoutException te) {
                fut.cancel(true);
                return ResponseEntity.ok(Map.of(
                        "status", "timeout",
                        "message", "FastMCP server not responding to direct queries (but tools are available via enhanced provider)",
                        "serverId", serverId,
                        "serverName", stdio.getName(),
                        "count", 0,
                        "tools", java.util.List.of(),
                        "note", "Use /api/mcp-servers/ai-tools to see all available tools including dynamic ones"
                ));
            }

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "serverId", serverId,
                    "serverName", stdio.getName(),
                    "count", tools != null ? tools.size() : 0,
                    "tools", tools
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
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
