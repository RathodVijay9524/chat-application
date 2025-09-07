package com.vijay.repository;

import com.vijay.entity.McpServerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for MCP server database operations using MySQL
 */
@Repository
public interface McpServerRepository extends JpaRepository<McpServerEntity, String> {
    
    /**
     * Find servers by transport type
     */
    List<McpServerEntity> findByTransportType(McpServerEntity.TransportType transportType);
    
    /**
     * Find servers by enabled status
     */
    List<McpServerEntity> findByEnabled(Boolean enabled);
    
    /**
     * Find servers by status
     */
    List<McpServerEntity> findByStatus(McpServerEntity.ServerStatus status);
    
    /**
     * Find running servers (status = RUNNING)
     */
    @Query("SELECT s FROM McpServerEntity s WHERE s.status = 'RUNNING'")
    List<McpServerEntity> findRunningServers();
    
    /**
     * Find servers that should be auto-started (enabled = true, status = STOPPED)
     */
    @Query("SELECT s FROM McpServerEntity s WHERE s.enabled = true AND s.status = 'STOPPED'")
    List<McpServerEntity> findServersToAutoStart();
    
    /**
     * Find servers by name (case insensitive)
     */
    List<McpServerEntity> findByNameContainingIgnoreCase(String name);
    
    /**
     * Count servers by transport type
     */
    @Query("SELECT COUNT(s) FROM McpServerEntity s WHERE s.transportType = :transportType")
    Long countByTransportType(@Param("transportType") McpServerEntity.TransportType transportType);
    
    /**
     * Count running servers
     */
    @Query("SELECT COUNT(s) FROM McpServerEntity s WHERE s.status = 'RUNNING'")
    Long countRunningServers();
    
    /**
     * Find servers with errors
     */
    @Query("SELECT s FROM McpServerEntity s WHERE s.errorCount > 0 ORDER BY s.errorCount DESC")
    List<McpServerEntity> findServersWithErrors();
    
    /**
     * Find servers by command (for STDIO servers)
     */
    List<McpServerEntity> findByCommandContainingIgnoreCase(String command);
    
    /**
     * Find servers by URL (for SSE servers)
     */
    List<McpServerEntity> findByUrlContainingIgnoreCase(String url);
    
    /**
     * Find servers by host and port (for Socket servers)
     */
    List<McpServerEntity> findByHostAndPort(String host, Integer port);
    
    /**
     * Find servers created after a specific date
     */
    List<McpServerEntity> findByCreatedAtAfter(java.time.LocalDateTime date);
    
    /**
     * Find servers updated after a specific date
     */
    List<McpServerEntity> findByUpdatedAtAfter(java.time.LocalDateTime date);
    
    /**
     * Find servers with most runtime
     */
    @Query("SELECT s FROM McpServerEntity s ORDER BY s.totalRuntimeSeconds DESC")
    List<McpServerEntity> findServersByRuntime();
    
    /**
     * Find servers with most starts
     */
    @Query("SELECT s FROM McpServerEntity s ORDER BY s.startCount DESC")
    List<McpServerEntity> findServersByStartCount();
    
    /**
     * Check if server exists by name
     */
    boolean existsByName(String name);
    
    /**
     * Check if server exists by command (for STDIO)
     */
    boolean existsByCommand(String command);
    
    /**
     * Check if server exists by URL (for SSE)
     */
    boolean existsByUrl(String url);
    
    /**
     * Check if server exists by host and port (for Socket)
     */
    boolean existsByHostAndPort(String host, Integer port);
    
    /**
     * Find servers by enabled status and transport type
     */
    List<McpServerEntity> findByEnabledAndTransportType(Boolean enabled, McpServerEntity.TransportType transportType);
    
    /**
     * Find servers created today
     */
    @Query("SELECT s FROM McpServerEntity s WHERE DATE(s.createdAt) = CURRENT_DATE")
    List<McpServerEntity> findServersCreatedToday();
    
    /**
     * Find servers updated today
     */
    @Query("SELECT s FROM McpServerEntity s WHERE DATE(s.updatedAt) = CURRENT_DATE")
    List<McpServerEntity> findServersUpdatedToday();
}
