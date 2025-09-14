package com.vijay.service;

import com.vijay.dto.McpServerConfig;
import com.vijay.mcp.DynamicToolCallback;
import com.vijay.mcp.UniversalMcpClient;
import com.vijay.mcp.UniversalMcpClientFactory;

import io.modelcontextprotocol.client.McpSyncClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.lang.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.io.IOException;

@Slf4j
@Service
public class DynamicMcpServerService {
    
    private final Map<String, UniversalMcpClient> activeClients = new ConcurrentHashMap<>(); // Universal MCP clients for all transport types
    
    // Active processes for cleanup
    private final Map<String, McpServerConfig> serverConfigs = new ConcurrentHashMap<>();
    private final List<McpSyncClient> staticClients = new ArrayList<>();
    
    private ToolCallbackProvider toolCallbackProvider;
    
    @Autowired
    private UniversalMcpClientFactory clientFactory;
    
    public DynamicMcpServerService() {
        log.info("📊 Dynamic MCP Server Service initialized with in-memory configuration");
        
        // Initialize with default Python MCP server configuration
        initializeDefaultServers();
    }
    
    private void initializeDefaultServers() {
        try {
            // Add default Python FastMCP server configuration
            McpServerConfig defaultConfig = McpServerConfig.builder()
                .id("my-python-mcp-server-stdio")
                .name("Python Coding Assistant MCP Server")
                .description("FastMCP server with coding assistant tools")
                .transportType(McpServerConfig.McpTransportType.STDIO)
                .enabled(true)
                .configuration(Map.of(
                    "command", "python",
                    // Use unbuffered mode to ensure timely STDIO framing
                    "args", List.of("-u", "E:\\ai_projects\\MCP_apps\\coding_assistant_mcp\\coding_assistant_mcp.py"),
                    "workingDirectory", "E:\\ai_projects\\MCP_apps\\coding_assistant_mcp",
                    // Ensure unbuffered, UTF-8 I/O for predictable framing
                    "environment", Map.of(
                        "PYTHONPATH", "E:\\ai_projects\\MCP_apps\\coding_assistant_mcp",
                        "PYTHONUNBUFFERED", "1",
                        "PYTHONIOENCODING", "UTF-8"
                    )
                ))
                .build();
                
            serverConfigs.put(defaultConfig.getId(), defaultConfig);
            log.info("✅ Default Python MCP server configuration initialized: {}", defaultConfig.getId());
            
        } catch (Exception e) {
            log.error("Error initializing default servers: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Load servers from in-memory configuration
     */
    public void loadServersFromMemory() {
        try {
            log.info(" Loading MCP servers from in-memory configuration...");
            
            // Skip database loading for Universal MCP client - use in-memory configs only
            log.info("Using in-memory server configurations instead of database");
            return;
        } catch (Exception e) {
            log.error("Error loading servers from memory: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Add a new MCP server configuration
     */
    @Transactional
    public boolean addServer(McpServerConfig config) {
        try {
            log.info("Adding MCP server: {} (type: {})", config.getName(), config.getTransportType());
            
            // Auto-generate ID if missing
            if (config.getId() == null || config.getId().trim().isEmpty()) {
                String generatedId = generateServerId(config.getName(), config.getTransportType());
                config.setId(generatedId);
                log.info("Auto-generated server ID: {} for server: {}", generatedId, config.getName());
            }
            
            // Validate configuration
            if (!validateConfig(config)) {
                log.error("Invalid configuration for server: {}", config.getName());
                return false;
            }
            
            // Skip database operations for Universal MCP client - use in-memory only
            serverConfigs.put(config.getId(), config);
            log.info("📊 Server configuration stored in memory: {} (total configs: {})", config.getId(), serverConfigs.size());
            
            // Start server if enabled
            if (config.isEnabled()) {
                boolean started = startServer(config.getId());
                if (started) {
                    log.info(" MCP server started successfully: {}", config.getName());
                    updateToolCallbackProvider();
                    return true;
                } else {
                    log.error(" Failed to start MCP server: {}", config.getName());
                    return false;
                }
            }
            
            updateToolCallbackProvider();
            return true;
        } catch (Exception e) {
            log.error("Error adding MCP server {}: {}", config.getName(), e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Start an MCP server
     */
    public boolean startServer(String serverId) {
        try {
            McpServerConfig config = serverConfigs.get(serverId);
            if (config == null) {
                log.error("Server configuration not found: {}", serverId);
                return false;
            }
            
            if (activeClients.containsKey(serverId)) {
                log.warn("Server already running: {}", serverId);
                return true;
            }
            
            log.info("🚀 Starting MCP server: {} (type: {})", config.getName(), config.getTransportType());
            
            UniversalMcpClient client = createUniversalClient(config);
            if (client != null) {
                activeClients.put(serverId, client);
                log.info("✅ Client created and added to activeClients: {} (total active: {})", 
                        serverId, activeClients.size());
                
                // Debug: Log client type
                log.info("🔍 Universal MCP client type: {} ({})", client.getClass().getSimpleName(), client.getTransportType());
                log.info("🔍 Client details: name={}, transport={}, connected={}", 
                        client.getName(), client.getTransportType(), client.isConnected());
                
                updateToolCallbackProvider();
                log.info("✅ MCP server started successfully: {}", config.getName());
                return true;
            } else {
                log.error("❌ Failed to create client for server: {}", config.getName());
            }
            
            return false;
        } catch (Exception e) {
            log.error("Error starting MCP server {}: {}", serverId, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Stop an MCP server
     */
    public boolean stopServer(String serverId) {
        try {
            UniversalMcpClient client = activeClients.remove(serverId);
            if (client != null) {
                client.disconnect();
                updateToolCallbackProvider();
                log.info("✅ MCP server stopped successfully: {}", serverId);
                return true;
            }
            log.warn("⚠️ Server not found or already stopped: {}", serverId);
            return false;
        } catch (Exception e) {
            log.error("❌ Error stopping MCP server {}: {}", serverId, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Remove an MCP server configuration
     */
    public boolean removeServer(String serverId) {
        try {
            // Stop server if running
            stopServer(serverId);
            
            // Remove configuration from memory
            McpServerConfig removed = serverConfigs.remove(serverId);
            if (removed != null) {
                log.info(" MCP server configuration removed from memory: {}", removed.getName());
                return true;
            }
            
            return false;
        } catch (Exception e) {
            log.error("Error removing MCP server {}: {}", serverId, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Get all server configurations
     */
    public List<McpServerConfig> getAllServers() {
        log.info("Getting all servers - serverConfigs size: {}, activeClients size: {}", 
                serverConfigs.size(), activeClients.size());
        log.info("Server IDs in configs: {}", serverConfigs.keySet());
        
        // Filter out static servers - only return dynamic servers
        List<McpServerConfig> dynamicServers = serverConfigs.entrySet().stream()
                .filter(entry -> !entry.getKey().startsWith("static-"))
                .map(Map.Entry::getValue)
                .collect(java.util.stream.Collectors.toList());
        
        log.info("Returning {} dynamic servers (filtered out static servers)", dynamicServers.size());
        return dynamicServers;
    }
    
    /**
     * Get active servers (servers that are currently running)
     */
    public List<String> getActiveServers() {
        return new ArrayList<>(activeClients.keySet());
    }
    
    /**
     * Get active clients map
     */
    public Map<String, Object> getActiveClients() {
        // Cast UniversalMcpClient to Object for compatibility with controller
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, UniversalMcpClient> entry : activeClients.entrySet()) {
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }
    
    /**
     * Get active server status
     */


    public Map<String, Boolean> getServerStatus() {
        Map<String, Boolean> status = new HashMap<>();
        // Only include dynamic servers (filter out static servers)
        for (String serverId : serverConfigs.keySet()) {
            if (!serverId.startsWith("static-")) {
                status.put(serverId, activeClients.containsKey(serverId));
            }
        }
        return status;
    }
    
    /**
     * Get the current tool callback provider
     */
    public ToolCallbackProvider getToolCallbackProvider() {
        if (toolCallbackProvider == null) {
            log.warn("🚨 Tool callback provider is NULL - this should not happen!");
            return null;
        }
        
        ToolCallback[] callbacks = toolCallbackProvider.getToolCallbacks();
        log.debug("🔍 Current provider has {} tool callbacks", callbacks.length);
        
        // Debug: Count static vs dynamic tools
        int staticCount = 0;
        int dynamicCount = 0;
        for (ToolCallback callback : callbacks) {
            if (callback instanceof com.vijay.mcp.DynamicToolCallback) {
                dynamicCount++;
            } else {
                staticCount++;
            }
        }
        log.debug("🔍 Tool breakdown: {} static, {} dynamic", staticCount, dynamicCount);
        
        return toolCallbackProvider;
    }
    
    /**
     * Set static MCP clients (from properties file)
     */
    public void setStaticClients(List<McpSyncClient> clients) {
        this.staticClients.clear();
        if (clients != null) {
            this.staticClients.addAll(clients);
            log.info(" Set {} static MCP clients", clients.size());
            
            // Convert static clients to server configs for display
            for (int i = 0; i < clients.size(); i++) {
                McpSyncClient client = clients.get(i);
                McpServerConfig config = new McpServerConfig();
                config.setId("static-server-" + (i + 1));
                config.setName("Static MCP Server " + (i + 1));
                config.setDescription("Static MCP server from application.properties");
                config.setTransportType(McpServerConfig.McpTransportType.STDIO); // Default to STDIO for static servers
                config.setEnabled(true); // Static servers are always enabled
                
                // Store in serverConfigs for display purposes
                serverConfigs.put(config.getId(), config);
            }
            
            log.info(" Converted {} static clients to server configs", clients.size());
        }
        updateToolCallbackProvider();
    }
    
    /**
     * Create Universal MCP client based on configuration
     */
    private UniversalMcpClient createUniversalClient(McpServerConfig config) {
        try {
            log.info("🏭 Creating Universal MCP client for: {} ({})", config.getName(), config.getTransportType());
            
            Map<String, Object> clientConfig = new HashMap<>();
            Map<String, Object> configMap = config.getConfiguration();
            
            switch (config.getTransportType()) {
                case STDIO:
                    clientConfig.put("command", configMap.get("command"));
                    clientConfig.put("args", configMap.get("args"));
                    clientConfig.put("workingDirectory", configMap.get("workingDirectory"));
                    clientConfig.put("environment", configMap.get("environment"));
                    break;
                case SSE:
                    clientConfig.put("baseUrl", configMap.get("baseUrl"));
                    clientConfig.put("headers", configMap.get("headers"));
                    break;
                case SOCKET:
                    clientConfig.put("wsUrl", configMap.get("wsUrl"));
                    clientConfig.put("headers", configMap.get("headers"));
                    break;
                default:
                    log.error("Unsupported transport type: {}", config.getTransportType());
                    return null;
            }
            
            UniversalMcpClient client = clientFactory.createClient(
                config.getName(), 
                config.getTransportType().name(), 
                clientConfig
            );
            
            // Connect and initialize the client
            client.connect();
            client.initialize();
            
            return client;
            
        } catch (Exception e) {
            log.error("Error creating Universal MCP client for {}: {}", config.getName(), e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Find server ID by name
     */
    private String findServerIdByName(String name) {
        for (Map.Entry<String, McpServerConfig> entry : serverConfigs.entrySet()) {
            if (name.equals(entry.getValue().getName())) {
                return entry.getKey();
            }
        }
        return null;
    }
    
    /**
     * Generate a unique server ID based on name and transport type
     */
    private String generateServerId(String name, McpServerConfig.McpTransportType transportType) {
        String baseId = name.toLowerCase()
                .replaceAll("[^a-z0-9]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        
        String transportSuffix = transportType.name().toLowerCase();
        String generatedId = baseId + "-" + transportSuffix;

        // Ensure uniqueness by adding counter if needed
        String finalId = generatedId;
        int counter = 1;
        while (serverConfigs.containsKey(finalId)) {
            finalId = generatedId + "-" + counter;
            counter++;
        }
        
        return finalId;
    }
    
    /**
     * Validate server configuration
     */
    private boolean validateConfig(McpServerConfig config) {
        // ID will be auto-generated if missing, so we don't validate it here
        // if (config.getId() == null || config.getId().trim().isEmpty()) {
        //     log.error("Server ID is required");
        //     return false;
        // }
        
        if (config.getName() == null || config.getName().trim().isEmpty()) {
            log.error("Server name is required");
            return false;
        }
        
        if (config.getTransportType() == null) {
            log.error("Transport type is required");
            return false;
        }
        
        // Additional validation based on transport type
        switch (config.getTransportType()) {
            case STDIO:
                return validateStdioConfig(config);
            case SSE:
                return validateSseConfig(config);
            case SOCKET:
                return validateSocketConfig(config);
            default:
                return false;
        }
    }
    
    private boolean validateStdioConfig(McpServerConfig config) {
        Map<String, Object> configMap = config.getConfiguration();
        if (configMap == null) {
            log.error("STDIO configuration is required");
            return false;
        }
        
        String command = (String) configMap.get("command");
        if (command == null || command.trim().isEmpty()) {
            log.error("STDIO command is required");
            return false;
        }
        
        return true;
    }
    
    private boolean validateSseConfig(McpServerConfig config) {
        Map<String, Object> configMap = config.getConfiguration();
        if (configMap == null) {
            log.error("SSE configuration is required");
            return false;
        }
        
        String url = (String) configMap.get("baseUrl");
        if (url == null || url.trim().isEmpty()) {
            log.error("SSE URL is required");
            return false;
        }
        
        // Basic URL validation
        try {
            new java.net.URL(url);
        } catch (Exception e) {
            log.error("Invalid SSE URL: {}", url);
            return false;
        }
        
        return true;
    }
    
    private boolean validateSocketConfig(McpServerConfig config) {
        Map<String, Object> configMap = config.getConfiguration();
        if (configMap == null) {
            log.error("Socket configuration is required");
            return false;
        }
        
        String host = (String) configMap.get("wsUrl");
        if (host == null || host.trim().isEmpty()) {
            log.error("Socket host is required");
            return false;
        }
        
        Object portObj = configMap.get("port");
        if (portObj == null) {
            log.error("Socket port is required");
            return false;
        }
        
        int port;
        try {
            port = Integer.parseInt(portObj.toString());
            if (port <= 0 || port > 65535) {
                log.error("Socket port must be between 1 and 65535");
                return false;
            }
        } catch (NumberFormatException e) {
            log.error("Socket port must be a valid number");
            return false;
        }
        
        return true;
    }
    
    /**
     * Update the tool callback provider with all active clients
     */
    private void updateToolCallbackProvider() {
        try {
            List<McpSyncClient> allClients = new ArrayList<>(staticClients);
            
            // Log Universal MCP clients for dynamic tool discovery
            for (UniversalMcpClient client : activeClients.values()) {
                log.info("Universal MCP client available: {} ({}, connected: {})", 
                        client.getName(), client.getTransportType(), client.isConnected());
            }
            
            // Create a custom tool callback provider that includes dynamic tools
            ToolCallbackProvider enhancedProvider = createEnhancedToolCallbackProvider(allClients);
            
            if (enhancedProvider != null) {
                this.toolCallbackProvider = enhancedProvider;
                log.info("✅ Enhanced tool callback provider created and stored successfully");
                
                // Verify the provider was stored correctly
                var toolCallbacks = this.toolCallbackProvider.getToolCallbacks();
                log.info("✅ Enhanced provider has {} total tools available", toolCallbacks.length);
                
                // Log tool details for debugging
                int staticToolCount = 0;
                int dynamicToolCount = 0;
                for (var callback : toolCallbacks) {
                    String toolName = callback.getToolDefinition().name();
                    if (callback instanceof com.vijay.mcp.DynamicToolCallback) {
                        dynamicToolCount++;
                        log.info("🔧 Dynamic tool available: {}", toolName);
                    } else {
                        staticToolCount++;
                        log.info("📋 Static tool available: {}", toolName);
                    }
                }
                
                log.info("📊 Tool breakdown: {} static tools, {} dynamic tools", staticToolCount, dynamicToolCount);
            } else {
                log.error("❌ Failed to create enhanced tool callback provider, keeping existing provider");
            }
            
            // Log dynamic clients for debugging
            for (Map.Entry<String, UniversalMcpClient> entry : activeClients.entrySet()) {
                String serverId = entry.getKey();
                UniversalMcpClient client = entry.getValue();
                log.info("Dynamic client {}: {} (type: {})", serverId, client, client.getClass().getSimpleName());
            }
            
            log.info("Updated tool callback provider with {} clients ({} static, {} dynamic)", 
                    allClients.size(), staticClients.size(), activeClients.size());
            log.info("Active dynamic clients: {}", activeClients.keySet());
        } catch (Exception e) {
            log.error("Error updating tool callback provider: {}", e.getMessage(), e);
            // Create a fallback provider with just static clients
            try {
                this.toolCallbackProvider = new SyncMcpToolCallbackProvider(staticClients);
                log.warn("Created fallback tool callback provider with {} static clients", staticClients.size());
            } catch (Exception fallbackError) {
                log.error("Failed to create fallback tool callback provider: {}", fallbackError.getMessage());
            }
        }
    }
    
    /**
     * Create an enhanced tool callback provider that includes dynamic tools
     */
    private ToolCallbackProvider createEnhancedToolCallbackProvider(List<McpSyncClient> staticClients) {
        try {
            log.info("🔧 Creating enhanced tool callback provider...");
            
            // Create the base tool callback provider with static clients
            SyncMcpToolCallbackProvider baseProvider = new SyncMcpToolCallbackProvider(staticClients != null ? staticClients : List.of());
            
            // Build a combined list of ToolCallbacks: static (via base) + dynamic (via UniversalMcpClient)
            List<ToolCallback> combined = new ArrayList<>();
            ToolCallback[] staticCallbacks = baseProvider.getToolCallbacks();
            if (staticCallbacks != null) {
                combined.addAll(Arrays.asList(staticCallbacks));
                log.info("📋 Added {} static tool callbacks", staticCallbacks.length);
            }

            ObjectMapper mapper = new ObjectMapper();
            int realDynamicToolsCount = 0;
            int processedClients = 0;
            
            log.info("🔍 Processing {} active clients for dynamic tools...", activeClients.size());
            
            for (Map.Entry<String, UniversalMcpClient> entry : activeClients.entrySet()) {
                String clientId = entry.getKey();
                UniversalMcpClient client = entry.getValue();
                
                log.info("🔍 Processing Universal MCP client: {} ({})", clientId, client.getClass().getSimpleName());
                
                processedClients++;
                
                log.info("🔧 Processing Universal MCP client: {} ({}) - Transport: {}", 
                        client.getName(), clientId, client.getTransportType());
                log.info("🔍 Client connected: {}, initialized: {}", client.isConnected(), client.isInitialized());
                
                try {
                    log.info("🔧 Getting tools from Universal MCP client: {} ({})", client.getName(), clientId);
                    
                    // Check if client is connected before attempting tool discovery
                    if (!client.isConnected()) {
                        log.error("❌ Client is not connected for {}, skipping tool discovery", client.getName());
                        continue;
                    }
                    
                    List<Object> tools = client.listTools();
                    
                    if (tools == null) {
                        log.error("❌ Universal MCP client {} returned NULL tools list", client.getName());
                        continue;
                    }
                    
                    if (tools.isEmpty()) {
                        log.warn("⚠️ Universal MCP client {} returned empty tools list (0 tools)", client.getName());
                        log.warn("   This could be due to initialization timeout or MCP protocol issues");
                        continue;
                    }
                    
                    log.info("✅ Universal MCP client {} returned {} tools", client.getName(), tools.size());
                    
                    for (Object t : tools) {
                        if (t instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> tm = (Map<String, Object>) t;
                            String toolName = String.valueOf(tm.getOrDefault("name", "dynamic_tool_" + realDynamicToolsCount));
                            String description = (String) tm.getOrDefault("description", "Dynamic tool from " + client.getName());
                            Object inputSchema = tm.get("inputSchema");
                            String inputSchemaJson = null;
                            
                            try {
                                if (inputSchema != null) {
                                    inputSchemaJson = mapper.writeValueAsString(inputSchema);
                                }
                            } catch (Exception schemaError) {
                                log.warn("Failed to serialize input schema for tool {}: {}", toolName, schemaError.getMessage());
                            }

                            DynamicToolCallback dynamicCallback = new DynamicToolCallback(toolName, description, inputSchemaJson, client, toolName);
                            combined.add(dynamicCallback);
                            realDynamicToolsCount++;
                            
                            log.info("✅ Added dynamic tool: {} from Universal MCP client {}", toolName, client.getName());
                        } else {
                            log.warn("⚠️ Unexpected tool format from Universal MCP client {}: {}", client.getName(), t.getClass().getSimpleName());
                        }
                    }
                    
                    log.info("✅ Successfully processed {} tools from Universal MCP client {}", tools.size(), client.getName());
                    
                } catch (Exception e) {
                    log.error("❌ Error getting tools from Universal MCP client {}: {}", client.getName(), e.getMessage(), e);
                }
            }

            log.info("📊 Enhanced provider creation summary:");
            log.info("   - Static callbacks: {}", staticCallbacks != null ? staticCallbacks.length : 0);
            log.info("   - Dynamic callbacks: {} (from {} real clients)", realDynamicToolsCount, processedClients);
            log.info("   - Total callbacks: {}", combined.size());

            if (combined.isEmpty()) {
                log.warn("⚠️ No tool callbacks available, returning empty provider");
                return new ToolCallbackProvider() {
                    @Override
                    @NonNull
                    public ToolCallback[] getToolCallbacks() {
                        return new ToolCallback[0];
                    }
                };
            }

            final ToolCallback[] callbacksArray = combined.toArray(new ToolCallback[0]);
            log.info("✅ Enhanced tool callback provider created with {} total tools", callbacksArray.length);

            // Return a simple provider that serves the combined tool callbacks
            return new ToolCallbackProvider() {
                @Override
                @NonNull
                public ToolCallback[] getToolCallbacks() {
                    log.debug("Enhanced provider returning {} tool callbacks", callbacksArray.length);
                    return callbacksArray;
                }
            };
            
        } catch (Exception e) {
            log.error("❌ Critical error creating enhanced tool callback provider: {}", e.getMessage(), e);
            // Return fallback provider with just static clients
            try {
                SyncMcpToolCallbackProvider fallback = new SyncMcpToolCallbackProvider(staticClients != null ? staticClients : List.of());
                log.warn("🔄 Created fallback provider with {} static clients", staticClients != null ? staticClients.size() : 0);
                return fallback;
            } catch (Exception fallbackError) {
                log.error("❌ Failed to create fallback provider: {}", fallbackError.getMessage());
                return new ToolCallbackProvider() {
                    @Override
                    @NonNull
                    public ToolCallback[] getToolCallbacks() {
                        return new ToolCallback[0];
                    }
                };
            }
        }
    }
    
}
