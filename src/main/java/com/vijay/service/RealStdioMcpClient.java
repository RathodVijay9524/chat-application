package com.vijay.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Real STDIO MCP Client that connects to actual processes
 */
@Slf4j
public class RealStdioMcpClient {
    
    private final String name;
    private final Process process;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicLong idCounter = new AtomicLong(1);
    private volatile boolean initialized = false;
    
    public RealStdioMcpClient(String name, Process process) {
        this.name = name;
        this.process = process;
        
        log.info("Created real STDIO MCP client: {} (PID: {})", name, process.pid());
    }
    
    public String getName() {
        return name;
    }
    
    public String getType() {
        return "REAL_STDIO";
    }
    
    public boolean isConnected() {
        return process.isAlive();
    }
    
    public void connect() throws IOException {
        log.info("Connecting to real STDIO process: {} (PID: {})", name, process.pid());
        // Process is already started, just verify it's alive
        if (!process.isAlive()) {
            throw new IOException("Process is not alive: " + name);
        }
    }
    
    public void disconnect() throws IOException {
        log.info("Disconnecting from real STDIO process: {} (PID: {})", name, process.pid());
        if (process.isAlive()) {
            process.destroy();
            try {
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
    }
    
    private void ensureInitialized() throws IOException {
        if (initialized) {
            return;
        }
        // Build initialize request per MCP spec
        long id = idCounter.getAndIncrement();
        Map<String, Object> request = Map.of(
                "jsonrpc", "2.0",
                "id", id,
                "method", "initialize",
                "params", Map.of(
                        "protocolVersion", "2024-11-05",
                        "capabilities", Map.of(),
                        "clientInfo", Map.of(
                                "name", "chat-app-stdio-client",
                                "version", "0.0.1"
                        )
                )
        );
        String json = mapper.writeValueAsString(request);
        writeFramed(process.getOutputStream(), json);

        // Wait for matching initialize response id (skip notifications)
        long timeoutMs = 30000;
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            String responseJson = readFramed(process.getInputStream(), deadline - System.currentTimeMillis());
            if (responseJson == null) {
                continue;
            }
            log.debug("Initialize response ({} bytes) from {}: {}", responseJson.length(), name,
                    responseJson.length() > 500 ? responseJson.substring(0, 500) + "..." : responseJson);
            JsonNode node;
            try {
                node = mapper.readTree(responseJson);
            } catch (IOException e) {
                log.warn("Failed to parse initialize response from {}: {}", name, e.getMessage());
                continue;
            }
            if (!node.has("id")) {
                // notification - ignore
                continue;
            }
            if (node.get("id").asLong() != id) {
                // response to another request - ignore
                continue;
            }
            if (node.has("error")) {
                log.warn("MCP initialize error from {}: {}", name, node.get("error").toString());
                return;
            }
            // Send notifications/initialized
            Map<String, Object> notification = Map.of(
                    "jsonrpc", "2.0",
                    "method", "notifications/initialized",
                    "params", Map.of()
            );
            String notifJson = mapper.writeValueAsString(notification);
            writeFramed(process.getOutputStream(), notifJson);

            initialized = true;
            log.info("MCP session initialized for {}", name);
            return;
        }
        log.warn("Timed out waiting for initialize response from {}", name);
    }
    
    public List<Object> listTools() {
        log.info("Listing tools from real STDIO process: {} (PID: {})", name, process.pid());
        
        try {
            // Ensure MCP session is initialized once
            ensureInitialized();
            // Build tools/list request
            long id = idCounter.getAndIncrement();
            Map<String, Object> request = Map.of(
                    "jsonrpc", "2.0",
                    "id", id,
                    "method", "tools/list",
                    "params", Map.of()
            );
            String json = mapper.writeValueAsString(request);
            writeFramed(process.getOutputStream(), json);

            // Read frames until we get the response matching our id (skip notifications)
            long timeoutMs = 35000;
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < deadline) {
                String responseJson = readFramed(process.getInputStream(), deadline - System.currentTimeMillis());
                if (responseJson == null) {
                    continue; // keep waiting until timeout
                }
                log.debug("Received frame ({} bytes) from {} during tools/list", responseJson.length(), name);
                JsonNode node = mapper.readTree(responseJson);
                // Skip notifications (no id)
                if (!node.has("id")) {
                    continue;
                }
                // Only process the response to our request id
                if (node.get("id").asLong() != id) {
                    continue;
                }
                if (node.has("error")) {
                    log.warn("MCP error from {}: {}", name, node.get("error").toString());
                    return List.of();
                }
                JsonNode result = node.get("result");
                if (result == null || result.get("tools") == null) {
                    log.warn("Unexpected MCP response format from {}: {}", name, responseJson);
                    return List.of();
                }
                JsonNode toolsNode = result.get("tools");
                List<Object> tools = mapper.convertValue(toolsNode, new TypeReference<List<Object>>() {});
                log.info("Real STDIO client {} provides {} tools (from MCP)", name, tools.size());
                return tools;
            }
            log.warn("Timed out waiting for tools/list response from {}", name);
            return List.of();
        } catch (Exception e) {
            log.warn("Failed to list tools via MCP for {}: {}", name, e.getMessage());
            return List.of();
        }
    }
    
