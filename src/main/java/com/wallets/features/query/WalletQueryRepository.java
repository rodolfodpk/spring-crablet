package com.wallets.features.query;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

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
