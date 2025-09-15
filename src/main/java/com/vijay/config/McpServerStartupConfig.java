package com.vijay.config;

import com.vijay.dto.McpServerConfig;

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
            mcpServerService.loadServersFromMemory();
            
            // Auto-start all enabled dynamic servers (skip static servers)
            log.info("🔄 Auto-starting enabled dynamic servers...");
            try {
                mcpServerService.getAllServers().stream()
                    .filter(McpServerConfig::isEnabled)
                    .filter(config -> !config.getId().startsWith("static-")) // Skip static servers
                    .forEach(config -> {
                        log.info("🚀 Starting dynamic server: {} ({})", config.getName(), config.getId());
                        mcpServerService.startServer(config.getId());
                    });
                
                log.info("✅ Dynamic server startup completed (static servers handled by Spring AI)");
            } catch (Exception e) {
                log.warn("⚠️ Some MCP servers failed to start, but continuing with application startup: {}", e.getMessage());
                // Don't fail the entire application startup if MCP servers fail
            }
            
            log.info("✅ MCP server startup configuration completed");
        } catch (Exception e) {
            log.error("❌ Error during MCP server startup configuration: {}", e.getMessage(), e);
            // Don't fail the entire application startup if MCP configuration fails
            log.warn("⚠️ Continuing with application startup despite MCP configuration errors");
        }
    }
}