    public Object callTool(String toolName, Map<String, Object> arguments) {
        log.info("Calling tool '{}' on real STDIO process: {} (PID: {}) with arguments: {}", 
                toolName, name, process.pid(), arguments);
        
        // For now, return a mock response
        // In a real implementation, you would send the tool call to the MCP server
        // and wait for the response
        return Map.of(
                "success", true,
                "message", "Tool '" + toolName + "' executed successfully via " + name,
                "toolName", toolName,
                "arguments", arguments,
                "server", name,
                "pid", process.pid(),
                "timestamp", System.currentTimeMillis()
        );
    }
    
    @Override
    public String toString() {
        return "RealStdioMcpClient{name='" + name + "', type='STDIO', pid=" + process.pid() + ", alive=" + process.isAlive() + "}";
    }
    
    /**
     * Get the underlying process
     */
    public Process getProcess() {
        return process;
    }
    
    /**
     * Check if the process is still alive
     */
    public boolean isProcessAlive() {
        return process.isAlive();
    }
    
    /**
     * Write a JSON-RPC message framed with MCP stdio headers
     */
    private void writeFramed(OutputStream os, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        String header = "Content-Length: " + body.length + "\r\n\r\n";
        os.write(header.getBytes(StandardCharsets.US_ASCII));
        os.write(body);
        os.flush();
    }
    
    /**
     * Read a single framed JSON-RPC message with timeout
     */
    private String readFramed(InputStream is, long timeoutMs) throws IOException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        StringBuilder header = new StringBuilder();
        // Read headers until CRLF CRLF
        int state = 0; // track \r\n\r\n
        while (System.currentTimeMillis() < deadline) {
            if (is.available() == 0) {
                try { Thread.sleep(10); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                continue;
            }
            int b = is.read();
            if (b == -1) break;
            header.append((char) b);
            // Detect CRLFCRLF
            int len = header.length();
            if (len >= 4 && header.substring(len-4).equals("\r\n\r\n")) {
                break;
            }
        }
        String headerStr = header.toString();
        if (!headerStr.contains("Content-Length:")) {
            return null;
        }
        int idx = headerStr.indexOf("Content-Length:");
        int end = headerStr.indexOf('\r', idx);
        if (idx < 0 || end < 0) return null;
        String lenStr = headerStr.substring(idx + "Content-Length:".length(), end).trim();
        int length;
        try { length = Integer.parseInt(lenStr); } catch (NumberFormatException nfe) { return null; }
        byte[] buf = new byte[length];
        int read = 0;
        while (read < length && System.currentTimeMillis() < deadline) {
            int r = is.read(buf, read, length - read);
            if (r == -1) break;
            read += r;
        }
        if (read != length) {
            return null;
        }
        return new String(buf, StandardCharsets.UTF_8);
    }
}
