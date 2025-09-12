package com.vijay.repository;

import com.vijay.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {
    
    Optional<UserSession> findBySessionToken(String sessionToken);
    
    List<UserSession> findByUserIdAndIsActiveTrue(String userId);
    
    @Query("SELECT s FROM UserSession s WHERE s.userId = :userId AND s.isActive = true AND s.expiresAt > :now")
    List<UserSession> findActiveSessionsByUserId(@Param("userId") String userId, @Param("now") LocalDateTime now);
    
    @Query("SELECT s FROM UserSession s WHERE s.isActive = true AND s.expiresAt < :now")
    List<UserSession> findExpiredSessions(@Param("now") LocalDateTime now);
    
    void deleteBySessionToken(String sessionToken);
    
    void deleteByUserIdAndIsActiveFalse(String userId);
}
