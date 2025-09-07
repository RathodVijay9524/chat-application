package com.vijay.controller;

import com.vijay.service.MCPService;
import com.vijay.service.RAGService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MCPRAGController {

    private final MCPService mcpService;
    private final RAGService ragService;

    @GetMapping("/mcp/status")
    public Map<String, Object> getMCPStatus() {
        Map<String, Object> status = new HashMap<>();
        
        try {
            Map<String, String> servers = mcpService.getAvailableMCPServers();
            Map<String, Object> serverStatus = new HashMap<>();
            
            for (String serverName : servers.keySet()) {
                serverStatus.put(serverName, mcpService.getMCPServerStatus(serverName));
            }
            
            status.put("available", true);
            status.put("servers", servers);
            status.put("serverStatus", serverStatus);
            status.put("totalServers", servers.size());
            
        } catch (Exception e) {
            log.error("Error checking MCP status: {}", e.getMessage());
            status.put("available", false);
            status.put("error", e.getMessage());
        }
        
        return status;
    }

    @GetMapping("/rag/status")
    public Map<String, Object> getRAGStatus() {
        Map<String, Object> status = new HashMap<>();
        
        try {
            boolean ragAvailable = ragService.isRAGAvailable();
            status.put("available", ragAvailable);
            status.put("vectorStore", "PostgreSQL with pgvector");
            status.put("embeddingModel", "text-embedding-3-small");
            
        } catch (Exception e) {
            log.error("Error checking RAG status: {}", e.getMessage());
            status.put("available", false);
            status.put("error", e.getMessage());
        }
        
        return status;
    }

    @GetMapping("/mcp-rag/status")
    public Map<String, Object> getMCPRAGStatus() {
        Map<String, Object> status = new HashMap<>();
        
        try {
            Map<String, Object> mcpStatus = getMCPStatus();
            Map<String, Object> ragStatus = getRAGStatus();
            
            status.put("mcp", mcpStatus);
            status.put("rag", ragStatus);
            status.put("integrated", (Boolean) mcpStatus.get("available") && (Boolean) ragStatus.get("available"));
            
        } catch (Exception e) {
            log.error("Error checking MCP+RAG status: {}", e.getMessage());
            status.put("error", e.getMessage());
        }
        
        return status;
    }
}
