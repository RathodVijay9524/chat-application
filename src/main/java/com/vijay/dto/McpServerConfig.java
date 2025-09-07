package com.vijay.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpServerConfig {
    
    private String id;
    private String name;
    private String description;
    private McpTransportType transportType;
    private boolean enabled;
    private Map<String, Object> configuration;
    
    // STDIO specific configuration
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StdioConfig {
        private String command;
        private List<String> args;
        private String workingDirectory;
        private Map<String, String> environment;
    }
    
    // SSE specific configuration
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SseConfig {
        private String url;
        private String endpoint;
        private String messageEndpoint;
        private Map<String, String> headers;
        private int timeoutSeconds;
    }
    
    // Socket specific configuration
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SocketConfig {
        private String host;
        private int port;
        private String protocol; // TCP, UDP, etc.
        private Map<String, String> options;
    }
    
    public enum McpTransportType {
        STDIO,
        SSE,
        SOCKET
    }
}
