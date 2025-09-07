# 🚀 Postman Setup Guide for Multi-Modal Chat Application

## 📥 **Import Instructions**

### **Step 1: Import Collection**
1. Open Postman
2. Click **Import** button
3. Select `Multi-Modal_Chat_App.postman_collection.json`
4. Click **Import**

### **Step 2: Import Environment**
1. Click **Import** button again
2. Select `Multi-Modal_Chat_App.postman_environment.json`
3. Click **Import**

### **Step 3: Set Up Environment Variables**
1. Click on **Environments** tab
2. Select **Multi-Modal Chat App Environment**
3. Update the API keys with your actual values:
   - `openai_api_key`: Your OpenAI API key
   - `claude_api_key`: Your Anthropic Claude API key
   - `gemini_api_key`: Your Google Gemini API key
   - `openrouter_api_key`: Your OpenRouter API key
   - `groq_api_key`: Your Groq API key
   - `huggingface_api_key`: Your Hugging Face API key

## 🎯 **Quick Start Testing**

### **1. Start Your Application**
```bash
# Set environment variables
export OPENAI_API_KEY="your-key"
export CLAUDAI_API_KEY="your-key"
export GEMINI_API_KEY="your-key"
export OPENROUTER_API_KEY="your-key"
export GROQ_API_KEY="your-key"
export HUGGINGFACE_API_KEY="your-key"

# Run the application
./mvnw spring-boot:run
```

### **2. Test Application Health**
1. Select **Provider Information** folder
2. Run **Get All Providers** request
3. Verify you get a list of available providers

### **3. Test Chat Functionality**
1. Select **Chat Messages** folder
2. Run **OpenAI Chat** request
3. Verify you get a response from OpenAI

## 📊 **Collection Structure**

```
Multi-Modal Chat Application API/
├── Provider Information/
│   ├── Get All Providers
│   ├── Get MCP Tools
│   └── Get RAG Status
├── Chat Messages/
│   ├── OpenAI Chat
│   ├── Claude Chat
│   ├── Gemini Chat
│   ├── Ollama Chat
│   ├── OpenRouter Chat
│   ├── Groq Chat
│   └── Hugging Face Chat
├── Error Handling Tests/
│   ├── Invalid Provider
│   ├── Missing Message
│   └── Invalid Model
└── Advanced Tests/
    ├── Code Generation Test
    ├── Multi-turn Conversation
    ├── Follow-up Question
    └── Performance Test
```

## 🔧 **Environment Variables**

| Variable | Description | Example |
|----------|-------------|---------|
| `base_url` | API base URL | `http://localhost:8080` |
| `conversation_id` | Auto-generated conversation ID | `test-conv-{{$timestamp}}` |
| `openai_api_key` | OpenAI API key | `sk-...` |
| `claude_api_key` | Anthropic Claude API key | `sk-ant-...` |
| `gemini_api_key` | Google Gemini API key | `AI...` |
| `openrouter_api_key` | OpenRouter API key | `sk-or-...` |
| `groq_api_key` | Groq API key | `gsk_...` |
| `huggingface_api_key` | Hugging Face API key | `hf_...` |

## 🧪 **Testing Workflow**

### **Basic Testing**
1. **Health Check**: Run "Get All Providers" to verify app is running
2. **Provider Test**: Test each provider individually
3. **Error Handling**: Test error scenarios

### **Advanced Testing**
1. **Performance**: Use "Performance Test" to test with long prompts
2. **Conversation**: Use "Multi-turn Conversation" to test context
3. **Code Generation**: Use "Code Generation Test" for technical queries

## 📈 **Automated Tests**

The collection includes automated tests that run after each request:

- **Status Code Validation**: Ensures 200 OK responses
- **Response Structure**: Validates required fields exist
- **Performance**: Checks response time is reasonable
- **Data Types**: Validates response data types

## 🐛 **Troubleshooting**

### **Common Issues**

1. **Connection Refused**
   - Ensure application is running on port 8080
   - Check if port is available

2. **401 Unauthorized**
   - Verify API keys are set correctly
   - Check if API keys are valid

3. **Provider Not Available**
   - Check if provider is enabled in application
   - Verify API key is set for that provider

4. **Timeout Errors**
   - Some providers may take longer to respond
   - Check network connectivity

### **Debug Steps**

1. Check application logs
2. Verify environment variables
3. Test with simple requests first
4. Check provider-specific documentation

## 📝 **Customization**

### **Adding New Tests**
1. Right-click on a folder
2. Select "Add Request"
3. Configure the request
4. Add test scripts if needed

### **Modifying Variables**
1. Go to Environment tab
2. Edit variable values
3. Save changes

### **Creating New Environments**
1. Click "New" → "Environment"
2. Add required variables
3. Save and select the environment

## 🎉 **Success Indicators**

- ✅ All providers return 200 OK
- ✅ Chat messages return valid responses
- ✅ Error handling works correctly
- ✅ Performance tests complete within 30 seconds
- ✅ Automated tests pass

## 📞 **Support**

If you encounter issues:
1. Check the main documentation: `POSTMAN_API_DOCUMENTATION.md`
2. Verify your API keys are correct
3. Ensure the application is running properly
4. Check the application logs for detailed error information

---

*Happy Testing! 🚀*

