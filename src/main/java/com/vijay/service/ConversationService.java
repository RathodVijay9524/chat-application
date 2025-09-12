package com.vijay.service;

import com.vijay.dto.ChatRequest;
import com.vijay.dto.ChatResponse;
import com.vijay.entity.ChatMessage;
import com.vijay.entity.Conversation;
import com.vijay.repository.ChatMessageRepository;
import com.vijay.repository.ConversationRepository;
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
public class ConversationService {
    
    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    
    /**
     * Create or get existing conversation
     */
    @Transactional
    public Conversation getOrCreateConversation(ChatRequest request) {
        String conversationId = request.getConversationId();
        String userId = request.getUserId();
        
        if (conversationId == null || conversationId.trim().isEmpty()) {
            conversationId = UUID.randomUUID().toString();
            log.info("Generated new conversation ID: {} for user: {}", conversationId, userId);
        }
        
        Optional<Conversation> existingConversation = conversationRepository.findByConversationId(conversationId);
        
        if (existingConversation.isPresent()) {
            Conversation conversation = existingConversation.get();
            // Update conversation metadata
            conversation.setUpdatedAt(LocalDateTime.now());
            conversation.setProvider(request.getProvider());
            conversation.setModel(request.getModel());
            conversation.setTemperature(request.getTemperature());
            conversation.setMaxTokens(request.getMaxTokens());
            conversationRepository.save(conversation);
            
            log.debug("Updated existing conversation: {} for user: {}", conversationId, userId);
            return conversation;
        } else {
            // Create new conversation
            Conversation newConversation = Conversation.builder()
                    .conversationId(conversationId)
                    .userId(userId)
                    .provider(request.getProvider())
                    .model(request.getModel())
                    .temperature(request.getTemperature())
                    .maxTokens(request.getMaxTokens())
                    .title(generateConversationTitle(request.getMessage()))
                    .isActive(true)
                    .totalMessages(0L)
                    .totalTokensUsed(0L)
                    .build();
            
            Conversation savedConversation = conversationRepository.save(newConversation);
            log.info("Created new conversation: {} for user: {}", conversationId, userId);
            return savedConversation;
        }
    }
    
    /**
     * Save chat message to database
     */
    @Transactional
    public ChatMessage saveChatMessage(ChatRequest request, ChatResponse response) {
        ChatMessage chatMessage = ChatMessage.builder()
                .conversationId(request.getConversationId())
                .userId(request.getUserId())
                .userMessage(request.getMessage())
                .aiResponse(response.getResponse())
                .provider(request.getProvider())
                .model(request.getModel())
                .tokensUsed(response.getTokensUsed())
                .responseTimeMs(response.getResponseTimeMs())
                .error(response.getError())
                .isSuccessful(response.getError() == null)
                .temperature(request.getTemperature())
                .maxTokens(request.getMaxTokens())
                .build();
        
        ChatMessage savedMessage = chatMessageRepository.save(chatMessage);
        
        // Update conversation statistics
        updateConversationStats(request.getConversationId());
        
        log.debug("Saved chat message for conversation: {} user: {}", request.getConversationId(), request.getUserId());
        return savedMessage;
    }
    
    /**
     * Get conversation history for a user
     */
    public List<Conversation> getUserConversations(String userId) {
        return conversationRepository.findByUserIdAndIsActiveTrueOrderByUpdatedAtDesc(userId);
    }
    
    /**
     * Get conversation messages
     */
    public List<ChatMessage> getConversationMessages(String conversationId) {
        return chatMessageRepository.findSuccessfulMessagesByConversationId(conversationId);
    }
    
    /**
     * Get user's recent conversations
     */
    public List<Conversation> getUserRecentConversations(String userId, int limit) {
        List<Conversation> conversations = conversationRepository.findByUserIdAndIsActiveTrueOrderByUpdatedAtDesc(userId);
        return conversations.stream().limit(limit).toList();
    }
    
    /**
     * Update conversation statistics
     */
    @Transactional
    public void updateConversationStats(String conversationId) {
        Optional<Conversation> conversationOpt = conversationRepository.findByConversationId(conversationId);
        if (conversationOpt.isPresent()) {
            Conversation conversation = conversationOpt.get();
            
            // Count total messages
            Long totalMessages = chatMessageRepository.countSuccessfulMessagesByUserId(conversation.getUserId());
            conversation.setTotalMessages(totalMessages);
            
            // Sum total tokens used
            Long totalTokens = chatMessageRepository.sumTokensUsedByUserId(conversation.getUserId());
            conversation.setTotalTokensUsed(totalTokens);
            
            conversationRepository.save(conversation);
        }
    }
    
    /**
     * Generate conversation title from first message
     */
    private String generateConversationTitle(String message) {
        if (message == null || message.trim().isEmpty()) {
            return "New Conversation";
        }
        
        // Take first 50 characters and add ellipsis if longer
        String title = message.trim();
        if (title.length() > 50) {
            title = title.substring(0, 47) + "...";
        }
        
        return title;
    }
    
    /**
     * Archive a conversation
     */
    @Transactional
    public void archiveConversation(String conversationId, String userId) {
        Optional<Conversation> conversationOpt = conversationRepository.findByConversationId(conversationId);
        if (conversationOpt.isPresent() && conversationOpt.get().getUserId().equals(userId)) {
            Conversation conversation = conversationOpt.get();
            conversation.setIsActive(false);
            conversationRepository.save(conversation);
            log.info("Archived conversation: {} for user: {}", conversationId, userId);
        }
    }
    
    /**
     * Get user statistics
     */
    public UserStats getUserStats(String userId) {
        Long totalMessages = chatMessageRepository.countSuccessfulMessagesByUserId(userId);
        Long totalTokens = chatMessageRepository.sumTokensUsedByUserId(userId);
        Long activeConversations = conversationRepository.countActiveConversationsByUserId(userId);
        
        return new UserStats(totalMessages, totalTokens, activeConversations);
    }
    
    /**
     * User statistics record
     */
    public record UserStats(Long totalMessages, Long totalTokens, Long activeConversations) {}
}
