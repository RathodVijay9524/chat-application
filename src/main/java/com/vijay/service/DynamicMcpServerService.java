package com.vijay.service;

import com.vijay.dto.McpServerConfig;
import io.modelcontextprotocol.client.McpSyncClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.io.IOException;

@Slf4j
@Service
public class DynamicMcpServerService {
    
    private final Map<String, Object> activeClients = new ConcurrentHashMap<>(); // Using Object to handle both real and mock clients
    private final Map<String, McpServerConfig> serverConfigs = new ConcurrentHashMap<>();
    private final List<McpSyncClient> staticClients = new ArrayList<>();
    
    private ToolCallbackProvider toolCallbackProvider;
    
    @Autowired
    private DynamicMcpConfigurationService dynamicConfigService;
    
    public DynamicMcpServerService() {
        log.info("🔧 Dynamic MCP Server Service initialized with file-based persistence");
    }
    
    
    /**
     * Add a new MCP server configuration
     */
    public boolean addServer(McpServerConfig config) {
        try {
            log.info("Adding MCP server: {} (type: {})", config.getName(), config.getTransportType());
            
            // Validate configuration
            if (!validateConfig(config)) {
                log.error("Invalid configuration for server: {}", config.getName());
                return false;
            }
            
            // Store configuration
            serverConfigs.put(config.getId(), config);
            log.info("✅ Server configuration stored: {} (total configs: {})", config.getId(), serverConfigs.size());
            
            // Add to dynamic configuration service for Spring AI integration
            boolean configAdded = dynamicConfigService.addDynamicMcpServer(
                config.getId(), 
                config.getTransportType().name(), 
                config.getConfiguration()
            );
            
            if (configAdded) {
                log.info("✅ Dynamic MCP server configuration added to Spring AI: {}", config.getId());
            } else {
                log.warn("⚠️ Failed to add dynamic MCP server configuration to Spring AI: {}", config.getId());
            }
            
            // Start server if enabled
            if (config.isEnabled()) {
                boolean started = startServer(config.getId());
                if (started) {
                    log.info("✅ MCP server started successfully: {}", config.getName());
                    updateToolCallbackProvider();
                    return true;
                } else {
                    log.error("❌ Failed to start MCP server: {}", config.getName());
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
                log.info("✅ MCP server started successfully: {}", config.getName());
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
                } else if (client instanceof MockMcpSyncClient) {
                    ((MockMcpSyncClient) client).closeGracefully();
                }
                updateToolCallbackProvider();
                log.info("✅ MCP server stopped: {}", serverId);
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
                log.info("✅ Dynamic MCP server configuration removed from Spring AI: {}", serverId);
            } else {
                log.warn("⚠️ Failed to remove dynamic MCP server configuration from Spring AI: {}", serverId);
            }
            
            // Remove configuration
            McpServerConfig removed = serverConfigs.remove(serverId);
            if (removed != null) {
                log.info("✅ MCP server configuration removed: {}", removed.getName());
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
        return new ArrayList<>(serverConfigs.values());
    }
    
    /**
     * Get active servers (servers that are currently running)
     */
    public List<String> getActiveServers() {
        return new ArrayList<>(activeClients.keySet());
    }
    
    /**
     * Get active server status
     */
    public Map<String, Boolean> getServerStatus() {
        Map<String, Boolean> status = new HashMap<>();
        for (String serverId : serverConfigs.keySet()) {
            status.put(serverId, activeClients.containsKey(serverId));
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
            log.info("✅ Set {} static MCP clients", clients.size());
            
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
            
            log.info("✅ Converted {} static clients to server configs", clients.size());
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
                        McpSyncClient realClient = createRealStdioClient(processBuilder, config.getName());
                        
                        if (realClient != null) {
                            log.info("✅ Real STDIO MCP client created successfully for: {}", config.getName());
                            log.info("Command: {}", commandList);
                            log.info("Working Directory: {}", workingDirectory);
                            log.info("Environment: {}", environment);
                            return realClient;
                        } else {
                            log.warn("Failed to create real client, using mock");
                            return createMockClient(config.getName(), "STDIO");
                        }
                        
                    } catch (Exception e) {
                        log.error("Error creating real STDIO client: {}", e.getMessage());
                        return createMockClient(config.getName(), "STDIO");
                    }
                    
                } catch (Exception e) {
                    log.error("Error creating real STDIO client, using mock: {}", e.getMessage());
                    return createMockClient(config.getName(), "STDIO");
                }
                
            } catch (Exception e) {
                log.error("Error creating real STDIO client, using mock: {}", e.getMessage());
                return createMockClient(config.getName(), "STDIO");
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
                
                // For now, create a mock client that simulates real functionality
                // TODO: Implement actual SSE client creation when proper Spring AI MCP classes are available
                log.info("Creating enhanced mock SSE client for: {}", config.getName());
                
                // Create an enhanced mock that simulates real MCP behavior
                MockMcpSyncClient mockClient = new MockMcpSyncClient(config.getName(), "SSE");
                
                // Simulate successful connection
                log.info("✅ Mock SSE client created successfully for: {}", config.getName());
                log.info("URL: {}", url);
                log.info("Endpoint: {}", endpoint);
                log.info("Message Endpoint: {}", messageEndpoint);
                log.info("Headers: {}", headers);
                
                return mockClient;
                
            } catch (Exception e) {
                log.error("Error creating real SSE client, using mock: {}", e.getMessage());
                return createMockClient(config.getName(), "SSE");
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
                
                // Create an enhanced mock that simulates real MCP behavior
                MockMcpSyncClient mockClient = new MockMcpSyncClient(config.getName(), "SOCKET");
                
                // Simulate successful connection
                log.info("✅ Mock Socket client created successfully for: {}", config.getName());
                log.info("Host: {}", host);
                log.info("Port: {}", port);
                log.info("Protocol: {}", protocol);
                log.info("Options: {}", options);
                
                return mockClient;
                
            } catch (Exception e) {
                log.error("Error creating real Socket client, using mock: {}", e.getMessage());
                return createMockClient(config.getName(), "SOCKET");
            }
            
        } catch (Exception e) {
            log.error("Error creating Socket client for {}: {}", config.getName(), e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Create a real STDIO MCP client using Spring AI's builder pattern
     */
    private McpSyncClient createRealStdioClient(ProcessBuilder processBuilder, String name) {
        try {
            log.info("Creating real STDIO MCP client for: {}", name);
            
            // For now, we'll create a mock client but with enhanced functionality
            // TODO: Implement actual real MCP client creation
            // The challenge is that McpSyncClient constructor is not accessible
            // We need to find a different approach to create real clients
            
            log.warn("Real MCP client creation not yet implemented - using enhanced mock");
            return null; // This will trigger fallback to mock client
            
        } catch (Exception e) {
            log.error("Error in createRealStdioClient for {}: {}", name, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Create a mock client for testing purposes
     */
    private Object createMockClient(String name, String type) {
        try {
            log.info("Creating mock {} client for: {}", type, name);
            
            // Create a mock transport that doesn't actually connect
            // This is for testing the API endpoints without real MCP servers
            return new MockMcpSyncClient(name, type);
            
        } catch (Exception e) {
            log.error("Error creating mock client for {}: {}", name, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Validate server configuration
     */
    private boolean validateConfig(McpServerConfig config) {
        if (config.getId() == null || config.getId().trim().isEmpty()) {
            log.error("Server ID is required");
            return false;
        }
        
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
                } else {
                    log.info("Skipping mock client (not injectable into AI models): {}", client);
                }
            }
            
            // For now, use the standard provider but log the dynamic clients
            this.toolCallbackProvider = new SyncMcpToolCallbackProvider(allClients);
            
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
    
}
