package com.vijay.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Minimal configuration for testing basic functionality
 */
@Configuration
@Profile("minimal")
public class MinimalConfig {
    
    // This configuration will only be loaded when the 'minimal' profile is active
    // It provides a basic setup without MCP features
    
}
