package com.vijay.repository;

import com.vijay.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    
    Optional<Conversation> findByConversationId(String conversationId);
    
    List<Conversation> findByUserIdOrderByUpdatedAtDesc(String userId);
    
    List<Conversation> findByUserIdAndIsActiveTrueOrderByUpdatedAtDesc(String userId);
    
    @Query("SELECT c FROM Conversation c WHERE c.userId = :userId AND c.provider = :provider ORDER BY c.updatedAt DESC")
    List<Conversation> findByUserIdAndProvider(@Param("userId") String userId, @Param("provider") String provider);
    
    @Query("SELECT c FROM Conversation c WHERE c.userId = :userId AND c.createdAt >= :since ORDER BY c.createdAt DESC")
    List<Conversation> findByUserIdAndCreatedAtAfter(@Param("userId") String userId, @Param("since") LocalDateTime since);
    
    @Query("SELECT COUNT(c) FROM Conversation c WHERE c.userId = :userId AND c.isActive = true")
    Long countActiveConversationsByUserId(@Param("userId") String userId);
    
    void deleteByUserIdAndIsActiveFalse(String userId);
}
