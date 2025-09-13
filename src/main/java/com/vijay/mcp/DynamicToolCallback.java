package com.vijay.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vijay.service.RealStdioMcpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.lang.NonNull;

import java.util.Map;

/**
 * A ToolCallback implementation that forwards tool calls to a RealStdioMcpClient.
 */
@Slf4j
public class DynamicToolCallback implements ToolCallback {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ToolDefinition definition;
    private final RealStdioMcpClient client;
    private final String toolName;

    public DynamicToolCallback(String name, String description, String inputSchemaJson,
                               RealStdioMcpClient client, String toolName) {
        var builder = ToolDefinition.builder()
                .name(name)
                .description(description != null ? description : ("Dynamic tool " + name));
        if (inputSchemaJson != null && !inputSchemaJson.isBlank()) {
            builder.inputSchema(inputSchemaJson);
        } else {
            builder.inputSchema("{\"type\":\"object\"}");
        }
        this.definition = builder.build();
        this.client = client;
        this.toolName = toolName;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return definition;
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return ToolMetadata.builder().build();
    }

    @Override
    @NonNull
    public String call(@NonNull String toolInput) {
        try {
            log.info("🔧 Calling dynamic tool '{}' with input: {}", toolName, toolInput);
            
            Map<String, Object> args;
            if (toolInput == null || toolInput.isBlank()) {
                args = Map.of();
            } else {
                args = MAPPER.readValue(toolInput, Map.class);
            }

            Object result = client.callTool(toolName, args);
            
            ObjectNode node = MAPPER.createObjectNode();
            if (result != null) {
                node.set("result", MAPPER.valueToTree(result));
            } else {
                node.put("result", "Tool executed successfully");
            }
            
            String response = MAPPER.writeValueAsString(node);
            log.info("✅ Dynamic tool '{}' returned: {}", toolName, response);
            return response;
        } catch (Exception e) {
            log.error("❌ Error calling dynamic tool '{}': {}", toolName, e.getMessage(), e);
            try {
                ObjectNode errorNode = MAPPER.createObjectNode();
                errorNode.put("error", "Tool execution failed: " + e.getMessage());
                return MAPPER.writeValueAsString(errorNode);
            } catch (Exception jsonError) {
                return "{\"error\": \"Tool execution and JSON serialization failed\"}";
            }
        }
    }

    public static String toJson(Object o) {
        try {
            return MAPPER.writeValueAsString(o);
        } catch (Exception e) {
            return null;
        }
    }
}
