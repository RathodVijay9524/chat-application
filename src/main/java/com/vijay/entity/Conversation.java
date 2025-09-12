package com.vijay.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import com.fasterxml.jackson.annotation.JsonManagedReference;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "conversations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Conversation {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String conversationId; // UUID from frontend
    
    @Column(nullable = false)
    private String userId;
    
    @Column(nullable = false)
    private String provider;
    
    @Column(nullable = false)
    private String model;
    
    @Column
    private Double temperature;
    
    @Column
    private Integer maxTokens;
    
    @Column(columnDefinition = "TEXT")
    private String title; // Auto-generated or user-defined conversation title
    
    @Column
    private boolean isActive;
    
    @Column
    private Long totalMessages;
    
    @Column
    private Long totalTokensUsed;
    
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    private LocalDateTime updatedAt;
    
    // Relationship with chat messages
    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<ChatMessage> messages;
    
    // Explicit getter and setter for isActive to fix compilation issue
    public boolean isActive() {
        return isActive;
    }
    
    public void setIsActive(boolean active) {
        this.isActive = active;
    }
}
