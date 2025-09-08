package com.vijay.service;

import com.vijay.dto.McpServerConfig;
import com.vijay.entity.McpServerEntity;
import com.vijay.repository.McpServerRepository;
import io.modelcontextprotocol.client.McpSyncClient;
import com.vijay.mcp.DynamicToolCallback;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
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
    
    private final Map<String, Object> activeClients = new ConcurrentHashMap<>(); // Using Object to handle both real and mock clients
    
    // Active processes for cleanup
    private final Map<String, Process> activeProcesses = new ConcurrentHashMap<>();
    private final Map<String, McpServerConfig> serverConfigs = new ConcurrentHashMap<>();
    private final List<McpSyncClient> staticClients = new ArrayList<>();
    
    private ToolCallbackProvider toolCallbackProvider;
    
    @Autowired
    private DynamicMcpConfigurationService dynamicConfigService;
    
    @Autowired
    private McpServerRepository serverRepository;
    
    public DynamicMcpServerService() {
        log.info(" Dynamic MCP Server Service initialized with MySQL database persistence");
    }
    
    /**
     * Load servers from MySQL database on startup
     * This method should be called after Spring context is fully initialized
     */
    @Transactional
    public void loadServersFromDatabase() {
        try {
            log.info(" Loading MCP servers from MySQL database...");
            
            List<McpServerEntity> entities = serverRepository.findAll();
            log.info("Found {} servers in database", entities.size());
            
            for (McpServerEntity entity : entities) {
                try {
                    // Skip static servers (they should only come from application.properties)
                    if (entity.getId().startsWith("static-")) {
                        log.debug("Skipping static server from database: {} (should be loaded from application.properties)", entity.getId());
                        continue;
                    }
                    
                    McpServerConfig config = entity.toDto();
                    serverConfigs.put(config.getId(), config);
                    
                    // Add to Spring environment
                    boolean configAdded = dynamicConfigService.addDynamicMcpServer(
                        config.getId(), 
                        config.getTransportType().name(), 
                        config.getConfiguration()
                    );
                    
                    if (configAdded) {
                        log.info(" Loaded server from database: {} (type: {})", config.getName(), config.getTransportType());
                        
                        // Auto-start if enabled and was running before
                        if (config.isEnabled() && entity.getStatus() == McpServerEntity.ServerStatus.RUNNING) {
                            boolean started = startServer(config.getId());
                            if (started) {
                                log.info(" Auto-started server: {}", config.getName());
                            } else {
                                log.warn(" Failed to auto-start server: {}", config.getName());
                            }
                        }
                    } else {
                        log.warn(" Failed to load server configuration: {}", config.getName());
                    }
                    
                } catch (Exception e) {
                    log.error("Error loading server {}: {}", entity.getName(), e.getMessage(), e);
                }
            }
            
            log.info(" Loaded {} servers from database", serverConfigs.size());
            updateToolCallbackProvider();
            
        } catch (Exception e) {
            log.error("Error loading servers from database: {}", e.getMessage(), e);
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
            
            // Check if server already exists in database
            if (serverRepository.existsById(config.getId())) {
                log.warn("Server with ID {} already exists in database", config.getId());
                return false;
            }
            
            // Convert to entity and save to database
            McpServerEntity entity = McpServerEntity.fromDto(config);
            serverRepository.save(entity);
            log.info(" Server configuration saved to MySQL database: {} (ID: {})", config.getName(), config.getId());
            
            // Store configuration in memory
            serverConfigs.put(config.getId(), config);
            log.info(" Server configuration stored in memory: {} (total configs: {})", config.getId(), serverConfigs.size());
            
            // Add to dynamic configuration service for Spring AI integration
            boolean configAdded = dynamicConfigService.addDynamicMcpServer(
                config.getId(), 
                config.getTransportType().name(), 
                config.getConfiguration()
            );
            
            if (configAdded) {
                log.info(" Dynamic MCP server configuration added to Spring AI: {}", config.getId());
            } else {
                log.warn(" Failed to add dynamic MCP server configuration to Spring AI: {}", config.getId());
            }
            
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
            
            log.info("Starting MCP server: {} (type: {})", config.getName(), config.getTransportType());
            
            Object client = createClient(config);
            if (client != null) {
                activeClients.put(serverId, client);
                updateToolCallbackProvider();
                log.info(" MCP server started successfully: {}", config.getName());
                return true;
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
            Object client = activeClients.remove(serverId);
            if (client != null) {
                // Close client connection
                if (client instanceof McpSyncClient) {
                    ((McpSyncClient) client).close();
                } else if (client instanceof RealStdioMcpClient) {
                    ((RealStdioMcpClient) client).disconnect();
                }
                updateToolCallbackProvider();
                log.info(" MCP server stopped: {}", serverId);
                return true;
            }
            
            log.warn("Server not found or already stopped: {}", serverId);
            return false;
        } catch (Exception e) {
            log.error("Error stopping MCP server {}: {}", serverId, e.getMessage(), e);
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
            
            // Remove from dynamic configuration service
            boolean configRemoved = dynamicConfigService.removeDynamicMcpServer(serverId);
            if (configRemoved) {
                log.info(" Dynamic MCP server configuration removed from Spring AI: {}", serverId);
            } else {
                log.warn(" Failed to remove dynamic MCP server configuration from Spring AI: {}", serverId);
            }
            
            // Remove configuration from database
            if (serverRepository.existsById(serverId)) {
                serverRepository.deleteById(serverId);
                log.info(" MCP server configuration removed from database: {}", serverId);
            }
            
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
        return new HashMap<>(activeClients);
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
     * Create MCP client based on configuration
     */
    private Object createClient(McpServerConfig config) {
        try {
            switch (config.getTransportType()) {
                case STDIO:
                    return createStdioClient(config);
                case SSE:
                    return createSseClient(config);
                case SOCKET:
                    return createSocketClient(config);
                default:
                    log.error("Unsupported transport type: {}", config.getTransportType());
                    return null;
            }
        } catch (Exception e) {
            log.error("Error creating MCP client for {}: {}", config.getName(), e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Create STDIO client
     */
    private Object createStdioClient(McpServerConfig config) {
        try {
            log.info("Creating STDIO client for: {}", config.getName());
            
            // Extract STDIO configuration
            Map<String, Object> configMap = config.getConfiguration();
            String command = (String) configMap.get("command");
            @SuppressWarnings("unchecked")
            List<String> args = (List<String>) configMap.get("args");
            String workingDirectory = (String) configMap.get("workingDirectory");
            @SuppressWarnings("unchecked")
            Map<String, String> environment = (Map<String, String>) configMap.get("environment");
            
            if (command == null || command.trim().isEmpty()) {
                log.error("STDIO command is required for server: {}", config.getName());
                return null;
            }
            
            // Build command list
            List<String> commandList = new ArrayList<>();
            commandList.add(command);
            if (args != null) {
                commandList.addAll(args);
            }
            
            log.info("STDIO command: {}", commandList);
            
            // Create real STDIO client using Spring AI MCP classes
            try {
                // Create ProcessBuilder for the MCP server
                ProcessBuilder processBuilder = new ProcessBuilder(commandList);
                
                // Set working directory if specified
                if (workingDirectory != null && !workingDirectory.trim().isEmpty()) {
                    processBuilder.directory(new java.io.File(workingDirectory));
                }
                
                // Set environment variables if specified
                if (environment != null) {
                    processBuilder.environment().putAll(environment);
                }
                
                // Create a real MCP client that can be injected into AI models
                log.info("Creating real STDIO MCP client for: {}", config.getName());
                
                try {
                    // Create ProcessBuilder for the MCP server (reusing the existing one)
                    processBuilder.command(commandList);
                    
                    // Set working directory if specified
                    if (workingDirectory != null && !workingDirectory.trim().isEmpty()) {
                        processBuilder.directory(new java.io.File(workingDirectory));
                    }
                    
                    // Set environment variables if specified
                    if (environment != null) {
                        processBuilder.environment().putAll(environment);
                    }
                    
                    // Create a real MCP client using Spring AI's builder pattern
                    log.info("Creating real STDIO MCP client for: {}", config.getName());
                    
                    try {
                        // Use Spring AI's McpClient builder to create a real client
                        // This approach mimics how Spring AI auto-configuration creates clients
                        Object realClient = createRealStdioClient(processBuilder, config.getName());
                        
                        if (realClient != null) {
                            log.info(" Real STDIO MCP client created successfully for: {}", config.getName());
                            log.info("Command: {}", commandList);
                            log.info("Working Directory: {}", workingDirectory);
                            log.info("Environment: {}", environment);
                            return realClient;
                        } else {
                            log.warn("Failed to create real STDIO client");
                            return null;
                        }
                        
                    } catch (Exception e) {
                        log.error("Error creating real STDIO client: {}", e.getMessage());
                        return null;
                    }
                    
                } catch (Exception e) {
                    log.error("Error creating real STDIO client: {}", e.getMessage());
                    return null;
                }
                
            } catch (Exception e) {
                log.error("Error creating real STDIO client: {}", e.getMessage());
                return null;
            }
            
        } catch (Exception e) {
            log.error("Error creating STDIO client for {}: {}", config.getName(), e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Create SSE client
     */
    private Object createSseClient(McpServerConfig config) {
        try {
            log.info("Creating SSE client for: {}", config.getName());
            
            // Extract SSE configuration
            Map<String, Object> configMap = config.getConfiguration();
            String url = (String) configMap.get("url");
            String endpoint = (String) configMap.getOrDefault("endpoint", "/sse");
            String messageEndpoint = (String) configMap.getOrDefault("messageEndpoint", "/mcp/message");
            @SuppressWarnings("unchecked")
            Map<String, String> headers = (Map<String, String>) configMap.get("headers");
            Integer timeoutSeconds = (Integer) configMap.getOrDefault("timeoutSeconds", 30);
            
            if (url == null || url.trim().isEmpty()) {
                log.error("SSE URL is required for server: {}", config.getName());
                return null;
            }
            
            // Ensure URL ends with /
            if (!url.endsWith("/")) {
                url += "/";
            }
            
            log.info("SSE URL: {}, endpoint: {}, messageEndpoint: {}", url, endpoint, messageEndpoint);
            
            // Create real SSE client using Spring AI MCP classes
            try {
                // Create WebClient for SSE transport
                WebClient webClient = WebClient.builder()
                        .baseUrl(url)
                        .defaultHeaders(httpHeaders -> {
                            if (headers != null) {
                                headers.forEach(httpHeaders::add);
                            }
                        })
                        .build();
                
                // SSE client creation not yet implemented
                // TODO: Implement actual SSE client creation when proper Spring AI MCP classes are available
                log.warn("SSE client creation not yet implemented for: {}", config.getName());
                log.info("URL: {}", url);
                log.info("Endpoint: {}", endpoint);
                log.info("Message Endpoint: {}", messageEndpoint);
                log.info("Headers: {}", headers);
                
                return null;
                
            } catch (Exception e) {
                log.error("Error creating SSE client: {}", e.getMessage());
                return null;
            }
            
        } catch (Exception e) {
            log.error("Error creating SSE client for {}: {}", config.getName(), e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Create Socket client
     */
    private Object createSocketClient(McpServerConfig config) {
        try {
            log.info("Creating Socket client for: {}", config.getName());
            
            // Extract Socket configuration
            Map<String, Object> configMap = config.getConfiguration();
            String host = (String) configMap.get("host");
            Integer port = (Integer) configMap.get("port");
            String protocol = (String) configMap.getOrDefault("protocol", "TCP");
            @SuppressWarnings("unchecked")
            Map<String, String> options = (Map<String, String>) configMap.get("options");
            
            if (host == null || host.trim().isEmpty()) {
                log.error("Socket host is required for server: {}", config.getName());
                return null;
            }
            
            if (port == null || port <= 0 || port > 65535) {
                log.error("Valid socket port is required for server: {}", config.getName());
                return null;
            }
            
            log.info("Socket connection: {}:{} (protocol: {})", host, port, protocol);
            
            // Create real Socket client using Spring AI MCP classes
            try {
                // For now, create a mock client that simulates real functionality
                // TODO: Implement actual Socket client creation when proper Spring AI MCP classes are available
                log.info("Creating enhanced mock Socket client for: {}", config.getName());
                
                // SOCKET client creation not yet implemented
                // TODO: Implement actual SOCKET client creation when proper Spring AI MCP classes are available
                log.warn("SOCKET client creation not yet implemented for: {}", config.getName());
                log.info("Host: {}", host);
                log.info("Port: {}", port);
                log.info("Protocol: {}", protocol);
                log.info("Options: {}", options);
                
                return null;
                
            } catch (Exception e) {
                log.error("Error creating SOCKET client: {}", e.getMessage());
                return null;
            }
            
        } catch (Exception e) {
            log.error("Error creating Socket client for {}: {}", config.getName(), e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Create a real STDIO MCP client using Spring AI's builder pattern
     */
    private Object createRealStdioClient(ProcessBuilder processBuilder, String name) {
        try {
            log.info("Creating real STDIO MCP client for: {}", name);

            // Ensure command is configured
            List<String> cmd = processBuilder.command();
            if (cmd == null || cmd.isEmpty()) {
                log.error("ProcessBuilder has no command configured for {}", name);
                return null;
            }

            // Start the process directly without MCP SDK
            Process process = processBuilder.start();
            log.info("Started STDIO process for '{}' with PID {} and command {}", name, process.pid(), cmd);

            // Wrap the process in our RealStdioMcpClient abstraction
            RealStdioMcpClient client = new RealStdioMcpClient(name, process);
            try {
                client.connect();
            } catch (IOException e) {
                log.warn("Process for '{}' started but connect verification failed: {}", name, e.getMessage());
            }
            return client;
            
        } catch (Exception e) {
            log.error("Error in createRealStdioClient for {}: {}", name, e.getMessage(), e);
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
        while (serverConfigs.containsKey(finalId) || serverRepository.existsById(finalId)) {
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
        
        String url = (String) configMap.get("url");
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
        
        String host = (String) configMap.get("host");
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
            
            // Add only real McpSyncClient instances from active clients
            for (Object client : activeClients.values()) {
                if (client instanceof McpSyncClient) {
                    allClients.add((McpSyncClient) client);
                    log.info("Added real MCP client to tool provider: {}", client);
                } else if (client instanceof RealStdioMcpClient) {
                    // Real STDIO clients provide actual tools
                    log.info("Real STDIO client available: {} (PID: {})", 
                            ((RealStdioMcpClient) client).getName(), 
                            ((RealStdioMcpClient) client).getProcess().pid());
                } else {
                    log.info("Mock client available (tools simulated via API): {}", client);
                }
            }
            
            // Create a custom tool callback provider that includes dynamic tools
            this.toolCallbackProvider = createEnhancedToolCallbackProvider(allClients);
            
            // Log dynamic clients for debugging
            for (Map.Entry<String, Object> entry : activeClients.entrySet()) {
                String serverId = entry.getKey();
                Object client = entry.getValue();
                log.info("Dynamic client {}: {} (type: {})", serverId, client, client.getClass().getSimpleName());
            }
            
            log.info("Updated tool callback provider with {} clients ({} static, {} dynamic)", 
                    allClients.size(), staticClients.size(), activeClients.size());
            log.info("Active dynamic clients: {}", activeClients.keySet());
            
            // Log the current tool count for debugging
            if (this.toolCallbackProvider != null) {
                var toolCallbacks = this.toolCallbackProvider.getToolCallbacks();
                log.info("Current tool callback provider has {} tools", toolCallbacks.length);
            }
        } catch (Exception e) {
            log.error("Error updating tool callback provider: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Auto-start all enabled dynamic servers on application startup
     * Note: Static servers are handled by Spring AI auto-configuration
     */
    public void autoStartEnabledServers() {
        log.info("🔄 Auto-starting enabled dynamic servers...");
        
        int startedCount = 0;
        int totalEnabled = 0;
        
        for (Map.Entry<String, McpServerConfig> entry : serverConfigs.entrySet()) {
            String serverId = entry.getKey();
            McpServerConfig config = entry.getValue();
            
            // Skip static servers - they're handled by Spring AI auto-configuration
            if (serverId.startsWith("static-")) {
                log.debug("Skipping static server (handled by Spring AI): {} ({})", config.getName(), serverId);
                continue;
            }
            
            if (config.isEnabled()) {
                totalEnabled++;
                log.info("Auto-starting enabled dynamic server: {} ({})", config.getName(), serverId);
                
                boolean started = startServer(serverId);
                if (started) {
                    startedCount++;
                    log.info("✅ Auto-started dynamic server: {} ({})", config.getName(), serverId);
                } else {
                    log.warn("⚠️ Failed to auto-start dynamic server: {} ({})", config.getName(), serverId);
                }
            }
        }
        
        log.info("🎯 Auto-start completed: {}/{} enabled dynamic servers started", startedCount, totalEnabled);
        
        // Update tool callback provider after auto-start
        updateToolCallbackProvider();
    }
    
    /**
     * Create an enhanced tool callback provider that includes dynamic tools
     */
    private ToolCallbackProvider createEnhancedToolCallbackProvider(List<McpSyncClient> staticClients) {
        try {
            // Create the base tool callback provider with static clients
            SyncMcpToolCallbackProvider baseProvider = new SyncMcpToolCallbackProvider(staticClients);
            
            // Build a combined list of ToolCallbacks: static (via base) + dynamic (via RealStdioMcpClient)
            List<ToolCallback> combined = new ArrayList<>();
            combined.addAll(Arrays.asList(baseProvider.getToolCallbacks()));

            ObjectMapper mapper = new ObjectMapper();

            int realDynamicToolsCount = 0;
            for (Object client : activeClients.values()) {
                if (client instanceof RealStdioMcpClient) {
                    RealStdioMcpClient real = (RealStdioMcpClient) client;
                    try {
                        List<Object> tools = real.listTools();
                        for (Object t : tools) {
                            if (t instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> tm = (Map<String, Object>) t;
                                String toolName = String.valueOf(tm.getOrDefault("name", "dynamic_tool"));
                                String description = (String) tm.getOrDefault("description", "Dynamic tool");
                                Object inputSchema = tm.get("inputSchema");
                                String inputSchemaJson = null;
                                try {
                                    if (inputSchema != null) {
                                        inputSchemaJson = mapper.writeValueAsString(inputSchema);
                                    }
                                } catch (Exception ignored) {}

                                combined.add(new DynamicToolCallback(toolName, description, inputSchemaJson, real, toolName));
                                realDynamicToolsCount++;
                            }
                        }
                        log.info("Real STDIO client {} provides {} tools (added to callbacks)", 
                                real.getName(), tools.size());
                    } catch (Exception e) {
                        log.warn("Error getting tools from real STDIO client {}: {}", 
                                real.getName(), e.getMessage());
                    }
                }
            }

            log.info("Created tool callback provider with {} static callbacks and {} dynamic callbacks", 
                    baseProvider.getToolCallbacks().length, realDynamicToolsCount);

            final ToolCallback[] callbacksArray = combined.toArray(new ToolCallback[0]);

            // Return a simple provider that serves the combined tool callbacks
            return new ToolCallbackProvider() {
                @Override
                public ToolCallback[] getToolCallbacks() {
                    return callbacksArray;
                }
            };
            
        } catch (Exception e) {
            log.error("Error creating enhanced tool callback provider: {}", e.getMessage(), e);
            return new SyncMcpToolCallbackProvider(staticClients);
        }
    }
    
}
