package com.vijay.controller;

import com.vijay.service.MCPService;
import com.vijay.service.RAGService;
import com.vijay.service.SimpleRAGService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MCPRAGController {

    private final MCPService mcpService;
    private final RAGService ragService;
    private final SimpleRAGService simpleRAGService;

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
            // Try full RAG service first
            boolean ragAvailable = ragService.isRAGAvailable();
            if (ragAvailable) {
                status.put("available", true);
                status.put("implementation", "Full RAG with Vector Store");
                status.put("vectorStore", "PostgreSQL with pgvector");
                status.put("embeddingModel", "Local Ollama Models");
            } else {
                // Fallback to simple RAG
                boolean simpleRAGAvailable = simpleRAGService.isRAGAvailable();
                status.put("available", simpleRAGAvailable);
                status.put("implementation", "Simple RAG (Demo Version)");
                status.put("note", "Using simplified implementation for demonstration");
            }
            
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

    // ==================== PDF DOCUMENT MANAGEMENT ====================

    /**
     * Load PDF document from resources into vector store
     */
    @PostMapping("/rag/load-pdf")
    public ResponseEntity<Map<String, Object>> loadPDFDocument(@RequestParam String pdfFileName) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            log.info("Received request to load PDF document: {}", pdfFileName);
            
            // Try full RAG service first
            boolean success = ragService.loadPDFDocument(pdfFileName);
            
            if (!success) {
                // Fallback to simple RAG
                success = simpleRAGService.loadPDFDocument(pdfFileName);
            }
            
            if (success) {
                response.put("success", true);
                response.put("message", "PDF document loaded successfully!");
                response.put("fileName", pdfFileName);
                response.put("implementation", ragService.isRAGAvailable() ? "Full RAG" : "Simple RAG");
                log.info("✅ Successfully loaded PDF document: {}", pdfFileName);
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", "Failed to load PDF document. Check logs for details.");
                response.put("fileName", pdfFileName);
                log.error("❌ Failed to load PDF document: {}", pdfFileName);
                return ResponseEntity.badRequest().body(response);
            }
            
        } catch (Exception e) {
            log.error("Error loading PDF document {}: {}", pdfFileName, e.getMessage());
            response.put("success", false);
            response.put("message", "Error loading PDF: " + e.getMessage());
            response.put("fileName", pdfFileName);
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Get RAG document statistics
     */
    @GetMapping("/rag/stats")
    public ResponseEntity<Map<String, Object>> getRAGStats() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            Map<String, Object> ragStats = ragService.getDocumentStats();
            stats.putAll(ragStats);
            stats.put("timestamp", System.currentTimeMillis());
            
            log.info("Retrieved RAG statistics");
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            log.error("Error retrieving RAG stats: {}", e.getMessage());
            stats.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(stats);
        }
    }

    // ==================== PDF CHAT ENDPOINTS ====================

    /**
     * Chat with PDF document using RAG
     */
    @PostMapping("/rag/chat")
    public ResponseEntity<Map<String, Object>> chatWithPDF(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String question = request.get("question");
            String pdfFileName = request.getOrDefault("pdfFileName", "vijay.pdf");
            
            if (question == null || question.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "Question is required");
                return ResponseEntity.badRequest().body(response);
            }
            
            log.info("Received PDF chat request for document: {} with question: {}", pdfFileName, question);
            
            String answer;
            String implementation;
            
            // Try full RAG service first
            if (ragService.isRAGAvailable()) {
                answer = ragService.chatWithPDF(question, pdfFileName);
                implementation = "Full RAG";
            } else {
                // Fallback to simple RAG
                answer = simpleRAGService.chatWithPDF(question, pdfFileName);
                implementation = "Simple RAG";
            }
            
            response.put("success", true);
            response.put("question", question);
            response.put("answer", answer);
            response.put("pdfFileName", pdfFileName);
            response.put("implementation", implementation);
            response.put("timestamp", System.currentTimeMillis());
            
            log.info("✅ Successfully generated PDF chat response");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error in PDF chat: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "Error processing PDF chat: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Enhanced chat with PDF using RAG + MCP integration
     */
    @PostMapping("/mcp-rag/chat")
    public ResponseEntity<Map<String, Object>> enhancedChatWithPDF(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String question = request.get("question");
            String pdfFileName = request.getOrDefault("pdfFileName", "vijay.pdf");
            String mcpServer = request.getOrDefault("mcpServer", "my-mcp-server");
            
            if (question == null || question.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "Question is required");
                return ResponseEntity.badRequest().body(response);
            }
            
            log.info("Received enhanced chat request - PDF: {}, MCP: {}, Question: {}", 
                pdfFileName, mcpServer, question);
            
            // 1. Get RAG response from PDF
            String ragAnswer = ragService.chatWithPDF(question, pdfFileName);
            
            // 2. Enhance with MCP if available
            String finalAnswer = ragAnswer;
            if (mcpService.isMCPServerAvailable(mcpServer)) {
                try {
                    // Send RAG answer to MCP for additional processing
                    String mcpEnhanced = mcpService.sendToMCPServer(mcpServer, ragAnswer)
                        .collectList()
                        .block()
                        .stream()
                        .reduce((a, b) -> a + " " + b)
                        .orElse(ragAnswer);
                    
                    finalAnswer = mcpEnhanced;
                    log.info("✅ Enhanced answer with MCP server: {}", mcpServer);
                } catch (Exception e) {
                    log.warn("MCP enhancement failed, using RAG answer only: {}", e.getMessage());
                }
            }
            
            response.put("success", true);
            response.put("question", question);
            response.put("answer", finalAnswer);
            response.put("pdfFileName", pdfFileName);
            response.put("mcpServer", mcpServer);
            response.put("mcpEnhanced", mcpService.isMCPServerAvailable(mcpServer));
            response.put("timestamp", System.currentTimeMillis());
            
            log.info("✅ Successfully generated enhanced chat response");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error in enhanced PDF chat: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "Error processing enhanced chat: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}
