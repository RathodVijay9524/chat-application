package com.vijay.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SimpleRAGService {
    
    // Add instance tracking for debugging
    private static int instanceCount = 0;
    private final int instanceId;

    private final Map<String, List<String>> documentContent = new HashMap<>();
    private boolean ragAvailable = false;
    
    public SimpleRAGService() {
        instanceId = ++instanceCount;
        log.info("🔧 SimpleRAGService instance #{} created", instanceId);
    }

    /**
     * Load PDF document from resources (simulated)
     */
    public boolean loadPDFDocument(String pdfFileName) {
        try {
            Resource pdfResource = new ClassPathResource("docks/" + pdfFileName);
            if (!pdfResource.exists()) {
                log.error("Simple RAG: PDF file not found: {}", pdfFileName);
                return false;
            }

            // Read the file content line by line
            List<String> lines;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(pdfResource.getInputStream()))) {
                lines = reader.lines().collect(Collectors.toList());
            }
            
            documentContent.put(pdfFileName, lines);
            ragAvailable = true;
            
            log.info("✅ Simple RAG (Instance #{}): Loaded PDF '{}' with {} lines", instanceId, pdfFileName, lines.size());
            log.info("📊 Simple RAG (Instance #{}): Total documents loaded: {}", instanceId, documentContent.size());
            return true;
            
        } catch (Exception e) {
            log.error("❌ Simple RAG: Error loading PDF '{}': {}", pdfFileName, e.getMessage());
            return false;
        }
    }

    /**
     * Search for relevant content (simplified)
     */
    public String searchRelevantContent(String query, String pdfFileName) {
        if (!documentContent.containsKey(pdfFileName)) {
            return "";
        }
        
        List<String> lines = documentContent.get(pdfFileName);
        String lowerQuery = query.toLowerCase();
        
        // Find lines that contain keywords from the query
        List<String> relevantLines = new ArrayList<>();
        String[] queryWords = lowerQuery.split("\\s+");
        
        for (String line : lines) {
            String lowerLine = line.toLowerCase();
            boolean isRelevant = false;
            
            // Check if any query word appears in the line
            for (String word : queryWords) {
                if (word.length() > 2 && lowerLine.contains(word)) { // Skip short words
                    isRelevant = true;
                    break;
                }
            }
            
            if (isRelevant) {
                relevantLines.add(line.trim());
            }
        }
        
        if (relevantLines.isEmpty()) {
            return "No specific content found for: " + query;
        }
        
        // Return top 5 most relevant lines
        return String.join(" ", relevantLines.stream().limit(5).collect(Collectors.toList()));
    }

    /**
     * Chat with PDF document (simplified RAG)
     */
    public String chatWithPDF(String userQuestion, String pdfFileName) {
        log.info("🔍 Simple RAG (Instance #{}): Chat request for '{}' with question: '{}'", instanceId, pdfFileName, userQuestion);
        log.info("📊 Simple RAG (Instance #{}): Available documents: {}", instanceId, documentContent.keySet());
        
        List<String> content = documentContent.get(pdfFileName);
        if (content == null || content.isEmpty()) {
            log.warn("⚠️ Simple RAG (Instance #{}): Document '{}' not found in loaded documents", instanceId, pdfFileName);
            return "Document '" + pdfFileName + "' not loaded. Please load it first.";
        }

        String lowerQuestion = userQuestion.toLowerCase();
        
        // Handle general questions about the document
        if (lowerQuestion.contains("what") && (lowerQuestion.contains("about") || lowerQuestion.contains("document"))) {
            return getDocumentOverview(pdfFileName, content);
        }
        
        if (lowerQuestion.contains("summary") || lowerQuestion.contains("overview")) {
            return getDocumentOverview(pdfFileName, content);
        }

        // Simple keyword-based retrieval
        List<String> relevantLines = content.stream()
                .filter(line -> line.toLowerCase().contains(userQuestion.toLowerCase()))
                .limit(5) // Limit to top 5 relevant lines
                .collect(Collectors.toList());

        if (relevantLines.isEmpty()) {
            return "I couldn't find relevant information in '" + pdfFileName + "' for your question: " + userQuestion;
        }

        return "Based on '" + pdfFileName + "': " + String.join(" ", relevantLines);
    }
    
    /**
     * Get document overview for general questions
     */
    private String getDocumentOverview(String pdfFileName, List<String> content) {
        StringBuilder overview = new StringBuilder();
        overview.append("Document Overview for '").append(pdfFileName).append("':\n\n");
        
        // Find key sections
        for (String line : content) {
            String lowerLine = line.toLowerCase().trim();
            if (lowerLine.contains("professional summary") || 
                lowerLine.contains("contact information") ||
                lowerLine.contains("education") ||
                lowerLine.contains("experience") ||
                lowerLine.contains("skills") ||
                lowerLine.contains("technologies")) {
                overview.append("• ").append(line.trim()).append("\n");
            }
        }
        
        // Add first few lines as general content
        overview.append("\nKey Information:\n");
        content.stream()
            .filter(line -> !line.trim().isEmpty())
            .limit(10)
            .forEach(line -> overview.append("- ").append(line.trim()).append("\n"));
            
        return overview.toString();
    }

    /**
     * Get document statistics
     */
    public Map<String, Object> getDocumentStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("ragAvailable", ragAvailable);
        stats.put("documentsLoaded", documentContent.size());
        stats.put("documentNames", documentContent.keySet());
        stats.put("implementation", "Simple RAG (Demo Version)");
        stats.put("note", "This is a simplified implementation for demonstration purposes");
        
        return stats;
    }

    /**
     * Check if RAG is available
     */
    public boolean isRAGAvailable() {
        return ragAvailable;
    }
}
