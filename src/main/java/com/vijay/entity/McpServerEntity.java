package com.vijay.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.HashMap;

/**
 * JPA Entity for persisting MCP server configurations in MySQL database
 * This ensures dynamic servers survive application restarts
 */
@Entity
@Table(name = "mcp_servers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class McpServerEntity {
    
    @Id
    @Column(name = "id", unique = true, nullable = false, length = 100)
    private String id;
    
    @Column(name = "name", nullable = false, length = 200)
    private String name;
    
    @Column(name = "description", length = 1000)
    private String description;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "transport_type", nullable = false, length = 20)
    private TransportType transportType;
    
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ServerStatus status;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "last_started")
    private LocalDateTime lastStarted;
    
    @Column(name = "last_stopped")
    private LocalDateTime lastStopped;
    
    // STDIO Configuration
    @Column(name = "command", length = 500)
    private String command;
    
    @Column(name = "args", columnDefinition = "TEXT")
    private String args;
    
    @Column(name = "working_directory", length = 500)
    private String workingDirectory;
    
    // SSE Configuration
    @Column(name = "url", length = 500)
    private String url;
    
    @Column(name = "endpoint", length = 200)
    private String endpoint;
    
    @Column(name = "message_endpoint", length = 200)
    private String messageEndpoint;
    
    // Socket Configuration
    @Column(name = "host", length = 200)
    private String host;
    
    @Column(name = "port")
    private Integer port;
    
    @Column(name = "protocol", length = 20)
    private String protocol;
    
    // Common Configuration
    @Column(name = "timeout_seconds")
    private Integer timeoutSeconds;
    
    @Column(name = "headers", columnDefinition = "TEXT")
    private String headers;
    
    // Error tracking
    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;
    
    @Column(name = "error_count")
    private Integer errorCount;
    
    // Statistics
    @Column(name = "start_count")
    private Integer startCount;
    
    @Column(name = "stop_count")
    private Integer stopCount;
    
    @Column(name = "total_runtime_seconds")
    private Long totalRuntimeSeconds;
    
    /**
     * Transport types for MCP servers
     */
    public enum TransportType {
        STDIO, SSE, SOCKET
    }
    
    /**
     * Server statuses
     */
    public enum ServerStatus {
        STOPPED, STARTING, RUNNING, STOPPING, ERROR, DISABLED
    }
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        
        // Set default values
        if (enabled == null) enabled = true;
        if (status == null) status = ServerStatus.STOPPED;
        if (errorCount == null) errorCount = 0;
        if (startCount == null) startCount = 0;
        if (stopCount == null) stopCount = 0;
        if (totalRuntimeSeconds == null) totalRuntimeSeconds = 0L;
        if (timeoutSeconds == null) timeoutSeconds = 30;
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    /**
     * Convert to DTO
     */
    public com.vijay.dto.McpServerConfig toDto() {
        com.vijay.dto.McpServerConfig config = new com.vijay.dto.McpServerConfig();
        config.setId(this.id);
        config.setName(this.name);
        config.setDescription(this.description);
        config.setTransportType(com.vijay.dto.McpServerConfig.McpTransportType.valueOf(this.transportType.name()));
        config.setEnabled(this.enabled);
        
        // Set configuration based on transport type
        Map<String, Object> configMap = new HashMap<>();
        switch (this.transportType) {
            case STDIO:
                configMap.put("command", this.command);
                if (this.args != null && !this.args.isEmpty()) {
                    configMap.put("args", java.util.Arrays.asList(this.args.split(",")));
                }
                configMap.put("workingDirectory", this.workingDirectory);
                break;
                
            case SSE:
                configMap.put("url", this.url);
                configMap.put("endpoint", this.endpoint != null ? this.endpoint : "/sse");
                configMap.put("messageEndpoint", this.messageEndpoint != null ? this.messageEndpoint : "/mcp/message");
                configMap.put("timeoutSeconds", this.timeoutSeconds);
                break;
                
            case SOCKET:
                configMap.put("host", this.host);
                configMap.put("port", this.port);
                configMap.put("protocol", this.protocol != null ? this.protocol : "TCP");
                break;
        }
        config.setConfiguration(configMap);
        
        return config;
    }
    
    /**
     * Create from DTO
     */
    public static McpServerEntity fromDto(com.vijay.dto.McpServerConfig dto) {
        McpServerEntity entity = McpServerEntity.builder()
            .id(dto.getId())
            .name(dto.getName())
            .description(dto.getDescription())
            .transportType(TransportType.valueOf(dto.getTransportType().name()))
            .enabled(dto.isEnabled())
            .status(ServerStatus.STOPPED)
            .build();
        
        // Set configuration based on transport type
        if (dto.getConfiguration() != null && dto.getConfiguration() instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> configMap = (Map<String, Object>) dto.getConfiguration();
            
            switch (dto.getTransportType()) {
                case STDIO:
                    entity.setCommand((String) configMap.get("command"));
                    if (configMap.get("args") instanceof java.util.List) {
                        @SuppressWarnings("unchecked")
                        java.util.List<String> args = (java.util.List<String>) configMap.get("args");
                        entity.setArgs(String.join(",", args));
                    }
                    entity.setWorkingDirectory((String) configMap.get("workingDirectory"));
                    break;
                    
                case SSE:
                    entity.setUrl((String) configMap.get("url"));
                    entity.setEndpoint((String) configMap.getOrDefault("endpoint", "/sse"));
                    entity.setMessageEndpoint((String) configMap.getOrDefault("messageEndpoint", "/mcp/message"));
                    if (configMap.get("timeoutSeconds") instanceof Integer) {
                        entity.setTimeoutSeconds((Integer) configMap.get("timeoutSeconds"));
                    }
                    break;
                    
                case SOCKET:
                    entity.setHost((String) configMap.get("host"));
                    if (configMap.get("port") instanceof Integer) {
                        entity.setPort((Integer) configMap.get("port"));
                    }
                    entity.setProtocol((String) configMap.getOrDefault("protocol", "TCP"));
                    break;
            }
        }
        
        return entity;
    }
}
