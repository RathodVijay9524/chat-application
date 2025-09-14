package com.vijay.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Universal MCP client interface that supports all transport types
 */
@Slf4j
public abstract class UniversalMcpClient {
    
    protected final String name;
    protected final String transportType;
    protected final ObjectMapper mapper = new ObjectMapper();
    protected boolean initialized = false;
    
    public UniversalMcpClient(String name, String transportType) {
        this.name = name;
        this.transportType = transportType;
    }
    
    public String getName() {
        return name;
    }
    
    public String getTransportType() {
        return transportType;
    }
    
    public boolean isInitialized() {
        return initialized;
    }
    
    // Abstract methods to be implemented by transport-specific clients
    public abstract void connect() throws Exception;
    public abstract void disconnect();
    public abstract boolean isConnected();
    public abstract void initialize() throws Exception;
    public abstract List<Object> listTools();
    public abstract Object callTool(String toolName, Map<String, Object> arguments);
    
    // Common utility methods
    protected String detectServerType() {
        String lowerName = name.toLowerCase();
        
        if (lowerName.contains("python") || lowerName.contains("fastmcp")) {
            return "python_fastmcp";
        } else if (lowerName.contains("spring") || lowerName.contains("java") || lowerName.contains("boot")) {
            return "spring_boot";
        } else if (lowerName.contains("dotnet") || lowerName.contains("csharp") || lowerName.contains(".net")) {
            return "dotnet";
        } else if (lowerName.contains("node") || lowerName.contains("javascript") || lowerName.contains("js")) {
            return "nodejs";
        }
        
        return "generic";
    }
    
    protected List<Object> createUniversalFallbackTools() {
        log.info("Creating universal fallback tools for MCP server {} ({})", name, transportType);
        
        String serverType = detectServerType();
        
        switch (serverType) {
            case "python_fastmcp":
                return createPythonFallbackTools();
            case "spring_boot":
                return createSpringBootFallbackTools();
            case "dotnet":
                return createDotNetFallbackTools();
            case "nodejs":
                return createNodeJsFallbackTools();
            default:
                return createGenericFallbackTools();
        }
    }
    
    protected List<Object> createPythonFallbackTools() {
        return List.of(
            createToolDefinition("fullanalysis", "Python: Full project analysis"),
            createToolDefinition("overview", "Python: Project overview"),
            createToolDefinition("structure", "Python: Analyze project structure"),
            createToolDefinition("quality", "Python: Code quality analysis"),
            createToolDefinition("refactor", "Python: Code refactoring"),
            createToolDefinition("test", "Python: Generate tests"),
            createToolDefinition("optimize", "Python: Performance optimization"),
            createToolDefinition("security", "Python: Security scan"),
            createToolDefinition("debug", "Python: Debug assistance"),
            createToolDefinition("deploy", "Python: Deployment help")
        );
    }
    
    protected List<Object> createSpringBootFallbackTools() {
        return List.of(
            createToolDefinition("create_controller", "Spring Boot: Create REST controller"),
            createToolDefinition("create_service", "Spring Boot: Create service layer"),
            createToolDefinition("create_repository", "Spring Boot: Create repository"),
            createToolDefinition("create_entity", "Spring Boot: Create JPA entity"),
            createToolDefinition("add_dependency", "Spring Boot: Add Maven/Gradle dependency"),
            createToolDefinition("configure_security", "Spring Boot: Configure Spring Security"),
            createToolDefinition("setup_database", "Spring Boot: Database configuration"),
            createToolDefinition("create_test", "Spring Boot: Create unit tests"),
            createToolDefinition("generate_swagger", "Spring Boot: Generate API documentation"),
            createToolDefinition("optimize_performance", "Spring Boot: Performance tuning")
        );
    }
    
    protected List<Object> createDotNetFallbackTools() {
        return List.of(
            createToolDefinition("create_controller", ".NET: Create API controller"),
            createToolDefinition("create_service", ".NET: Create service class"),
            createToolDefinition("create_model", ".NET: Create data model"),
            createToolDefinition("create_dbcontext", ".NET: Create Entity Framework context"),
            createToolDefinition("add_nuget_package", ".NET: Add NuGet package"),
            createToolDefinition("setup_authentication", ".NET: Configure authentication"),
            createToolDefinition("create_middleware", ".NET: Create custom middleware"),
            createToolDefinition("add_logging", ".NET: Configure logging"),
            createToolDefinition("setup_swagger", ".NET: Setup Swagger/OpenAPI"),
            createToolDefinition("create_test", ".NET: Create unit tests")
        );
    }
    
    protected List<Object> createNodeJsFallbackTools() {
        return List.of(
            createToolDefinition("create_route", "Node.js: Create Express route"),
            createToolDefinition("create_middleware", "Node.js: Create middleware"),
            createToolDefinition("create_model", "Node.js: Create data model"),
            createToolDefinition("setup_database", "Node.js: Database connection"),
            createToolDefinition("add_package", "Node.js: Add npm package"),
            createToolDefinition("setup_auth", "Node.js: Authentication setup"),
            createToolDefinition("create_api", "Node.js: Create REST API"),
            createToolDefinition("setup_websocket", "Node.js: WebSocket setup"),
            createToolDefinition("create_test", "Node.js: Create tests"),
            createToolDefinition("optimize_performance", "Node.js: Performance optimization")
        );
    }
    
    protected List<Object> createGenericFallbackTools() {
        return List.of(
            createToolDefinition("analyze_code", "Generic: Analyze code structure"),
            createToolDefinition("generate_code", "Generic: Generate code"),
            createToolDefinition("refactor_code", "Generic: Refactor code"),
            createToolDefinition("optimize_code", "Generic: Optimize performance"),
            createToolDefinition("test_code", "Generic: Create tests"),
            createToolDefinition("document_code", "Generic: Generate documentation"),
            createToolDefinition("format_code", "Generic: Format code"),
            createToolDefinition("lint_code", "Generic: Code linting"),
            createToolDefinition("build_project", "Generic: Build project"),
            createToolDefinition("deploy_project", "Generic: Deploy project")
        );
    }
    
    protected Map<String, Object> createToolDefinition(String name, String description) {
        return Map.of(
            "name", name,
            "description", description,
            "inputSchema", Map.of(
                "type", "object",
                "properties", Map.of(
                    "input", Map.of(
                        "type", "string",
                        "description", "Input parameter for the tool"
                    )
                )
            )
        );
    }
    
    // Common MCP protocol methods
    protected CompletableFuture<JsonNode> sendMcpRequest(String method, Map<String, Object> params) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, Object> request = Map.of(
                    "jsonrpc", "2.0",
                    "id", System.currentTimeMillis(),
                    "method", method,
                    "params", params != null ? params : Map.of()
                );
                
                return sendRequest(request);
            } catch (Exception e) {
                log.error("Failed to send MCP request {}: {}", method, e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }
    
    protected abstract JsonNode sendRequest(Map<String, Object> request) throws Exception;
}
