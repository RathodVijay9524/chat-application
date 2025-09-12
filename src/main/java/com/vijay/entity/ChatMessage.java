package com.vijay.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import com.fasterxml.jackson.annotation.JsonBackReference;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class ChatMessage {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String conversationId; // UUID from frontend
    
    @Column(nullable = false)
    private String userId;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String userMessage;
    
    @Column(columnDefinition = "TEXT")
    private String aiResponse;
    
    @Column(nullable = false, length = 100)
    private String provider;
    
    @Column(nullable = false, length = 500)
    private String model;
    
    @Column
    private Long tokensUsed;
    
    @Column
    private Long responseTimeMs;
    
    @Column(columnDefinition = "TEXT")
    private String error;
    
    @Column
    private boolean isSuccessful;
    
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;
    
    // Additional metadata
    @Column(columnDefinition = "TEXT")
    private String ragContext; // RAG context used for this message
    
    @Column(columnDefinition = "TEXT")
    private String mcpToolsUsed; // MCP tools used in this conversation
    
    @Column
    private Double temperature;
    
    @Column
    private Integer maxTokens;
    
    // Relationship with conversation
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversationId", referencedColumnName = "conversationId", insertable = false, updatable = false)
    @JsonBackReference
    private Conversation conversation;
    
    // Explicit getter and setter for isSuccessful to fix compilation issue
    public boolean isSuccessful() {
        return isSuccessful;
    }
    
    public void setIsSuccessful(boolean successful) {
        this.isSuccessful = successful;
    }
}