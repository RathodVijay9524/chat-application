package com.vijay.controller;

import io.modelcontextprotocol.client.McpSyncClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/mcp")
public class MCPToolsController {

    @Autowired
    private SyncMcpToolCallbackProvider mcpToolCallbackProvider;

    @Autowired(required = false)
    private List<McpSyncClient> mcpSyncClients;

    @GetMapping("/tools")
    public Map<String, Object> getAvailableTools() {
        log.info("Getting available MCP tools");
        
        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> tools = new ArrayList<>();
        
        try {
            // Get tools from MCP clients directly
            if (mcpSyncClients == null || mcpSyncClients.isEmpty()) {
                log.info("No MCP clients available");
            } else {
                for (McpSyncClient client : mcpSyncClients) {
                    try {
                        // Get tools from the MCP client
                        var mcpTools = client.listTools();
                        if (mcpTools != null && mcpTools.tools() != null) {
                            for (var tool : mcpTools.tools()) {
                                Map<String, Object> toolInfo = new HashMap<>();
                                toolInfo.put("name", tool.name());
                                toolInfo.put("description", tool.description());
                                toolInfo.put("inputSchema", tool.inputSchema());
                                toolInfo.put("clientId", client.toString());
                                tools.add(toolInfo);
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Error getting tools from MCP client {}: {}", client, e.getMessage());
                    }
                }
            }
            
            // If no tools found from MCP clients, show callback info
            if (tools.isEmpty()) {
                var toolCallbacks = mcpToolCallbackProvider.getToolCallbacks();
                for (var toolCallback : toolCallbacks) {
                    Map<String, Object> toolInfo = new HashMap<>();
                    toolInfo.put("name", toolCallback.getClass().getSimpleName());
                    toolInfo.put("description", "MCP Tool Callback");
                    toolInfo.put("type", toolCallback.getClass().getName());
                    toolInfo.put("status", "callback");
                    tools.add(toolInfo);
                }
            }
            
            response.put("status", "success");
            response.put("totalTools", tools.size());
            response.put("tools", tools);
            response.put("mcpClients", mcpSyncClients != null ? mcpSyncClients.size() : 0);
            
            log.info("Found {} MCP tools", tools.size());
            
        } catch (Exception e) {
            log.error("Error getting MCP tools: {}", e.getMessage(), e);
            response.put("status", "error");
            response.put("message", e.getMessage());
            response.put("tools", new ArrayList<>());
        }
        
        return response;
    }

    @GetMapping("/tools-status")
    public Map<String, Object> getMCPStatus() {
        log.info("Getting MCP status");
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            response.put("status", "connected");
            response.put("mcpClients", mcpSyncClients != null ? mcpSyncClients.size() : 0);
            response.put("toolCallbackProvider", mcpToolCallbackProvider != null ? "available" : "not available");
            
            // Get basic info about MCP clients
            List<Map<String, Object>> clientInfo = new ArrayList<>();
            if (mcpSyncClients != null && !mcpSyncClients.isEmpty()) {
                for (McpSyncClient client : mcpSyncClients) {
                    Map<String, Object> info = new HashMap<>();
                    info.put("connected", true);
                    info.put("clientClass", client.getClass().getSimpleName());
                    clientInfo.add(info);
                }
            }
            response.put("clients", clientInfo);
            
        } catch (Exception e) {
            log.error("Error getting MCP status: {}", e.getMessage(), e);
            response.put("status", "error");
            response.put("message", e.getMessage());
        }
        
        return response;
    }
}