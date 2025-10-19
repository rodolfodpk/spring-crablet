package com.wallets.features.query;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * Repository for wallet query operations.
 * Uses vertical slices approach - direct database access without service layer.
 */
@Repository
public class WalletQueryRepository {

    private final JdbcTemplate jdbcTemplate;

    public WalletQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }


    /**
     * Get commands for a wallet with their associated events.
     */
    public List<CommandResponse> getCommandsForWallet(String walletId, Instant timestamp, int page, int size) {
        String sql = """
                SELECT DISTINCT c.transaction_id, c.type, c.data, c.occurred_at
                FROM commands c
                JOIN events e ON e.transaction_id = c.transaction_id
                WHERE (
                    e.tags @> ARRAY['wallet_id=' || ?] OR
                    e.tags @> ARRAY['from_wallet_id=' || ?] OR
                    e.tags @> ARRAY['to_wallet_id=' || ?]
                )
                AND (?::TIMESTAMP IS NULL OR c.occurred_at <= ?::TIMESTAMP)
                ORDER BY c.occurred_at DESC
                LIMIT ? OFFSET ?
                """;

        List<CommandResponse> commands = jdbcTemplate.query(sql, new CommandRowMapper(),
                walletId, walletId, walletId,
                timestamp != null ? Timestamp.from(timestamp) : null,
                timestamp != null ? Timestamp.from(timestamp) : null,
                size + 1, page * size);

        // Fetch events for each command
        for (CommandResponse command : commands) {
            List<EventResponse> events = getEventsForTransaction(command.transactionId());
            // Replace the command with one that has events
            int index = commands.indexOf(command);
            commands.set(index, new CommandResponse(
                    command.transactionId(),
                    command.type(),
                    command.data(),
                    command.occurredAt(),
                    events
            ));
        }

        return commands;
    }

    public long getTotalCommandsForWallet(String walletId, Instant timestamp) {
        String sql = """
                SELECT COUNT(DISTINCT c.transaction_id)
                FROM commands c
                JOIN events e ON e.transaction_id = c.transaction_id
                WHERE (
                    e.tags @> ARRAY['wallet_id=' || ?] OR
                    e.tags @> ARRAY['from_wallet_id=' || ?] OR
                    e.tags @> ARRAY['to_wallet_id=' || ?]
                )
                AND (?::TIMESTAMP IS NULL OR c.occurred_at <= ?::TIMESTAMP)
                """;

        Long count = jdbcTemplate.queryForObject(sql, Long.class,
                walletId, walletId, walletId,
                timestamp != null ? Timestamp.from(timestamp) : null,
                timestamp != null ? Timestamp.from(timestamp) : null);
        return count != null ? count : 0L;
    }

    /**
     * Get wallet events as individual rows with pagination and timestamp filtering.
     * Used for history queries - pagination done at database level for efficiency.
     */
    public List<EventResponse> getWalletEvents(String walletId, Instant before, int limit, int offset) {
        String sql = """
                SELECT type, data, position, occurred_at
                FROM events
                WHERE (
                    tags @> ARRAY['wallet_id=' || ?] OR
                    tags @> ARRAY['from_wallet_id=' || ?] OR
                    tags @> ARRAY['to_wallet_id=' || ?]
                )
                AND (?::TIMESTAMP IS NULL OR occurred_at <= ?::TIMESTAMP)
                ORDER BY transaction_id, position ASC
                LIMIT ? OFFSET ?
                """;

        return jdbcTemplate.query(sql, new EventRowMapper(),
                walletId, walletId, walletId,
                before != null ? Timestamp.from(before) : null,
                before != null ? Timestamp.from(before) : null,
                limit, offset);
    }

    /**
     * Get total count of wallet events (for pagination metadata).
     */
    public long getWalletEventsCount(String walletId, Instant before) {
        String sql = """
                SELECT COUNT(*)
                FROM events
                WHERE (
                    tags @> ARRAY['wallet_id=' || ?] OR
                    tags @> ARRAY['from_wallet_id=' || ?] OR
                    tags @> ARRAY['to_wallet_id=' || ?]
                )
                AND (?::TIMESTAMP IS NULL OR occurred_at <= ?::TIMESTAMP)
                """;

        Long count = jdbcTemplate.queryForObject(sql, Long.class,
                walletId, walletId, walletId,
                before != null ? Timestamp.from(before) : null,
                before != null ? Timestamp.from(before) : null);

        return count != null ? count : 0L;
    }

    /**
     * Get wallet events as aggregated JSON array (optimized for projection).
     * Used for state projection without pagination - leverages PostgreSQL jsonb_agg() for efficient binary transfer.
     */
    public byte[] getWalletEventsAsJsonArray(String walletId) {
        String sql = """
                SELECT COALESCE(jsonb_agg(data ORDER BY transaction_id, position), '[]'::jsonb)
                FROM events
                WHERE (
                    tags @> ARRAY['wallet_id=' || ?] OR
                    tags @> ARRAY['from_wallet_id=' || ?] OR
                    tags @> ARRAY['to_wallet_id=' || ?]
                )
                """;

        String jsonArray = jdbcTemplate.queryForObject(sql, String.class,
                walletId, walletId, walletId);

        return jsonArray != null ? jsonArray.getBytes(StandardCharsets.UTF_8) : "[]".getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Get events for a specific transaction.
     */
    private List<EventResponse> getEventsForTransaction(String transactionId) {
        String sql = """
                SELECT type, data, position, occurred_at
                FROM events
                WHERE transaction_id = ?::xid8
                ORDER BY position
                """;

        return jdbcTemplate.query(sql, new EventRowMapper(), transactionId);
    }

    /**
     * Row mapper for EventResponse.
     */
    private static class EventRowMapper implements RowMapper<EventResponse> {
        @Override
        public EventResponse mapRow(@NonNull ResultSet rs, int rowNum) throws SQLException {
            return new EventResponse(
                    rs.getString("type"),
                    rs.getString("data"), // JSON as string
                    rs.getLong("position"),
                    rs.getTimestamp("occurred_at").toInstant()
            );
        }
    }

    /**
     * Row mapper for CommandResponse.
     * Events are fetched separately and added to each command after initial mapping.
     */
    private static class CommandRowMapper implements RowMapper<CommandResponse> {
        @Override
        public CommandResponse mapRow(@NonNull ResultSet rs, int rowNum) throws SQLException {
            return new CommandResponse(
                    rs.getString("transaction_id"), // xid8 should be retrieved as String
                    rs.getString("type"),
                    rs.getString("data"), // JSON as string
                    rs.getTimestamp("occurred_at").toInstant(),
                    List.of() // Events are fetched separately in getCommandsForWallet()
            );
        }
    }

}
