package com.vijay.config;

import com.vijay.service.DynamicMcpServerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Configuration component to load MCP servers from MySQL database on application startup
 */
@Slf4j
@Component
public class McpServerStartupConfig {
    
    @Autowired
    private DynamicMcpServerService mcpServerService;
    
    /**
     * Load MCP servers from MySQL database and auto-start enabled servers when application is ready
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("🚀 Application ready - loading MCP servers from MySQL database...");
        try {
            // Load servers from database
            mcpServerService.loadServersFromDatabase();
            
            // Auto-start all enabled servers
            log.info("🔄 Auto-starting enabled dynamic servers...");
            mcpServerService.autoStartEnabledServers();
            
            log.info("✅ MCP server startup configuration completed");
        } catch (Exception e) {
            log.error("❌ Error during MCP server startup configuration: {}", e.getMessage(), e);
        }
    }
}
