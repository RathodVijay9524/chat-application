package com.vijay.service;

import lombok.extern.slf4j.Slf4j;

/**
 * Mock MCP Sync Client for testing purposes
 * This allows us to test the API endpoints without requiring actual MCP servers
 * 
 * Note: This is a simplified mock that doesn't extend McpSyncClient to avoid
 * complex inheritance issues. It's used for testing the API endpoints.
 */
@Slf4j
public class MockMcpSyncClient {
    
    private final String name;
    private final String type;
    
    public MockMcpSyncClient(String name, String type) {
        this.name = name;
        this.type = type;
        log.info("Created mock MCP client: {} (type: {})", name, type);
    }
    
    public String getName() {
        return name;
    }
    
    public String getType() {
        return type;
    }
    
    public void closeGracefully() {
        log.info("Mock MCP client closed: {}", name);
    }
    
    @Override
    public String toString() {
        return "MockMcpSyncClient{name='" + name + "', type='" + type + "'}";
    }
}
