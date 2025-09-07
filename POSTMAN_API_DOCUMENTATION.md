# 🤖 Multi-Modal Chat Application - Postman API Documentation

## 📋 **Overview**
This document provides comprehensive Postman API documentation for the Multi-Modal Chat Application built with Spring AI 1.0.1. The application supports multiple AI providers including OpenAI, Anthropic Claude, Google Gemini, Ollama, OpenRouter AI, Groq, and Hugging Face.

## 🌐 **Base URL**
```
http://localhost:8080
```

## 🔑 **Authentication**
- **Type**: API Key (via Environment Variables)
- **Required Keys**:
  - `OPENAI_API_KEY`
  - `CLAUDAI_API_KEY` 
  - `GEMINI_API_KEY`
  - `OPENROUTER_API_KEY`
  - `GROQ_API_KEY`
  - `HUGGINGFACE_API_KEY`

---

## 📚 **API Endpoints**

### 1. **Get Available AI Providers**
**Endpoint**: `GET /api/chat/providers`

**Description**: Retrieves all available AI providers and their information.

**Request**:
```http
GET http://localhost:8080/api/chat/providers
Content-Type: application/json
```

**Response**:
```json
[
  {
    "name": "openai",
    "displayName": "OpenAI GPT",
    "description": "OpenAI GPT models",
    "availableModels": [
      "gpt-4o",
      "gpt-4o-mini",
      "gpt-4-turbo",
      "gpt-3.5-turbo"
    ],
    "isAvailable": true
  },
  {
    "name": "claude",
    "displayName": "Anthropic Claude",
    "description": "Anthropic Claude models",
    "availableModels": [
      "claude-3-sonnet-20240229",
      "claude-3-opus-20240229",
      "claude-3-haiku-20240307",
      "claude-3-5-sonnet-20241022"
    ],
    "isAvailable": true
  },
  {
    "name": "gemini",
    "displayName": "Google Gemini",
    "description": "Google Gemini models",
    "availableModels": [
      "gemini-1.5-pro",
      "gemini-1.5-flash",
      "gemini-1.0-pro"
    ],
    "isAvailable": true
  },
  {
    "name": "ollama",
    "displayName": "Ollama Local",
    "description": "Local Ollama models",
    "availableModels": [
      "qwen2.5-coder:7b",
      "llama3.1:8b",
      "mistral:7b",
      "codellama:7b"
    ],
    "isAvailable": true
  },
  {
    "name": "openrouter",
    "displayName": "OpenRouter AI",
    "description": "OpenRouter AI models",
    "availableModels": [
      "anthropic/claude-3.5-sonnet",
      "openai/gpt-4o",
      "meta-llama/llama-3.1-8b-instruct",
      "google/gemini-pro-1.5"
    ],
    "isAvailable": true
  },
  {
    "name": "groq",
    "displayName": "Groq AI",
    "description": "Groq AI models",
    "availableModels": [
      "llama3-70b-8192",
      "llama3-8b-8192",
      "mixtral-8x7b-32768",
      "gemma-7b-it"
    ],
    "isAvailable": true
  },
  {
    "name": "huggingface",
    "displayName": "Hugging Face",
    "description": "Hugging Face models",
    "availableModels": [
      "microsoft/DialoGPT-medium",
      "microsoft/DialoGPT-small",
      "facebook/blenderbot-400M",
      "facebook/blenderbot-90M"
    ],
    "isAvailable": true
  }
]
```

---

### 2. **Send Chat Message**
**Endpoint**: `POST /api/chat/message`

**Description**: Send a message to any available AI provider and get a response.

**Request Body**:
```json
{
  "message": "Hello, how are you?",
  "provider": "openai",
  "model": "gpt-4o",
  "conversationId": "conv-123"
}
```

**Request Parameters**:
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `message` | String | Yes | The user's message |
| `provider` | String | Yes | AI provider name (openai, claude, gemini, ollama, openrouter, groq, huggingface) |
| `model` | String | No | Specific model to use (optional, uses provider default) |
| `conversationId` | String | No | Conversation ID for context (optional) |

**Response**:
```json
{
  "response": "Hello! I'm doing well, thank you for asking. How can I help you today?",
  "provider": "openai",
  "model": "gpt-4o",
  "conversationId": "conv-123",
  "timestamp": "2025-09-05T00:30:00",
  "tokensUsed": 25,
  "responseTimeMs": 1250,
  "error": null
}
```

