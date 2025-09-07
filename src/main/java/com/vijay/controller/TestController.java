package com.vijay.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test")
public class TestController {
    
    @GetMapping("/hello")
    public String hello() {
        return "Hello! Chat application is working!";
    }
    
    @GetMapping("/providers")
    public String[] getProviders() {
        return new String[]{"openai", "claude", "gemini", "ollama"};
    }
}
