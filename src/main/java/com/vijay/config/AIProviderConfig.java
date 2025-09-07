package com.vijay.config;

import io.modelcontextprotocol.client.McpSyncClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import com.vijay.service.SystemMessageService;
import com.vijay.service.DynamicMcpServerService;
// import org.springframework.ai.huggingface.HuggingFaceChatModel; // Not available in Spring AI 1.0.1
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.reactive.function.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Configuration
public class AIProviderConfig {

    private static final Logger logger = LoggerFactory.getLogger(AIProviderConfig.class);

    // WebClient.Builder bean for HTTP clients
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

    // Merge all MCP servers (static + dynamic)
    @Bean
    @Primary
    public ToolCallbackProvider mcpToolCallbackProvider(@Autowired(required = false) List<McpSyncClient> mcpSyncClients,
                                                       DynamicMcpServerService dynamicMcpServerService) {
        // Initialize dynamic service with static clients
        dynamicMcpServerService.setStaticClients(mcpSyncClients);
        
        // Get the combined tool callback provider from dynamic service
        ToolCallbackProvider provider = dynamicMcpServerService.getToolCallbackProvider();
        
        if (provider == null) {
            logger.info("No MCP clients available, creating empty tool callback provider");
            return new SyncMcpToolCallbackProvider(List.of());
        }
        
        logger.info("Created MCP Tool Callback Provider with dynamic server management");
        return provider;
    }

    // Chat Memory for conversation context
    @Bean
    ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .maxMessages(20)
                .build();
    }

    // System message service for all providers
    @Bean
    SystemMessageService systemMessageService() {
        return new SystemMessageService();
    }

    // OpenAI client with MCP tools
    @Bean(name = "openAiChatClient")
    ChatClient openAiChatClient(OpenAiChatModel openAiChatModel,
                               ToolCallbackProvider mcp, ChatMemory chatMemory) {
        logger.info("Creating OpenAI Chat Client with MCP tools");
        return ChatClient.builder(openAiChatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultToolCallbacks(mcp.getToolCallbacks())
                .build();
    }

    // Anthropic Claude client with MCP tools
    @Bean(name = "anthropicChatClient")
    ChatClient anthropicChatClient(AnthropicChatModel anthropicChatModel,
                                  ToolCallbackProvider mcp, ChatMemory chatMemory) {
        logger.info("Creating Anthropic Chat Client with MCP tools");
        return ChatClient.builder(anthropicChatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultToolCallbacks(mcp.getToolCallbacks())
                .build();
    }

    // Custom providers with MCP tools - using WebClient for API calls but ChatClient for MCP tools
    
    // Groq client with MCP tools (for tool access only, API calls use WebClient)
    @Bean(name = "groqChatClient")
    ChatClient groqChatClient(OpenAiChatModel openAiChatModel, ToolCallbackProvider mcp, ChatMemory chatMemory) {
        logger.info("Creating Groq Chat Client with MCP tools");
        // Create a dummy model for tool access - actual API calls use WebClient
        // We'll use OpenAI model as a placeholder since we need a ChatModel
        return ChatClient.builder(openAiChatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultToolCallbacks(mcp.getToolCallbacks())
                .build();
    }
    
    // Gemini client with MCP tools (for tool access only, API calls use WebClient)
    @Bean(name = "geminiChatClient")
    ChatClient geminiChatClient(OpenAiChatModel openAiChatModel, ToolCallbackProvider mcp, ChatMemory chatMemory) {
        logger.info("Creating Gemini Chat Client with MCP tools");
        // Create a dummy model for tool access - actual API calls use WebClient
        // We'll use OpenAI model as a placeholder since we need a ChatModel
        return ChatClient.builder(openAiChatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultToolCallbacks(mcp.getToolCallbacks())
                .build();
    }
    
    // OpenRouter client with MCP tools (for tool access only, API calls use WebClient)
    @Bean(name = "openRouterChatClient")
    ChatClient openRouterChatClient(OpenAiChatModel openAiChatModel, ToolCallbackProvider mcp, ChatMemory chatMemory) {
        logger.info("Creating OpenRouter Chat Client with MCP tools");
        // Create a dummy model for tool access - actual API calls use WebClient
        // We'll use OpenAI model as a placeholder since we need a ChatModel
        return ChatClient.builder(openAiChatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultToolCallbacks(mcp.getToolCallbacks())
                .build();
    }

    // Ollama client with MCP tools
    @Bean(name = "ollamaChatClient")
    ChatClient ollamaChatClient(OllamaChatModel ollamaChatModel,
                               ToolCallbackProvider mcp, ChatMemory chatMemory) {
        logger.info("Creating Ollama Chat Client with MCP tools");
        return ChatClient.builder(ollamaChatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultToolCallbacks(mcp.getToolCallbacks())
                .build();
    }

    // Hugging Face client with MCP tools (for tool access only, API calls use WebClient)
    @Bean(name = "huggingFaceChatClient")
    ChatClient huggingFaceChatClient(OpenAiChatModel openAiChatModel, ToolCallbackProvider mcp, ChatMemory chatMemory) {
        logger.info("Creating HuggingFace Chat Client with MCP tools");
        // Create a dummy model for tool access - actual API calls use WebClient
        // We'll use OpenAI model as a placeholder since we need a ChatModel
        return ChatClient.builder(openAiChatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultToolCallbacks(mcp.getToolCallbacks())
                .build();
    }
}
