package com.vijay.service;

import com.vijay.dto.McpServerConfig;
import com.vijay.entity.McpServerEntity;
import com.vijay.mcp.DynamicToolCallback;
import com.vijay.mcp.UniversalMcpClient;
import com.vijay.mcp.UniversalMcpClientFactory;
import com.vijay.repository.McpServerRepository;

import io.modelcontextprotocol.client.McpSyncClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class DynamicMcpServerService {
    
    private final Map<String, UniversalMcpClient> activeClients = new ConcurrentHashMap<>(); // Universal MCP clients for all transport types
    
    // Active processes for cleanup
    private final Map<String, McpServerConfig> serverConfigs = new ConcurrentHashMap<>();
    private final List<McpSyncClient> staticClients = new ArrayList<>();
    
    private ToolCallbackProvider toolCallbackProvider;
    
    private final UniversalMcpClientFactory clientFactory;
    private final McpServerRepository mcpServerRepository;
    
    public DynamicMcpServerService(UniversalMcpClientFactory clientFactory, McpServerRepository mcpServerRepository) {
        this.clientFactory = clientFactory;
        this.mcpServerRepository = mcpServerRepository;
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
            
            // Save to database
            McpServerEntity entity = convertConfigToEntity(config);
            McpServerEntity savedEntity = mcpServerRepository.save(entity);
            log.info("💾 Server configuration saved to database: {} (ID: {})", config.getName(), savedEntity.getId());
            
            // Also store in memory for quick access
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
                // Clear any existing cache when starting fresh
                client.clearToolCache();
                
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
                // Check if this is a static server (handled by Spring AI)
                if (config.getId().startsWith("static-")) {
                    log.info("🔧 Static server {} is handled by Spring AI, not UniversalMcpClient", config.getName());
                    updateToolCallbackProvider(); // Update provider to include static tools
                    return true; // Consider it "started" since Spring AI handles it
                } else {
                    log.error("❌ Failed to create client for dynamic server: {}", config.getName());
                }
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
                // Clear cache when stopping server
                client.clearToolCache();
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
            
            // Remove from database
            if (mcpServerRepository.existsById(serverId)) {
                mcpServerRepository.deleteById(serverId);
                log.info("💾 MCP server configuration deleted from database: {}", serverId);
            }
            
            // Remove configuration from memory
            McpServerConfig removed = serverConfigs.remove(serverId);
            if (removed != null) {
                log.info("📊 MCP server configuration removed from memory: {}", removed.getName());
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
        log.info("Getting all servers from database and memory");
        
        try {
            // Load from database
            List<McpServerEntity> entities = mcpServerRepository.findAll();
            log.info("📊 Found {} servers in database", entities.size());
            
            // Convert entities to configs
            List<McpServerConfig> configs = new ArrayList<>();
            for (McpServerEntity entity : entities) {
                McpServerConfig config = convertEntityToConfig(entity);
                configs.add(config);
                
                // Also update memory cache
                serverConfigs.put(config.getId(), config);
            }
            
            // Add static servers from memory (if any)
            List<McpServerConfig> staticServers = serverConfigs.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith("static-"))
                    .map(Map.Entry::getValue)
                    .collect(java.util.stream.Collectors.toList());
            
            configs.addAll(staticServers);
            
            log.info("📊 Returning {} total servers ({} from database, {} static)", 
                    configs.size(), entities.size(), staticServers.size());
            return configs;
            
        } catch (Exception e) {
            log.error("Error loading servers from database: {}", e.getMessage(), e);
            
            // Fallback to memory only
            List<McpServerConfig> dynamicServers = serverConfigs.entrySet().stream()
                    .filter(entry -> !entry.getKey().startsWith("static-"))
                    .map(Map.Entry::getValue)
                    .collect(java.util.stream.Collectors.toList());
            
            log.warn("Using fallback - returning {} servers from memory only", dynamicServers.size());
            return dynamicServers;
        }
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
     * Force refresh the tool callback provider
     */
    public void forceRefreshToolCallbackProvider() {
        log.info("🔄 Force refreshing tool callback provider...");
        log.info("🔍 Active clients: {}", activeClients.keySet());
        log.info("🔍 Static clients: {}", staticClients.size());
        
        long startTime = System.currentTimeMillis();
        updateToolCallbackProvider();
        long endTime = System.currentTimeMillis();
        
        // Verify the refresh
        if (toolCallbackProvider != null) {
            ToolCallback[] callbacks = toolCallbackProvider.getToolCallbacks();
            int staticCount = 0;
            int dynamicCount = 0;
            for (ToolCallback callback : callbacks) {
                if (callback instanceof com.vijay.mcp.DynamicToolCallback) {
                    dynamicCount++;
                } else {
                    staticCount++;
                }
            }
            log.info("✅ Tool callback provider refreshed - Static: {}, Dynamic: {}, Total: {} (took {}ms)", 
                    staticCount, dynamicCount, callbacks.length, (endTime - startTime));
        }
    }
    
    /**
     * Get detailed server statistics
     */
    public Map<String, Object> getServerStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        // Server counts
        stats.put("totalServers", serverConfigs.size());
        stats.put("activeServers", activeClients.size());
        stats.put("staticServers", staticClients.size());
        
        // Tool counts
        if (toolCallbackProvider != null) {
            ToolCallback[] callbacks = toolCallbackProvider.getToolCallbacks();
            int staticCount = 0;
            int dynamicCount = 0;
            
            for (ToolCallback callback : callbacks) {
                if (callback instanceof com.vijay.mcp.DynamicToolCallback) {
                    dynamicCount++;
                } else {
                    staticCount++;
                }
            }
            
            stats.put("totalTools", callbacks.length);
            stats.put("staticTools", staticCount);
            stats.put("dynamicTools", dynamicCount);
        }
        
        // Server details
        List<Map<String, Object>> serverDetails = new ArrayList<>();
        for (Map.Entry<String, UniversalMcpClient> entry : activeClients.entrySet()) {
            Map<String, Object> serverInfo = new HashMap<>();
            UniversalMcpClient client = entry.getValue();
            
            serverInfo.put("id", entry.getKey());
            serverInfo.put("name", client.getName());
            serverInfo.put("transportType", client.getTransportType());
            serverInfo.put("connected", client.isConnected());
            serverInfo.put("initialized", client.isInitialized());
            
            // Get tool count for this server
            try {
                List<Object> tools = client.getToolsWithCache();
                serverInfo.put("toolCount", tools != null ? tools.size() : 0);
            } catch (Exception e) {
                serverInfo.put("toolCount", 0);
                serverInfo.put("error", e.getMessage());
            }
            
            serverDetails.add(serverInfo);
        }
        
        stats.put("serverDetails", serverDetails);
        stats.put("timestamp", System.currentTimeMillis());
        
        return stats;
    }
    
    /**
     * Set static MCP clients (from properties file)
     */
    public void setStaticClients(List<McpSyncClient> clients) {
        this.staticClients.clear();
        if (clients != null) {
            this.staticClients.addAll(clients);
            log.info("📋 Set {} static MCP clients (handled by Spring AI)", clients.size());
            
            // Don't create server configs for static servers - they're handled by Spring AI
            // Static servers don't need UniversalMcpClient instances
            log.info("🔧 Static servers are handled by Spring AI, not UniversalMcpClient");
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
            
            // Handle null configuration for static servers
            if (configMap == null) {
                log.warn("⚠️ Configuration is null for server: {} - this might be a static server", config.getName());
                if (config.getId().startsWith("static-")) {
                    log.info("🔧 Skipping static server creation (handled by Spring AI): {}", config.getName());
                    return null; // Static servers are handled by Spring AI, not UniversalMcpClient
                } else {
                    log.error("❌ Configuration is null for dynamic server: {}", config.getName());
                    return null;
                }
            }
            
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
            Set<String> toolNames = new HashSet<>(); // Track tool names to detect duplicates
            
            log.info("🔍 Processing {} active clients for dynamic tools...", activeClients.size());
            log.info("🔍 Active client IDs: {}", activeClients.keySet());
            
            for (Map.Entry<String, UniversalMcpClient> entry : activeClients.entrySet()) {
                String clientId = entry.getKey();
                UniversalMcpClient client = entry.getValue();
                
                log.info("🔍 Processing Universal MCP client: {} ({})", clientId, client.getClass().getSimpleName());
                log.info("🔍 Client details - Name: {}, Transport: {}, Connected: {}", 
                        client.getName(), client.getTransportType(), client.isConnected());
                
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

                            // Allow duplicate tool names from different servers
                            // Prefix tool name with server name to make them unique
                            String uniqueToolName = client.getName() + "_" + toolName;
                            
                            if (toolNames.contains(uniqueToolName)) {
                                log.warn("⚠️ DUPLICATE TOOL NAME DETECTED: {} from client {} - SKIPPING", uniqueToolName, client.getName());
                                continue;
                            }
                            
                            toolNames.add(uniqueToolName);
                            DynamicToolCallback dynamicCallback = new DynamicToolCallback(uniqueToolName, description, inputSchemaJson, client, toolName);
                            combined.add(dynamicCallback);
                            realDynamicToolsCount++;
                            
                            log.info("✅ Added dynamic tool: {} from Universal MCP client {} (total: {})", toolName, client.getName(), realDynamicToolsCount);
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
            log.info("   - Unique tool names: {}", toolNames.size());
            log.info("   - Expected dynamic tools: {} ({} servers × 72 tools each)", activeClients.size() * 72, activeClients.size());
            if (realDynamicToolsCount != activeClients.size() * 72) {
                log.warn("⚠️ MISMATCH: Expected {} dynamic tools, got {} - checking for duplicates or failures", 
                        activeClients.size() * 72, realDynamicToolsCount);
            }

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
    
    /**
     * Convert McpServerConfig to McpServerEntity for database storage
     */
    private McpServerEntity convertConfigToEntity(McpServerConfig config) {
        McpServerEntity entity = new McpServerEntity();
        
        // Basic fields
        entity.setId(config.getId());
        entity.setName(config.getName());
        entity.setDescription(config.getDescription());
        entity.setEnabled(config.isEnabled());
        
        // Transport type conversion
        switch (config.getTransportType()) {
            case STDIO:
                entity.setTransportType(McpServerEntity.TransportType.STDIO);
                break;
            case SSE:
                entity.setTransportType(McpServerEntity.TransportType.SSE);
                break;
            case SOCKET:
                entity.setTransportType(McpServerEntity.TransportType.SOCKET);
                break;
            default:
                entity.setTransportType(McpServerEntity.TransportType.STDIO);
        }
        
        // Status
        entity.setStatus(McpServerEntity.ServerStatus.STOPPED);
        
        // Configuration mapping
        Map<String, Object> configMap = config.getConfiguration();
        if (configMap != null) {
            // STDIO configuration
            if (config.getTransportType() == McpServerConfig.McpTransportType.STDIO) {
                entity.setCommand((String) configMap.get("command"));
                entity.setArgs(convertListToString((List<?>) configMap.get("args")));
                entity.setWorkingDirectory((String) configMap.get("workingDirectory"));
                // Store environment as headers field (JSON string)
                entity.setHeaders(convertMapToString((Map<?, ?>) configMap.get("environment")));
            }
            // SSE configuration
            else if (config.getTransportType() == McpServerConfig.McpTransportType.SSE) {
                entity.setUrl((String) configMap.get("baseUrl"));
            }
            // Socket configuration
            else if (config.getTransportType() == McpServerConfig.McpTransportType.SOCKET) {
                entity.setHost((String) configMap.get("host"));
                Object portObj = configMap.get("port");
                if (portObj != null) {
                    entity.setPort(Integer.parseInt(portObj.toString()));
                }
                // Store wsUrl in protocol field
                entity.setProtocol((String) configMap.get("wsUrl"));
            }
        }
        
        // Set timestamps
        entity.setCreatedAt(java.time.LocalDateTime.now());
        entity.setUpdatedAt(java.time.LocalDateTime.now());
        
        return entity;
    }
    
    /**
     * Convert List to comma-separated string
     */
    private String convertListToString(List<?> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        return String.join(",", list.stream().map(Object::toString).toArray(String[]::new));
    }
    
    /**
     * Convert Map to JSON string
     */
    private String convertMapToString(Map<?, ?> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.writeValueAsString(map);
        } catch (Exception e) {
            log.warn("Failed to convert map to string: {}", e.getMessage());
            return map.toString();
        }
    }
    
    /**
     * Convert McpServerEntity to McpServerConfig for API responses
     */
    private McpServerConfig convertEntityToConfig(McpServerEntity entity) {
        McpServerConfig config = new McpServerConfig();
        
        // Basic fields
        config.setId(entity.getId());
        config.setName(entity.getName());
        config.setDescription(entity.getDescription());
        config.setEnabled(entity.getEnabled());
        
        // Transport type conversion
        switch (entity.getTransportType()) {
            case STDIO:
                config.setTransportType(McpServerConfig.McpTransportType.STDIO);
                break;
            case SSE:
                config.setTransportType(McpServerConfig.McpTransportType.SSE);
                break;
            case SOCKET:
                config.setTransportType(McpServerConfig.McpTransportType.SOCKET);
                break;
            default:
                config.setTransportType(McpServerConfig.McpTransportType.STDIO);
        }
        
        // Configuration mapping
        Map<String, Object> configMap = new HashMap<>();
        
        if (entity.getTransportType() == McpServerEntity.TransportType.STDIO) {
            configMap.put("command", entity.getCommand());
            if (entity.getArgs() != null) {
                configMap.put("args", Arrays.asList(entity.getArgs().split(",")));
            }
            configMap.put("workingDirectory", entity.getWorkingDirectory());
            if (entity.getHeaders() != null) {
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> envMap = mapper.readValue(entity.getHeaders(), Map.class);
                    configMap.put("environment", envMap);
                } catch (Exception e) {
                    log.warn("Failed to parse environment JSON: {}", e.getMessage());
                }
            }
        } else if (entity.getTransportType() == McpServerEntity.TransportType.SSE) {
            configMap.put("baseUrl", entity.getUrl());
        } else if (entity.getTransportType() == McpServerEntity.TransportType.SOCKET) {
            configMap.put("host", entity.getHost());
            configMap.put("port", entity.getPort());
            configMap.put("wsUrl", entity.getProtocol());
        }
        
        config.setConfiguration(configMap);
        
        return config;
    }
    
}
