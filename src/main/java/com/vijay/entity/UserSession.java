package com.vijay.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class UserSession {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String userId;
    
    @Column(nullable = false, unique = true)
    private String sessionToken;
    
    @Column(nullable = false)
    private LocalDateTime expiresAt;
    
    @Column(nullable = false)
    private boolean isActive;
    
    @Column(columnDefinition = "TEXT")
    private String userAgent;
    
    @Column(length = 45)
    private String ipAddress;
    
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    private LocalDateTime updatedAt;
    
    // Additional session metadata
    @Column(columnDefinition = "TEXT")
    private String sessionData; // JSON string for additional session info
    
    @Column(length = 200)
    private String lastActivity; // Last endpoint accessed
    
    @Column
    private LocalDateTime lastActivityAt;
    
    // Explicit getter and setter for isActive to fix compilation issue
    public boolean getIsActive() {
        return isActive;
    }
    
    public void setIsActive(boolean active) {
        this.isActive = active;
    }
}
