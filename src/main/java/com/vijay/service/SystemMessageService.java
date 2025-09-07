package com.vijay.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
public class SystemMessageService {

    private String cachedSystemMessage;

    /**
     * Get the system message from tool-only.st file
     */
    public String getSystemMessage() {
        if (cachedSystemMessage == null) {
            loadSystemMessage();
        }
        return cachedSystemMessage;
    }

    private void loadSystemMessage() {
        try {
            ClassPathResource resource = new ClassPathResource("prompts/tool-only.st");
            cachedSystemMessage = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            log.info("Successfully loaded system message from tool-only.st: {} characters", cachedSystemMessage.length());
        } catch (Exception e) {
            log.warn("Could not load system message, using fallback: {}", e.getMessage());
            cachedSystemMessage = "You are a specialized AI coding assistant with access to MCP tools: listFaqs and createNote. Only respond to coding and programming questions.";
        }
    }
}
