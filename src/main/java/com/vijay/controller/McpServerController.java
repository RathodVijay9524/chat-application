package com.vijay.controller;

import com.vijay.dto.McpServerConfig;
import com.vijay.mcp.UniversalMcpClient;
import com.vijay.service.DynamicMcpServerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/mcp-servers")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"}, allowedHeaders = "*", allowCredentials = "true", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS, RequestMethod.HEAD})
public class McpServerController {

    private final DynamicMcpServerService mcpServerService;
    
    public McpServerController(DynamicMcpServerService mcpServerService) {
        this.mcpServerService = mcpServerService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllServersSimple() {
        try {
            List<McpServerConfig> allServers = mcpServerService.getAllServers();
            Map<String, Object> activeClients = mcpServerService.getActiveClients();
            
            // Add status information to each server
            List<Map<String, Object>> serverList = new ArrayList<>();
            for (McpServerConfig server : allServers) {
                Map<String, Object> serverInfo = new HashMap<>();
                serverInfo.put("id", server.getId());
                serverInfo.put("name", server.getName());
                serverInfo.put("transportType", server.getTransportType());
                serverInfo.put("enabled", server.isEnabled());
                serverInfo.put("active", activeClients.containsKey(server.getId()));
                serverInfo.put("status", activeClients.containsKey(server.getId()) ? "RUNNING" : "STOPPED");
                
                // Add tool count if server is active
                if (activeClients.containsKey(server.getId())) {
                    try {
                        UniversalMcpClient client = (UniversalMcpClient) activeClients.get(server.getId());
                        if (client != null) {
                            List<Object> tools = client.getToolsWithCache();
                            serverInfo.put("toolCount", tools != null ? tools.size() : 0);
                        }
                    } catch (Exception e) {
                        serverInfo.put("toolCount", 0);
                        serverInfo.put("error", e.getMessage());
                    }
                } else {
                    serverInfo.put("toolCount", 0);
                }
                
                serverList.add(serverInfo);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("servers", serverList);
            response.put("totalCount", allServers.size());
            response.put("activeCount", activeClients.size());
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting all servers: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> addServer(@RequestBody McpServerConfig config) {
        boolean success = mcpServerService.addServer(config);
        Map<String, Object> response = Map.of("success", success, "message", success ? "Server added" : "Failed to add server", "serverId", config.getId());
        return success ? ResponseEntity.ok(response) : ResponseEntity.badRequest().body(response);
    }

    @PostMapping("/{serverId}/start")
    public ResponseEntity<Map<String, Object>> startServer(@PathVariable String serverId) {
        boolean success = mcpServerService.startServer(serverId);
        Map<String, Object> response = Map.of("success", success, "message", success ? "Server started" : "Failed to start server", "serverId", serverId);
        return success ? ResponseEntity.ok(response) : ResponseEntity.badRequest().body(response);
    }

    @PostMapping("/{serverId}/stop")
    public ResponseEntity<Map<String, Object>> stopServer(@PathVariable String serverId) {
        boolean success = mcpServerService.stopServer(serverId);
        Map<String, Object> response = Map.of("success", success, "message", success ? "Server stopped" : "Failed to stop server", "serverId", serverId);
        return success ? ResponseEntity.ok(response) : ResponseEntity.badRequest().body(response);
    }

    @DeleteMapping("/{serverId}")
    public ResponseEntity<Map<String, Object>> removeServer(@PathVariable String serverId) {
        boolean success = mcpServerService.removeServer(serverId);
        Map<String, Object> response = Map.of("success", success, "message", success ? "Server removed" : "Failed to remove server", "serverId", serverId);
        return success ? ResponseEntity.ok(response) : ResponseEntity.badRequest().body(response);
    }

    @GetMapping("/tools")
    public ResponseEntity<Map<String, Object>> getAvailableTools() {
        try {
            var toolCallbackProvider = mcpServerService.getToolCallbackProvider();
            if (toolCallbackProvider == null) {
                return ResponseEntity.ok(Map.of("tools", List.of(), "count", 0, "message", "No MCP servers available"));
            }

            var toolCallbacks = toolCallbackProvider.getToolCallbacks();
            List<Map<String, String>> tools = new java.util.ArrayList<>();
            for (var toolCallback : toolCallbacks) {
                tools.add(Map.of(
                    "name", toolCallback.getToolDefinition().name(),
                    "description", toolCallback.getToolDefinition().description()
                ));
            }

            return ResponseEntity.ok(Map.of("tools", tools, "count", tools.size(), "message", "Retrieved " + tools.size() + " MCP tools"));
        } catch (Exception e) {
            log.error("Error retrieving MCP tools: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("message", "Error retrieving tools: " + e.getMessage()));
        }
    }

    @GetMapping("/{serverId}/tools")
    public ResponseEntity<Map<String, Object>> getServerTools(@PathVariable String serverId) {
        try {
            // Decode URL-encoded server ID
            String decodedServerId = java.net.URLDecoder.decode(serverId, "UTF-8");
            log.info("Getting tools for server: {} (decoded: {})", serverId, decodedServerId);
            
            // Check if server exists in database first
            List<McpServerConfig> allServers = mcpServerService.getAllServers();
            boolean serverExists = allServers.stream()
                .anyMatch(server -> server.getId().equals(decodedServerId));
            
            if (!serverExists) {
                log.warn("Server not found in database: {}", decodedServerId);
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "Server not found",
                    "serverId", decodedServerId,
                    "message", "Server does not exist in database"
                ));
            }
            
            var client = mcpServerService.getActiveClients().get(decodedServerId);
            if (client == null) {
                log.warn("Server client not active: {}", decodedServerId);
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "Server not active",
                    "serverId", decodedServerId,
                    "message", "Server is not currently running"
                ));
            }

            if (client instanceof UniversalMcpClient) {
                UniversalMcpClient universalClient = (UniversalMcpClient) client;
                List<Object> tools = universalClient.getToolsWithCache(); // Use cached version for performance
                return ResponseEntity.ok(Map.of(
                    "serverId", decodedServerId,
                    "serverName", universalClient.getName(),
                    "count", tools.size(),
                    "tools", tools,
                    "cached", true // Indicate this is from cache
                ));
            }

            return ResponseEntity.badRequest().body(Map.of(
                "error", "Invalid client type",
                "serverId", decodedServerId,
                "message", "Client is not a UniversalMcpClient"
            ));
        } catch (Exception e) {
            log.error("Error getting tools for server {}: {}", serverId, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Internal server error",
                "serverId", serverId,
                "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/injection-status")
    public ResponseEntity<Map<String, Object>> getInjectionStatus() {
        try {
            Map<String, Object> status = new HashMap<>();
            var serverStatus = mcpServerService.getServerStatus();
            var activeClients = mcpServerService.getActiveClients();

            status.put("serverStatus", serverStatus);
            status.put("totalServers", serverStatus.size());
            status.put("activeServers", activeClients.size());

            var toolCallbackProvider = mcpServerService.getToolCallbackProvider();
            int totalToolCount = 0;
            int dynamicToolCount = 0;
            int staticToolCount = 0;

            if (toolCallbackProvider != null) {
                var callbacks = toolCallbackProvider.getToolCallbacks();
                totalToolCount = callbacks.length;
                for (var cb : callbacks) {
                    if (cb.getClass().getName().contains("DynamicToolCallback")) {
                        dynamicToolCount++;
                    } else {
                        staticToolCount++;
                    }
                }
            }

            long activeDynamicServers = activeClients.values().stream()
                .filter(c -> c instanceof UniversalMcpClient && ((UniversalMcpClient) c).isConnected())
                .count();

            status.put("availableTools", totalToolCount);
            status.put("staticTools", staticToolCount);
            status.put("dynamicTools", dynamicToolCount);
            status.put("activeDynamicServers", activeDynamicServers);

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
            return ResponseEntity.internalServerError().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/{serverId}/refresh-cache")
    public ResponseEntity<Map<String, Object>> refreshToolCache(@PathVariable String serverId) {
        try {
            var client = mcpServerService.getActiveClients().get(serverId);
            if (client == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "No active client found for serverId: " + serverId));
            }

            if (client instanceof UniversalMcpClient) {
                UniversalMcpClient universalClient = (UniversalMcpClient) client;
                universalClient.clearToolCache(); // Clear cache
                List<Object> tools = universalClient.getToolsWithCache(); // Force refresh
                return ResponseEntity.ok(Map.of(
                    "message", "Tool cache refreshed successfully",
                    "serverId", serverId,
                    "serverName", universalClient.getName(),
                    "count", tools.size(),
                    "tools", tools
                ));
            }

            return ResponseEntity.badRequest().body(Map.of("message", "Client is not a UniversalMcpClient"));
        } catch (Exception e) {
            log.error("Error refreshing tool cache for server {}: {}", serverId, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/refresh-all-tools")
    public ResponseEntity<Map<String, Object>> refreshAllTools() {
        try {
            log.info("🔄 Force refreshing all tool callbacks...");
            mcpServerService.forceRefreshToolCallbackProvider();
            
            var status = mcpServerService.getServerStatus();
            return ResponseEntity.ok(Map.of(
                "message", "All tool callbacks refreshed successfully",
                "status", status
            ));
        } catch (Exception e) {
            log.error("Error refreshing all tool callbacks: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getHealthStatus() {
        try {
            Map<String, Object> health = new HashMap<>();
            
            // Server status
            var serverStatus = mcpServerService.getServerStatus();
            var activeClients = mcpServerService.getActiveClients();
            
            // Tool callback provider status
            var toolProvider = mcpServerService.getToolCallbackProvider();
            int totalTools = 0;
            int staticTools = 0;
            int dynamicTools = 0;
            
            if (toolProvider != null) {
                var callbacks = toolProvider.getToolCallbacks();
                totalTools = callbacks.length;
                
                for (var callback : callbacks) {
                    if (callback instanceof com.vijay.mcp.DynamicToolCallback) {
                        dynamicTools++;
                    } else {
                        staticTools++;
                    }
                }
            }
            
            // Health metrics
            health.put("status", "healthy");
            health.put("timestamp", System.currentTimeMillis());
            health.put("servers", Map.of(
                "total", serverStatus.size(),
                "active", activeClients.size(),
                "status", serverStatus
            ));
            health.put("tools", Map.of(
                "total", totalTools,
                "static", staticTools,
                "dynamic", dynamicTools
            ));
            health.put("performance", Map.of(
                "uptime", System.currentTimeMillis() - System.getProperty("java.class.path").hashCode(),
                "memory", Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory(),
                "maxMemory", Runtime.getRuntime().maxMemory()
            ));
            
            return ResponseEntity.ok(health);
        } catch (Exception e) {
            log.error("Error getting health status: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "unhealthy",
                "error", e.getMessage(),
                "timestamp", System.currentTimeMillis()
            ));
        }
    }

    @PostMapping("/bulk-start")
    public ResponseEntity<Map<String, Object>> bulkStartServers(@RequestBody List<String> serverIds) {
        try {
            Map<String, Object> results = new HashMap<>();
            int successCount = 0;
            int failureCount = 0;
            
            for (String serverId : serverIds) {
                try {
                    boolean started = mcpServerService.startServer(serverId);
                    if (started) {
                        results.put(serverId, "started");
                        successCount++;
                    } else {
                        results.put(serverId, "failed");
                        failureCount++;
                    }
                } catch (Exception e) {
                    results.put(serverId, "error: " + e.getMessage());
                    failureCount++;
                }
            }
            
            return ResponseEntity.ok(Map.of(
                "message", "Bulk start completed",
                "success", successCount,
                "failures", failureCount,
                "results", results
            ));
        } catch (Exception e) {
            log.error("Error in bulk start: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/bulk-stop")
    public ResponseEntity<Map<String, Object>> bulkStopServers(@RequestBody List<String> serverIds) {
        try {
            Map<String, Object> results = new HashMap<>();
            int successCount = 0;
            int failureCount = 0;
            
            for (String serverId : serverIds) {
                try {
                    boolean stopped = mcpServerService.stopServer(serverId);
                    if (stopped) {
                        results.put(serverId, "stopped");
                        successCount++;
                    } else {
                        results.put(serverId, "failed");
                        failureCount++;
                    }
                } catch (Exception e) {
                    results.put(serverId, "error: " + e.getMessage());
                    failureCount++;
                }
            }
            
            return ResponseEntity.ok(Map.of(
                "message", "Bulk stop completed",
                "success", successCount,
                "failures", failureCount,
                "results", results
            ));
        } catch (Exception e) {
            log.error("Error in bulk stop: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        try {
            Map<String, Object> stats = mcpServerService.getServerStatistics();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error getting statistics: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/servers")
    public ResponseEntity<Map<String, Object>> getAllServers() {
        try {
            List<McpServerConfig> allServers = mcpServerService.getAllServers();
            Map<String, Object> activeClients = mcpServerService.getActiveClients();
            
            List<Map<String, Object>> serverList = new ArrayList<>();
            
            for (McpServerConfig server : allServers) {
                Map<String, Object> serverInfo = new HashMap<>();
                serverInfo.put("id", server.getId());
                serverInfo.put("name", server.getName());
                serverInfo.put("transportType", server.getTransportType());
                serverInfo.put("enabled", server.isEnabled());
                serverInfo.put("active", activeClients.containsKey(server.getId()));
                serverInfo.put("status", activeClients.containsKey(server.getId()) ? "RUNNING" : "STOPPED");
                
                // Add tool count if server is active
                if (activeClients.containsKey(server.getId())) {
                    try {
                        UniversalMcpClient client = (UniversalMcpClient) activeClients.get(server.getId());
                        if (client != null) {
                            List<Object> tools = client.getToolsWithCache();
                            serverInfo.put("toolCount", tools != null ? tools.size() : 0);
                        }
                    } catch (Exception e) {
                        serverInfo.put("toolCount", 0);
                        serverInfo.put("error", e.getMessage());
                    }
                } else {
                    serverInfo.put("toolCount", 0);
                }
                
                serverList.add(serverInfo);
            }
            
            return ResponseEntity.ok(Map.of(
                "servers", serverList,
                "totalCount", allServers.size(),
                "activeCount", activeClients.size(),
                "timestamp", System.currentTimeMillis()
            ));
        } catch (Exception e) {
            log.error("Error getting all servers: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/{serverId}/status")
    public ResponseEntity<Map<String, Object>> getServerStatus(@PathVariable String serverId) {
        try {
            String decodedServerId = java.net.URLDecoder.decode(serverId, "UTF-8");
            log.info("Getting status for server: {} (decoded: {})", serverId, decodedServerId);
            
            // Check if server exists in database
            List<McpServerConfig> allServers = mcpServerService.getAllServers();
            boolean serverExists = allServers.stream()
                .anyMatch(server -> server.getId().equals(decodedServerId));
            
            if (!serverExists) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "Server not found",
                    "serverId", decodedServerId,
                    "status", "NOT_FOUND"
                ));
            }
            
            Map<String, Object> activeClients = mcpServerService.getActiveClients();
            boolean isActive = activeClients.containsKey(decodedServerId);
            
            Map<String, Object> status = new HashMap<>();
            status.put("serverId", decodedServerId);
            status.put("active", isActive);
            status.put("status", isActive ? "RUNNING" : "STOPPED");
            status.put("timestamp", System.currentTimeMillis());
            
            if (isActive) {
                try {
                    UniversalMcpClient client = (UniversalMcpClient) activeClients.get(decodedServerId);
                    if (client != null) {
                        status.put("name", client.getName());
                        status.put("transportType", client.getTransportType());
                        status.put("connected", client.isConnected());
                        status.put("initialized", client.isInitialized());
                        
                        List<Object> tools = client.getToolsWithCache();
                        status.put("toolCount", tools != null ? tools.size() : 0);
                    }
                } catch (Exception e) {
                    status.put("error", e.getMessage());
                    status.put("toolCount", 0);
                }
            }
            
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Error getting server status for {}: {}", serverId, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Internal server error",
                "serverId", serverId,
                "message", e.getMessage()
            ));
        }
    }
}
