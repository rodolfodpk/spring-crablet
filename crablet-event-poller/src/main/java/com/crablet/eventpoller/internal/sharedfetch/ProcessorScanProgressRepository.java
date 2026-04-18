package com.crablet.eventpoller.internal.sharedfetch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * JDBC wrapper for {@code crablet_processor_scan_progress}.
 * Tracks the scanned position for each processor within a module.
 * Uses the write datasource for consistency with handledPosition writes.
 */
public class ProcessorScanProgressRepository {

    private static final Logger log = LoggerFactory.getLogger(ProcessorScanProgressRepository.class);

    private final DataSource dataSource;

    public ProcessorScanProgressRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public long getScannedPosition(String moduleName, String processorId) {
        String sql = """
                SELECT scanned_position FROM crablet_processor_scan_progress
                WHERE module_name = ? AND processor_id = ?
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, moduleName);
            stmt.setString(2, processorId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            log.error("Failed to get scanned position for {}/{}: {}", moduleName, processorId, e.getMessage());
            throw new RuntimeException("Failed to get scanned position", e);
        }
    }

    public void upsertScannedPosition(String moduleName, String processorId, long position) {
        String sql = """
                INSERT INTO crablet_processor_scan_progress (module_name, processor_id, scanned_position)
                VALUES (?, ?, ?)
                ON CONFLICT (module_name, processor_id)
                DO UPDATE SET scanned_position = EXCLUDED.scanned_position
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, moduleName);
            stmt.setString(2, processorId);
            stmt.setLong(3, position);
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to upsert scanned position for {}/{}: {}", moduleName, processorId, e.getMessage());
            throw new RuntimeException("Failed to upsert scanned position", e);
        }
    }

    /**
     * Loads all processor scan positions for the given module in one query.
     * Used on startup and leadership acquisition to reload state from persistence.
     */
    public Map<String, Long> getAllScannedPositions(String moduleName) {
        String sql = """
                SELECT processor_id, scanned_position FROM crablet_processor_scan_progress
                WHERE module_name = ?
                """;
        Map<String, Long> result = new HashMap<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, moduleName);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString(1), rs.getLong(2));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to get all scanned positions for module {}: {}", moduleName, e.getMessage());
            throw new RuntimeException("Failed to get all scanned positions for module " + moduleName, e);
        }
        return result;
    }
}
