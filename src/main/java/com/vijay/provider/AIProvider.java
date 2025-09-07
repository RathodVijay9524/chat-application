package com.vijay.provider;

import com.vijay.dto.ChatRequest;
import com.vijay.dto.ChatResponse;
import com.vijay.dto.ProviderInfo;

import java.util.List;

public interface AIProvider {
    String getProviderName();
    ProviderInfo getProviderInfo();
    ChatResponse generateResponse(ChatRequest request);
    List<String> getAvailableModels();
    boolean isAvailable();
}
