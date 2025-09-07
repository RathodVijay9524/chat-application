# 🤖 Multi-Modal Chat Frontend

A React TypeScript frontend application for the Multi-Modal Chat Application built with Spring AI 1.0.1.

## 🚀 Features

- **Multiple AI Providers**: Support for OpenAI, Anthropic Claude, Google Gemini, Ollama, OpenRouter, Groq, and Hugging Face
- **Redux State Management**: Built with Redux Toolkit for efficient state management
- **Real-time Chat**: Interactive chat interface with message history
- **Provider Selection**: Easy switching between different AI providers and models
- **Error Handling**: Comprehensive error handling and user feedback
- **Responsive Design**: Mobile-friendly responsive design
- **TypeScript**: Full TypeScript support for type safety

## 🛠️ Tech Stack

- **React 19.1.1** - UI library
- **TypeScript** - Type safety
- **Redux Toolkit** - State management
- **Axios** - HTTP client
- **CSS3** - Styling

## 📦 Installation

1. **Install dependencies**:
   ```bash
   npm install
   ```

2. **Set up environment variables**:
   Create a `.env` file in the root directory:
   ```env
   REACT_APP_API_URL=http://localhost:8080
   ```

3. **Start the development server**:
   ```bash
   npm start
   ```

4. **Build for production**:
   ```bash
   npm run build
   ```

## 🏗️ Project Structure

```
src/
├── components/
│   └── Chat/
│       ├── ChatInterface.tsx    # Main chat component
│       └── ChatInterface.css    # Chat styles
├── services/
│   └── chatApi.ts              # API service layer
├── store/
│   ├── slices/
│   │   └── chatSlice.ts        # Redux chat slice
│   ├── store.ts                # Redux store configuration
│   └── hooks.ts                # Typed Redux hooks
├── types/
│   └── chat.ts                 # TypeScript type definitions
├── App.tsx                     # Main app component
└── index.tsx                   # App entry point
```

## 🔧 Redux Store

### State Structure
```typescript
interface ChatState {
  messages: ChatMessage[];           // Chat message history
  providers: Provider[];             // Available AI providers
  selectedProvider: string;          // Currently selected provider
  selectedModel: string;             // Currently selected model
  isLoading: boolean;                // Loading state
  error: string | null;              // Error messages
  currentConversationId: string;     // Current conversation ID
}
```

### Available Actions
- `fetchProviders()` - Load available AI providers
- `sendMessage(request)` - Send a chat message
- `setSelectedProvider(provider)` - Change AI provider
- `setSelectedModel(model)` - Change AI model
- `clearMessages()` - Clear chat history
- `startNewConversation()` - Start a new conversation

## 🌐 API Integration

The frontend connects to the Spring Boot backend API:

- **Base URL**: `http://localhost:8080`
- **Endpoints**:
  - `GET /api/chat/providers` - Get available providers
  - `POST /api/chat/message` - Send chat message
  - `GET /api/chat/tools` - Get MCP tools
  - `GET /api/chat/rag` - Get RAG status

## 🎨 UI Components

### ChatInterface
The main chat component that includes:
- Provider and model selection dropdowns
- Message history display
- Input form for new messages
- Loading states and error handling
- Responsive design

### Features
- **Real-time messaging**: Send and receive messages instantly
- **Provider switching**: Easy switching between AI providers
- **Model selection**: Choose specific models for each provider
- **Message history**: View all previous messages in the conversation
- **Error handling**: Clear error messages and retry functionality
- **Loading states**: Visual feedback during API calls

## 🔄 Usage

1. **Start the backend**: Make sure the Spring Boot application is running on port 8080
2. **Start the frontend**: Run `npm start` to start the development server
3. **Open browser**: Navigate to `http://localhost:3000`
4. **Select provider**: Choose an AI provider from the dropdown
5. **Select model**: Choose a specific model (optional)
6. **Start chatting**: Type a message and press Send

## 🧪 Testing

The application includes comprehensive error handling and testing:

- **API Error Handling**: Graceful handling of API errors
- **Loading States**: Visual feedback during operations
- **Input Validation**: Form validation and error messages
- **Provider Validation**: Checks for provider availability

## 🚀 Deployment

1. **Build the application**:
   ```bash
   npm run build
   ```

2. **Serve the build folder**:
   ```bash
   npx serve -s build
   ```

3. **Deploy to your hosting platform** (Vercel, Netlify, etc.)

## 🔧 Configuration

### Environment Variables
- `REACT_APP_API_URL`: Backend API URL (default: http://localhost:8080)

### Redux DevTools
Redux DevTools are enabled in development mode for debugging.

## 📱 Responsive Design

The application is fully responsive and works on:
- Desktop computers
- Tablets
- Mobile phones

## 🐛 Troubleshooting

### Common Issues

1. **Connection Refused**:
   - Ensure the Spring Boot backend is running
   - Check the API URL in `.env` file

2. **CORS Errors**:
   - Make sure the backend has CORS configured
   - Check if the frontend and backend are on the same domain

3. **Provider Not Available**:
   - Check if the provider is enabled in the backend
   - Verify API keys are set correctly

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test thoroughly
5. Submit a pull request

## 📄 License

This project is licensed under the MIT License.

---

**Happy Chatting! 🚀**

