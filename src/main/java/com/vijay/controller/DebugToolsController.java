package com.vijay.controller;

import com.vijay.service.DynamicMcpServerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/debug")
public class DebugToolsController {

    private static final Logger log = LoggerFactory.getLogger(DebugToolsController.class);

    private final ToolCallbackProvider toolCallbackProvider;
    private final DynamicMcpServerService dynamicMcpServerService;

    @Autowired
    public DebugToolsController(ToolCallbackProvider toolCallbackProvider,
                                DynamicMcpServerService dynamicMcpServerService) {
        this.toolCallbackProvider = toolCallbackProvider;
        this.dynamicMcpServerService = dynamicMcpServerService;
    }

    @GetMapping("/tools")
    public Map<String, Object> listAllTools() {
        Map<String, Object> resp = new HashMap<>();
        List<Map<String, Object>> tools = new ArrayList<>();
        try {
            ToolCallback[] callbacks = toolCallbackProvider != null ? toolCallbackProvider.getToolCallbacks() : new ToolCallback[0];
            for (ToolCallback cb : callbacks) {
                Map<String, Object> row = new HashMap<>();
                ToolDefinition def = cb.getToolDefinition();
                // ToolDefinition in Spring AI exposes name(), description(), inputSchema()
                row.put("name", def.name());
                row.put("description", def.description());
                row.put("inputSchema", def.inputSchema());
                row.put("callbackClass", cb.getClass().getName());
                tools.add(row);
            }

            resp.put("status", "success");
            resp.put("toolCount", tools.size());
            resp.put("tools", tools);
            resp.put("activeServers", dynamicMcpServerService.getActiveServers());
            resp.put("serverStatus", dynamicMcpServerService.getServerStatus());
        } catch (Exception e) {
            log.error("Error listing tools: {}", e.getMessage(), e);
            resp.put("status", "error");
            resp.put("message", e.getMessage());
            resp.put("tools", tools);
        }
        return resp;
    }
}