**Error Response**:
```json
{
  "response": "Sorry, I encountered an error while processing your request.",
  "provider": "openai",
  "model": "gpt-4o",
  "conversationId": "conv-123",
  "timestamp": "2025-09-05T00:30:00",
  "tokensUsed": 0,
  "responseTimeMs": 500,
  "error": "401 Unauthorized - Invalid API key"
}
```

---

### 3. **Get MCP Tools**
**Endpoint**: `GET /api/chat/tools`

**Description**: Retrieves available MCP (Model Context Protocol) tools.

**Request**:
```http
GET http://localhost:8080/api/chat/tools
Content-Type: application/json
```

**Response**:
```json
{
  "tools": [
    {
      "name": "file_operations",
      "description": "File system operations",
      "parameters": {
        "type": "object",
        "properties": {
          "operation": {
            "type": "string",
            "enum": ["read", "write", "list", "delete"]
          },
          "path": {
            "type": "string",
            "description": "File or directory path"
          }
        }
      }
    },
    {
      "name": "web_search",
      "description": "Search the web for information",
      "parameters": {
        "type": "object",
        "properties": {
          "query": {
            "type": "string",
            "description": "Search query"
          }
        }
      }
    }
  ],
  "serverInfo": {
    "name": "mcp-server",
    "version": "1.0.0",
    "protocol": "2024-11-05"
  }
}
```

---

### 4. **Get RAG Information**
**Endpoint**: `GET /api/chat/rag`

**Description**: Retrieves RAG (Retrieval-Augmented Generation) configuration and status.

**Request**:
```http
GET http://localhost:8080/api/chat/rag
Content-Type: application/json
```

**Response**:
```json
{
  "enabled": true,
  "vectorStore": "in-memory",
  "embeddingModel": "text-embedding-ada-002",
  "chunkSize": 1000,
  "chunkOverlap": 200,
  "documentsCount": 0,
  "status": "ready"
}
```

---

## 🧪 **Postman Collection Examples**

### **Collection 1: Basic Chat Tests**

#### **Test 1: OpenAI Chat**
```http
POST http://localhost:8080/api/chat/message
Content-Type: application/json

{
  "message": "What is artificial intelligence?",
  "provider": "openai",
  "model": "gpt-4o",
  "conversationId": "test-openai-001"
}
```

#### **Test 2: Claude Chat**
```http
POST http://localhost:8080/api/chat/message
Content-Type: application/json

{
  "message": "Explain quantum computing in simple terms",
  "provider": "claude",
  "model": "claude-3-sonnet-20240229",
  "conversationId": "test-claude-001"
}
```

#### **Test 3: Gemini Chat**
```http
POST http://localhost:8080/api/chat/message
Content-Type: application/json

{
  "message": "Write a Python function to calculate fibonacci numbers",
  "provider": "gemini",
  "model": "gemini-1.5-pro",
  "conversationId": "test-gemini-001"
}
```

#### **Test 4: Ollama Local Chat**
```http
POST http://localhost:8080/api/chat/message
Content-Type: application/json

{
  "message": "Hello from Ollama!",
  "provider": "ollama",
  "model": "qwen2.5-coder:7b",
  "conversationId": "test-ollama-001"
}
```

#### **Test 5: OpenRouter Chat**
```http
POST http://localhost:8080/api/chat/message
Content-Type: application/json

{
  "message": "What are the benefits of using OpenRouter?",
  "provider": "openrouter",
  "model": "anthropic/claude-3.5-sonnet",
  "conversationId": "test-openrouter-001"
}
```

#### **Test 6: Groq Chat**
```http
POST http://localhost:8080/api/chat/message
Content-Type: application/json

{
  "message": "Tell me about Groq's inference speed",
  "provider": "groq",
  "model": "llama3-70b-8192",
  "conversationId": "test-groq-001"
}
```

#### **Test 7: Hugging Face Chat**
```http
POST http://localhost:8080/api/chat/message
Content-Type: application/json

{
  "message": "Hello from Hugging Face!",
  "provider": "huggingface",
  "model": "microsoft/DialoGPT-medium",
  "conversationId": "test-huggingface-001"
}
```

