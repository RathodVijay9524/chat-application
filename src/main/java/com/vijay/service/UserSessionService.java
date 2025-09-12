package com.vijay.service;

import com.vijay.entity.UserSession;
import com.vijay.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserSessionService {
    
    private final UserSessionRepository userSessionRepository;
    
    /**
     * Create a new user session
     */
    @Transactional
    public UserSession createSession(String userId, String userAgent, String ipAddress) {
        // Clean up expired sessions for this user
        cleanupExpiredSessions(userId);
        
        // Create new session
        String sessionToken = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(24); // 24 hours session
        
        UserSession session = UserSession.builder()
                .userId(userId)
                .sessionToken(sessionToken)
                .expiresAt(expiresAt)
                .isActive(true)
                .userAgent(userAgent)
                .ipAddress(ipAddress)
                .lastActivityAt(LocalDateTime.now())
                .build();
        
        UserSession savedSession = userSessionRepository.save(session);
        log.info("Created new session for user {}: {}", userId, sessionToken);
        
        return savedSession;
    }
    
    /**
     * Validate and update session activity
     */
    @Transactional
    public boolean validateAndUpdateSession(String sessionToken, String lastActivity) {
        Optional<UserSession> sessionOpt = userSessionRepository.findBySessionToken(sessionToken);
        
        if (sessionOpt.isEmpty()) {
            log.warn("Session not found: {}", sessionToken);
            return false;
        }
        
        UserSession session = sessionOpt.get();
        
        // Check if session is expired
        if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
            log.warn("Session expired: {}", sessionToken);
            session.setIsActive(false);
            userSessionRepository.save(session);
            return false;
        }
        
        // Update last activity
        session.setLastActivity(lastActivity);
        session.setLastActivityAt(LocalDateTime.now());
        userSessionRepository.save(session);
        
        log.debug("Updated session activity: {} -> {}", sessionToken, lastActivity);
        return true;
    }
    
    /**
     * Get active sessions for a user
     */
    public List<UserSession> getActiveSessions(String userId) {
        return userSessionRepository.findActiveSessionsByUserId(userId, LocalDateTime.now());
    }
    
    /**
     * Invalidate a session
     */
    @Transactional
    public void invalidateSession(String sessionToken) {
        Optional<UserSession> sessionOpt = userSessionRepository.findBySessionToken(sessionToken);
        if (sessionOpt.isPresent()) {
            UserSession session = sessionOpt.get();
            session.setIsActive(false);
            userSessionRepository.save(session);
            log.info("Invalidated session: {}", sessionToken);
        }
    }
    
    /**
     * Clean up expired sessions for a user
     */
    @Transactional
    public void cleanupExpiredSessions(String userId) {
        List<UserSession> expiredSessions = userSessionRepository.findExpiredSessions(LocalDateTime.now());
        for (UserSession session : expiredSessions) {
            if (session.getUserId().equals(userId)) {
                session.setIsActive(false);
                userSessionRepository.save(session);
            }
        }
        log.debug("Cleaned up expired sessions for user: {}", userId);
    }
    
    /**
     * Get session by token
     */
    public Optional<UserSession> getSessionByToken(String sessionToken) {
        return userSessionRepository.findBySessionToken(sessionToken);
    }
    
    /**
     * Check if user has active session
     */
    public boolean hasActiveSession(String userId) {
        List<UserSession> activeSessions = getActiveSessions(userId);
        return !activeSessions.isEmpty();
    }
}
