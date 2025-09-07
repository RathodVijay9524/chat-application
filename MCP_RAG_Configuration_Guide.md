# 🚀 Complete MCP + RAG Integration Guide for Spring AI

## Overview
This guide shows how to integrate **Model Context Protocol (MCP)** and **Retrieval-Augmented Generation (RAG)** with **ALL** AI providers (OpenAI, Claude, Gemini, Ollama) using Spring AI.

## 🔧 Configuration

### 1. Dependencies (pom.xml)
```xml
<!-- MCP Server Support -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-mcp-spring-boot-starter</artifactId>
    <version>1.0.0-M4</version>
</dependency>

<!-- RAG Support - Vector Store -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-pgvector-store-spring-boot-starter</artifactId>
    <version>1.0.0-M4</version>
</dependency>

<!-- Document Processing for RAG -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-pdf-document-reader</artifactId>
    <version>1.0.0-M4</version>
</dependency>
```

### 2. Application Properties
```properties
# MCP Server Configuration
spring.ai.mcp.sse.connections.my-mcp-server.url=http://localhost:8081
spring.ai.mcp.sse.connections.my-mcp-server.enabled=true
spring.ai.mcp.sse.connections.local-mcp.url=http://localhost:8082
spring.ai.mcp.sse.connections.local-mcp.enabled=false

# RAG Configuration - Vector Store
spring.ai.vectorstore.pgvector.index-type=HNSW
spring.ai.vectorstore.pgvector.distance-type=COSINE_DISTANCE
spring.ai.vectorstore.pgvector.dimensions=1536
spring.ai.vectorstore.pgvector.url=jdbc:postgresql://localhost:5432/vectordb
spring.ai.vectorstore.pgvector.username=postgres
spring.ai.vectorstore.pgvector.password=password

# RAG Configuration - Embeddings
spring.ai.openai.embedding.options.model=text-embedding-3-small
spring.ai.openai.embedding.options.dimensions=1536

# All AI Providers with API Keys
spring.ai.openai.api-key=${OPENAI_API_KEY}
spring.ai.anthropic.api-key=${CLAUDE_API_KEY}
spring.ai.vertex.ai.project-id=${GEMINI_PROJECT_ID}
spring.ai.vertex.ai.location=us-central1
spring.ai.ollama.base-url=http://localhost:11434
```

## 🏗️ Architecture

### MCP Integration
- **MCP Service**: Handles connections to external MCP servers
- **All Providers**: Can send responses to MCP servers for additional processing
- **Real-time**: Uses Server-Sent Events (SSE) for real-time communication

### RAG Integration
- **Vector Store**: PostgreSQL with pgvector extension
- **Embeddings**: OpenAI text-embedding-3-small
- **Document Processing**: PDF document reader
- **Context Enhancement**: All providers get RAG-enhanced context

## 🔄 How It Works

### 1. User sends message
```
User: "What is machine learning?"
```

### 2. RAG Context Generation
```java
String ragContext = ragService.generateRAGContext(request.getMessage());
// Searches vector store for relevant documents
// Returns: "Relevant Context: [1] Machine learning is... [2] AI algorithms..."
```

### 3. Enhanced Prompt Building
```java
String enhancedPrompt = buildEnhancedPrompt(userMessage, ragContext);
// Result: "Relevant Context: [1] Machine learning is... [2] AI algorithms...
// 
// User Question: What is machine learning?"
```

### 4. AI Provider Processing
```java
String response = chatClient.prompt()
    .user(enhancedPrompt)
    .call()
    .content();
// All providers (OpenAI, Claude, Gemini, Ollama) use the same enhanced prompt
```

### 5. MCP Server Integration
```java
if (mcpService.isMCPServerAvailable("my-mcp-server")) {
    Flux<String> mcpResponse = mcpService.sendToMCPServer("my-mcp-server", response);
    // Optional: Process MCP response for additional context
}
```

## 🎯 Benefits

### For All AI Providers:
1. **Enhanced Context**: RAG provides relevant document context
2. **MCP Integration**: Can leverage external tools and services
3. **Consistent Experience**: Same RAG + MCP features across all providers
4. **Spring AI Native**: Uses Spring AI's ChatClient for all providers

### Provider-Specific Features:
- **OpenAI**: GPT models with RAG + MCP
- **Claude**: Anthropic models with RAG + MCP  
- **Gemini**: Google models with RAG + MCP
- **Ollama**: Local models with RAG + MCP

## 🚀 Testing

### Test RAG + MCP with All Providers:
```bash
# OpenAI with RAG + MCP
curl -X POST http://localhost:8080/api/chat/message \
  -H "Content-Type: application/json" \
  -d '{"message": "Explain quantum computing", "provider": "openai"}'

# Claude with RAG + MCP
curl -X POST http://localhost:8080/api/chat/message \
  -H "Content-Type: application/json" \
  -d '{"message": "What are neural networks?", "provider": "claude"}'

# Gemini with RAG + MCP
curl -X POST http://localhost:8080/api/chat/message \
  -H "Content-Type: application/json" \
  -d '{"message": "How does deep learning work?", "provider": "gemini"}'

# Ollama with RAG + MCP
curl -X POST http://localhost:8080/api/chat/message \
  -H "Content-Type: application/json" \
  -d '{"message": "Write a Python function", "provider": "ollama"}'
```

## 📊 Monitoring

### Check MCP Server Status:
```bash
curl http://localhost:8080/api/mcp/status
```

### Check RAG Status:
```bash
curl http://localhost:8080/api/rag/status
```

## 🔧 Setup Requirements

### 1. MCP Server
- Start your MCP server on `http://localhost:8081`
- Ensure SSE endpoint is available

### 2. PostgreSQL with pgvector
```sql
CREATE DATABASE vectordb;
CREATE EXTENSION vector;
```

### 3. API Keys
- Set environment variables for all providers
- OpenAI, Claude, Gemini API keys

## 🎉 Result

All AI providers now have:
- ✅ **RAG Enhancement**: Context from vector store
- ✅ **MCP Integration**: External tool connectivity  
- ✅ **Spring AI ChatClient**: Native Spring AI integration
- ✅ **Consistent API**: Same interface for all providers
- ✅ **Real-time Processing**: SSE for MCP communication

This creates a powerful, unified AI platform where all providers benefit from RAG and MCP capabilities!