---

### **Collection 2: Provider Information Tests**

#### **Test 1: Get All Providers**
```http
GET http://localhost:8080/api/chat/providers
Content-Type: application/json
```

#### **Test 2: Get MCP Tools**
```http
GET http://localhost:8080/api/chat/tools
Content-Type: application/json
```

#### **Test 3: Get RAG Status**
```http
GET http://localhost:8080/api/chat/rag
Content-Type: application/json
```

---

### **Collection 3: Error Handling Tests**

#### **Test 1: Invalid Provider**
```http
POST http://localhost:8080/api/chat/message
Content-Type: application/json

{
  "message": "Test message",
  "provider": "invalid-provider",
  "conversationId": "test-error-001"
}
```

#### **Test 2: Missing Message**
```http
POST http://localhost:8080/api/chat/message
Content-Type: application/json

{
  "provider": "openai",
  "conversationId": "test-error-002"
}
```

#### **Test 3: Invalid Model**
```http
POST http://localhost:8080/api/chat/message
Content-Type: application/json

{
  "message": "Test message",
  "provider": "openai",
  "model": "invalid-model",
  "conversationId": "test-error-003"
}
```

---

## 🔧 **Postman Environment Variables**

Create a Postman environment with the following variables:

| Variable | Value | Description |
|----------|-------|-------------|
| `base_url` | `http://localhost:8080` | Base URL for the API |
| `openai_api_key` | `your-openai-key` | OpenAI API key |
| `claude_api_key` | `your-claude-key` | Anthropic Claude API key |
| `gemini_api_key` | `your-gemini-key` | Google Gemini API key |
| `openrouter_api_key` | `your-openrouter-key` | OpenRouter API key |
| `groq_api_key` | `your-groq-key` | Groq API key |
| `huggingface_api_key` | `your-huggingface-key` | Hugging Face API key |

---

## 📊 **Response Status Codes**

| Code | Description |
|------|-------------|
| `200` | Success |
| `400` | Bad Request - Invalid input |
| `401` | Unauthorized - Invalid API key |
| `404` | Not Found - Provider or endpoint not found |
| `500` | Internal Server Error - Server error |

---

## 🚀 **Quick Start Guide**

1. **Start the Application**:
   ```bash
   # Set environment variables
   export OPENAI_API_KEY="your-key"
   export CLAUDAI_API_KEY="your-key"
   # ... other keys
   
   # Run the application
   ./mvnw spring-boot:run
   ```

2. **Test with Postman**:
   - Import the collection
   - Set up environment variables
   - Start with "Get All Providers" to verify the application is running
   - Test individual providers with sample messages

3. **Monitor Logs**:
   - Check application logs for detailed information
   - Monitor response times and token usage
   - Debug any API key issues

---

## 🔍 **Troubleshooting**

### **Common Issues**:

1. **Port 8080 already in use**:
   - Kill existing processes: `netstat -ano | findstr :8080`
   - Change port in `application.properties`

2. **API Key errors**:
   - Verify environment variables are set
   - Check API key validity
   - Ensure proper key format

3. **Provider not available**:
   - Check if provider is enabled
   - Verify API key is set
   - Check provider-specific configuration

4. **Model not found**:
   - Use provider's default model
   - Check available models via `/api/chat/providers`
   - Verify model name spelling

---

## 📈 **Performance Monitoring**

The API provides the following performance metrics in responses:
- `responseTimeMs`: Time taken to generate response
- `tokensUsed`: Number of tokens consumed
- `timestamp`: When the response was generated

Monitor these metrics to:
- Track response times across providers
- Monitor token usage and costs
- Identify performance bottlenecks

---

## 🎯 **Best Practices**

1. **Use appropriate models** for your use case
2. **Set conversation IDs** for context continuity
3. **Monitor token usage** to control costs
4. **Handle errors gracefully** in your client applications
5. **Use environment variables** for API keys
6. **Test with different providers** to find the best fit

---

## 📞 **Support**

For issues or questions:
1. Check the application logs
2. Verify API key configuration
3. Test with the provided Postman collection
4. Review the troubleshooting section

---

*This documentation covers all available endpoints and provides comprehensive examples for testing the Multi-Modal Chat Application.*

