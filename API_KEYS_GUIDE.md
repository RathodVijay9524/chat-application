# API Keys Configuration Guide

## How to Add API Keys

### Method 1: Environment Variables (Recommended)
Set these environment variables in your system:

```bash
# Windows (PowerShell)
$env:OPENAI_API_KEY="your-openai-api-key-here"
$env:ANTHROPIC_API_KEY="your-anthropic-api-key-here" 
$env:GEMINI_PROJECT_ID="your-gcp-project-id-here"

# Windows (Command Prompt)
set OPENAI_API_KEY=your-openai-api-key-here
set ANTHROPIC_API_KEY=your-anthropic-api-key-here
set GEMINI_PROJECT_ID=your-gcp-project-id-here
```

### Method 2: application.properties
Uncomment and update the API key lines in `src/main/resources/application.properties`:

```properties
# OpenAI Configuration
spring.ai.openai.api-key=$[OPENAI_API_KEY]
spring.ai.openai.chat.options.model=gpt-3.5-turbo

# Anthropic Claude Configuration  
spring.ai.anthropic.api-key=$[CLAUDAI_API_KEY]
spring.ai.anthropic.chat.options.model=claude-3-sonnet-20240229

# Google Gemini Configuration
spring.ai.vertex.ai.project-id=$[GEMIN_API_KEY]
spring.ai.vertex.ai.location=us-central1
spring.ai.vertex.ai.chat.options.model=gemini-1.5-flash
```

### Method 3: IDE Environment Variables
In your IDE (IntelliJ IDEA, VS Code, etc.), set environment variables in run configuration.

## Current Status
- ✅ Ollama: Working (no API key needed)
- ⏳ OpenAI: Needs API key
- ⏳ Claude: Needs API key  
- ⏳ Gemini: Needs API key

## Testing with Ollama
The application is currently configured to use your local Ollama instance with the `qwen2.5-coder:7b` model.
