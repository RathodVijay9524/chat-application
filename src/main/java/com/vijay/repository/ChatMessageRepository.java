package com.vijay.repository;

import com.vijay.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    
    List<ChatMessage> findByConversationIdOrderByTimestampAsc(String conversationId);
    
    List<ChatMessage> findByUserIdOrderByTimestampDesc(String userId);
    
    @Query("SELECT m FROM ChatMessage m WHERE m.userId = :userId AND m.provider = :provider ORDER BY m.timestamp DESC")
    List<ChatMessage> findByUserIdAndProvider(@Param("userId") String userId, @Param("provider") String provider);
    
    @Query("SELECT m FROM ChatMessage m WHERE m.userId = :userId AND m.timestamp >= :since ORDER BY m.timestamp DESC")
    List<ChatMessage> findByUserIdAndTimestampAfter(@Param("userId") String userId, @Param("since") LocalDateTime since);
    
    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.userId = :userId AND m.isSuccessful = true")
    Long countSuccessfulMessagesByUserId(@Param("userId") String userId);
    
    @Query("SELECT SUM(m.tokensUsed) FROM ChatMessage m WHERE m.userId = :userId AND m.isSuccessful = true")
    Long sumTokensUsedByUserId(@Param("userId") String userId);
    
    @Query("SELECT m FROM ChatMessage m WHERE m.conversationId = :conversationId AND m.isSuccessful = true ORDER BY m.timestamp ASC")
    List<ChatMessage> findSuccessfulMessagesByConversationId(@Param("conversationId") String conversationId);
}
