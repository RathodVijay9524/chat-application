package com.vijay.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vijay.service.RealStdioMcpClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.Map;

/**
 * A ToolCallback implementation that forwards tool calls to a RealStdioMcpClient.
 */
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
    public String call(String toolInput) {
        try {
            Map<String, Object> args;
            if (toolInput == null || toolInput.isBlank()) {
                args = Map.of();
            } else {
                args = MAPPER.readValue(toolInput, Map.class);
            }

            Object result = client.callTool(toolName, args);

            // Wrap result in a standard envelope
            ObjectNode node = MAPPER.createObjectNode();
            node.put("server", client.getName());
            node.put("tool", toolName);
            node.set("result", MAPPER.valueToTree(result));
            return MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            ObjectNode err = MAPPER.createObjectNode();
            err.put("server", client.getName());
            err.put("tool", toolName);
            err.put("error", e.getMessage());
            try {
                return MAPPER.writeValueAsString(err);
            } catch (Exception ignored) {
                return "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}";
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
