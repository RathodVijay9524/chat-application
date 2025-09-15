package com.vijay.controller;

import com.vijay.dto.McpServerConfig;
import com.vijay.mcp.UniversalMcpClient;
import com.vijay.service.DynamicMcpServerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/mcp-servers")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"}, allowedHeaders = "*", allowCredentials = "true", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS, RequestMethod.HEAD})
public class McpServerController {

    @Autowired
    private DynamicMcpServerService mcpServerService;

    @GetMapping
    public ResponseEntity<List<McpServerConfig>> getAllServers() {
        return ResponseEntity.ok(mcpServerService.getAllServers());
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
            var client = mcpServerService.getActiveClients().get(serverId);
            if (client == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "No active client found for serverId: " + serverId));
            }

            if (client instanceof UniversalMcpClient) {
                UniversalMcpClient universalClient = (UniversalMcpClient) client;
                List<Object> tools = universalClient.getToolsWithCache(); // Use cached version for performance
                return ResponseEntity.ok(Map.of(
                    "serverId", serverId,
                    "serverName", universalClient.getName(),
                    "count", tools.size(),
                    "tools", tools,
                    "cached", true // Indicate this is from cache
                ));
            }

            return ResponseEntity.badRequest().body(Map.of("message", "Client is not a UniversalMcpClient"));
        } catch (Exception e) {
            log.error("Error getting tools for server {}: {}", serverId, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("message", e.getMessage()));
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
}
