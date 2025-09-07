package com.vijay.service;

import com.vijay.provider.AIProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIProviderFactory {
    
    private final List<AIProvider> providers;
    private Map<String, AIProvider> providerMap;
    
    @PostConstruct
    public void init() {
        providerMap = providers.stream()
                .collect(Collectors.toMap(
                        AIProvider::getProviderName,
                        Function.identity()
                ));
    }
    
    public AIProvider getProvider(String providerName) {
        if (providerMap == null) {
            init();
        }
        log.info("Looking for provider: '{}', available providers: {}", providerName, providerMap.keySet());
        return providerMap.get(providerName.toLowerCase());
    }
    
    public List<AIProvider> getAllProviders() {
        return providers;
    }
    
    public List<String> getAvailableProviderNames() {
        return providers.stream()
                .map(AIProvider::getProviderName)
                .collect(Collectors.toList());
    }
}
