package com.crablet.eventpoller.internal.sharedfetch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * JDBC wrapper for {@code crablet_module_scan_progress}.
 * Tracks how far the module's shared fetch cursor has advanced.
 * Uses the write datasource for consistency with leader election and progress writes.
 */
public class ModuleScanProgressRepository {

    private static final Logger log = LoggerFactory.getLogger(ModuleScanProgressRepository.class);

    private final DataSource dataSource;

    public ModuleScanProgressRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public long getScanPosition(String moduleName) {
        String sql = "SELECT scan_position FROM crablet_module_scan_progress WHERE module_name = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, moduleName);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            log.error("Failed to get scan position for module {}: {}", moduleName, e.getMessage());
            throw new RuntimeException("Failed to get scan position for module " + moduleName, e);
        }
    }

    public void upsertScanPosition(String moduleName, long position) {
        String sql = """
                INSERT INTO crablet_module_scan_progress (module_name, scan_position)
                VALUES (?, ?)
                ON CONFLICT (module_name) DO UPDATE SET scan_position = EXCLUDED.scan_position
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, moduleName);
            stmt.setLong(2, position);
            stmt.executeUpdate();
        } catch (SQLException e) {
            log.error("Failed to upsert scan position for module {}: {}", moduleName, e.getMessage());
            throw new RuntimeException("Failed to upsert scan position for module " + moduleName, e);
        }
    }
}
