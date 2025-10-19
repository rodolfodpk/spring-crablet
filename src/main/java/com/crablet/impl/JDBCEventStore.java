package com.crablet.impl;

import com.crablet.core.AppendCondition;
import com.crablet.core.AppendEvent;
import com.crablet.core.Command;
import com.crablet.core.ConcurrencyException;
import com.crablet.core.Cursor;
import com.crablet.core.EventStore;
import com.crablet.core.EventStoreConfig;
import com.crablet.core.ProjectionResult;
import com.crablet.core.Query;
import com.crablet.core.QueryItem;
import com.crablet.core.StateProjector;
import com.crablet.core.StoredEvent;
import com.crablet.core.Tag;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * JDBC-based implementation of EventStore using PostgreSQL functions.
 * This implementation uses the existing PostgreSQL schema and functions.
 */
@Component
public class JDBCEventStore implements EventStore {

    private static final Logger log = LoggerFactory.getLogger(JDBCEventStore.class);

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    private final EventStoreConfig config;

    /**
     * Singleton RowMapper for StoredEvent objects.
     * Reused across all queries to avoid lambda allocation overhead.
     */
    private final RowMapper<StoredEvent> EVENT_ROW_MAPPER = (rs, rowNum) -> {
        String type = rs.getString("type");
        String[] tagArray = (String[]) rs.getArray("tags").getArray();
        List<Tag> tags = parseTags(tagArray);
        byte[] data = rs.getString("data").getBytes(StandardCharsets.UTF_8);
        String transactionId = rs.getString("transaction_id");
        long position = rs.getLong("position");
        Instant occurredAt = rs.getTimestamp("occurred_at").toInstant();

        return new StoredEvent(type, tags, data, transactionId, position, occurredAt);
    };

    @Autowired
    public JDBCEventStore(DataSource dataSource, ObjectMapper objectMapper, EventStoreConfig config) {
        if (dataSource == null) {
            throw new IllegalArgumentException("DataSource must not be null");
        }
        if (objectMapper == null) {
            throw new IllegalArgumentException("ObjectMapper must not be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("EventStoreConfig must not be null");
        }
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
        this.config = config;
    }

    @Override
    @CircuitBreaker(name = "database")
    @Retry(name = "database")
    @TimeLimiter(name = "database")
    public List<StoredEvent> query(Query query, Cursor after) {
        try {
            // Build SQL query directly instead of using the function
            StringBuilder sql = new StringBuilder("SELECT type, tags, data, transaction_id, position, occurred_at FROM events");
            List<Object> params = new ArrayList<>();

            // after_position parameter
            long afterPosition = after != null ? after.position().value() : 0L;
            if (afterPosition > 0) {
                sql.append(" WHERE position > ?");
                params.add(afterPosition);
            }

            // Build OR conditions for each QueryItem (go-crablet style)
            if (!query.isEmpty()) {
                List<String> orConditions = new ArrayList<>();

                for (QueryItem item : query.items()) {
                    StringBuilder condition = new StringBuilder("(");
                    List<String> andConditions = new ArrayList<>();

                    // Handle event types for this QueryItem
                    if (item.hasEventTypes() && !item.eventTypes().isEmpty()) {
                        andConditions.add("type = ANY(?)");
                        params.add(item.eventTypes().toArray(new String[0]));
                    }

                    // Handle tags for this QueryItem
                    if (item.hasTags() && !item.tags().isEmpty()) {
                        List<String> tagStrings = item.tags().stream()
                                .map(tag -> tag.key() + "=" + tag.value())
                                .collect(Collectors.toList());
                        andConditions.add("tags @> ?::TEXT[]");
                        params.add(tagStrings.toArray(new String[0]));
                    }

                    if (!andConditions.isEmpty()) {
                        condition.append(String.join(" AND ", andConditions));
                        condition.append(")");
                        orConditions.add(condition.toString());
                    }
                }

                if (!orConditions.isEmpty()) {
                    if (afterPosition > 0) {
                        sql.append(" AND ");
                    } else {
                        sql.append(" WHERE ");
                    }
                    sql.append("(").append(String.join(" OR ", orConditions)).append(")");
                }
            }

            sql.append(" ORDER BY position ASC");

            // Use raw JDBC for better control
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement stmt = connection.prepareStatement(sql.toString())) {

                stmt.setFetchSize(config.getFetchSize());

                // Set parameters
                for (int i = 0; i < params.size(); i++) {
                    Object param = params.get(i);
                    if (param instanceof String[]) {
                        stmt.setArray(i + 1, connection.createArrayOf("text", (String[]) param));
                    } else {
                        stmt.setObject(i + 1, param);
                    }
                }

                // Execute query and process results
                List<StoredEvent> events = new ArrayList<>();
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        events.add(EVENT_ROW_MAPPER.mapRow(rs, 0));
                    }
                }
                return events;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to query events", e);
        }
    }

    @Override
    @CircuitBreaker(name = "database")
    @Retry(name = "database")
    @TimeLimiter(name = "database")
    public void append(List<AppendEvent> events) {
        if (events.isEmpty()) {
            return;
        }

        try {
            // Prepare arrays for append_events_batch function
            String[] types = events.stream().map(AppendEvent::type).toArray(String[]::new);
            String[] tagArrays = events.stream()
                    .map(event -> convertTagsToPostgresArray(event.tags()))
                    .toArray(String[]::new);
            String[] dataStrings = events.stream()
                    .map(event -> new String(event.data(), StandardCharsets.UTF_8))
                    .toArray(String[]::new);

            // Call append_events_batch function with JSONB[] cast using raw JDBC
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement stmt = connection.prepareStatement("SELECT append_events_batch(?, ?, ?::jsonb[])")) {

                stmt.setArray(1, connection.createArrayOf("varchar", types));
                stmt.setArray(2, connection.createArrayOf("varchar", tagArrays));
                stmt.setArray(3, connection.createArrayOf("jsonb", dataStrings));

                stmt.execute();
            }
        } catch (SQLException e) {
            // Handle PostgreSQL function errors like go-crablet does
            String sqlState = e.getSQLState();

            // Handle PostgreSQL procedural errors (P0001, etc.)
            if (sqlState != null && sqlState.startsWith("P")) {
                throw new RuntimeException("PostgreSQL procedural error (" + sqlState + "): " + e.getMessage(), e);
            }

            throw new RuntimeException("Failed to append events", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to append events", e);
        }
    }

    @Override
    public void appendIf(List<AppendEvent> events, AppendCondition condition) {
        if (events.isEmpty()) {
            return;
        }

        try {
            // Extract event types and tags from failIfEventsMatch query
            List<String> eventTypes = condition.failIfEventsMatch().items().stream()
                    .flatMap(item -> item.eventTypes().stream())
                    .distinct()
                    .toList();

            List<String> conditionTags = condition.failIfEventsMatch().items().stream()
                    .flatMap(item -> item.tags().stream())
                    .map(tag -> tag.key() + "=" + tag.value())
                    .distinct()
                    .toList();

            // Prepare event data
            String[] types = events.stream().map(AppendEvent::type).toArray(String[]::new);
            String[] tagArrays = events.stream()
                    .map(event -> convertTagsToPostgresArray(event.tags()))
                    .toArray(String[]::new);
            String[] dataStrings = events.stream()
                    .map(event -> new String(event.data()))
                    .toArray(String[]::new);

            // Call append_events_if with position-based parameters only (no transaction_id)
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement stmt = connection.prepareStatement("SELECT append_events_if(?, ?, ?::jsonb[], ?, ?, ?)")) {

                stmt.setArray(1, connection.createArrayOf("varchar", types));
                stmt.setArray(2, connection.createArrayOf("varchar", tagArrays));
                stmt.setArray(3, connection.createArrayOf("jsonb", dataStrings));
                stmt.setObject(4, eventTypes.isEmpty() ? null : eventTypes.toArray(new String[0]));
                stmt.setObject(5, conditionTags.isEmpty() ? null : conditionTags.toArray(new String[0]));
                stmt.setObject(6, condition.afterCursor().position().value());

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String jsonResult = rs.getString(1);

                        // Handle different return types from PostgreSQL function
                        if (jsonResult == null || jsonResult.trim().isEmpty()) {
                            throw new ConcurrencyException("AppendIf condition failed: no result");
                        }

                        // Try to parse as JSON first
                        try {
                            Map<String, Object> result = objectMapper.readValue(jsonResult, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                            });

                            // Check result and throw ConcurrencyException if condition failed
                            Object successObj = result.get("success");
                            if (successObj instanceof Boolean) {
                                Boolean success = (Boolean) successObj;
                                if (!success) {
                                    throw new ConcurrencyException("AppendCondition violated: " + result.get("message"));
                                }
                            } else {
                                // Handle case where success is not a boolean
                                log.warn("Unexpected success value type: {}", successObj);
                                throw new ConcurrencyException("AppendCondition violated: " + result.get("message"));
                            }
                        } catch (ConcurrencyException e) {
                            // Re-throw ConcurrencyException immediately
                            throw e;
                        } catch (com.fasterxml.jackson.core.JsonProcessingException jsonParseException) {
                            // If JSON parsing fails, check if it's a simple error message
                            if (jsonResult.contains("AppendIf condition failed") || jsonResult.contains("condition failed")) {
                                throw new ConcurrencyException("AppendCondition violated: " + jsonResult);
                            }
                            // If it's not a recognizable error message, rethrow the parsing exception
                            throw new RuntimeException("Failed to parse JSONB result: " + jsonResult, jsonParseException);
                        } catch (Exception jsonParseException) {
                            // Handle other JSON parsing exceptions
                            log.error("Unexpected JSON parsing error: {}", jsonParseException.getMessage());
                            if (jsonResult.contains("AppendIf condition failed") || jsonResult.contains("condition failed")) {
                                throw new ConcurrencyException("AppendCondition violated: " + jsonResult);
                            }
                            throw new RuntimeException("Failed to parse JSONB result: " + jsonResult, jsonParseException);
                        }
                    } else {
                        throw new RuntimeException("No result from append_events_if");
                    }
                }
            }

        } catch (ConcurrencyException e) {
            throw e;
        } catch (RuntimeException e) {
            // Re-throw ConcurrencyException if it's wrapped in RuntimeException
            if (e instanceof ConcurrencyException) {
                throw e;
            }
            // Handle other RuntimeException
            throw e;
        } catch (SQLException e) {
            // Handle PostgreSQL function errors like go-crablet does
            String sqlState = e.getSQLState();

            // Handle PostgreSQL RAISE EXCEPTION (P0001) - go-crablet style
            if ("P0001".equals(sqlState)) {
                String message = e.getMessage();
                if (message != null && message.contains("AppendIf condition failed")) {
                    throw new ConcurrencyException("Concurrent modification: " + message, e);
                }
                // Other P0001 errors from PostgreSQL functions
                throw new ConcurrencyException("PostgreSQL function error: " + message, e);
            }

            // Handle other PostgreSQL-specific errors
            if (sqlState != null && sqlState.startsWith("P")) {
                throw new RuntimeException("PostgreSQL procedural error (" + sqlState + "): " + e.getMessage(), e);
            }

            throw new RuntimeException("Failed to append events with condition", e);
        } catch (Exception e) {
            // Fallback: check message content for backward compatibility
            if (e.getMessage() != null && e.getMessage().contains("AppendIf condition failed")) {
                throw new ConcurrencyException("Concurrent modification: " + e.getMessage(), e);
            }

            throw new RuntimeException("Failed to append events", e);
        }
    }

    @Override
    public <T> ProjectionResult<T> project(List<StateProjector<T>> projectors, Cursor after, Class<T> stateType) {
        if (projectors == null) {
            throw new NullPointerException("Projectors must not be null");
        }
        if (after == null) {
            throw new NullPointerException("Cursor must not be null");
        }
        if (stateType == null) {
            throw new NullPointerException("State type must not be null");
        }

        // 1. Build query from projectors (DCB spec: automatically derive from decision model)
        Query query = buildQueryFromProjectors(projectors);

        // 2. Read events matching the query, starting AFTER cursor
        List<StoredEvent> events = query(query, after);

        // 3. Apply projectors to build state
        T state = projectors.isEmpty()
                ? null
                : projectors.get(0).getInitialState();

        for (StoredEvent event : events) {
            for (StateProjector<T> projector : projectors) {
                if (projector.handles(event)) {
                    state = projector.transition(state, event);
                }
            }
        }

        // 4. Return state with cursor of last processed event (for DCB optimistic locking)
        Cursor newCursor = events.isEmpty()
                ? after
                : Cursor.of(
                events.get(events.size() - 1).position(),
                events.get(events.size() - 1).occurredAt(),
                events.get(events.size() - 1).transactionId()
        );

        return ProjectionResult.of(state, newCursor);
    }

    @Override
    public ProjectionResult<Map<String, Object>> project(List<StateProjector> projectors, Cursor after) {
        if (projectors == null) {
            throw new IllegalArgumentException("Projectors cannot be null");
        }
        if (after == null) {
            throw new IllegalArgumentException("Cursor cannot be null");
        }

        // Build query from projectors
        @SuppressWarnings("unchecked")
        Query query = buildQueryFromProjectors((List<StateProjector<Map<String, Object>>>) (List<?>) projectors);
        List<StoredEvent> events = query(query, after);

        // Apply projectors to build state
        Map<String, Object> state = projectors.isEmpty()
                ? new HashMap<>()
                : (Map<String, Object>) projectors.get(0).getInitialState();

        for (StoredEvent event : events) {
            for (StateProjector projector : projectors) {
                if (projector.handles(event)) {
                    state = (Map<String, Object>) projector.transition(state, event);
                }
            }
        }

        // Return with proper cursor
        Cursor newCursor = events.isEmpty()
                ? after
                : Cursor.of(
                events.get(events.size() - 1).position(),
                events.get(events.size() - 1).occurredAt(),
                events.get(events.size() - 1).transactionId()
        );

        return ProjectionResult.of(state, newCursor);
    }

    /**
     * Private method to append events using a provided connection.
     * Used internally by ConnectionScopedEventStore.
     */
    private void appendWithConnection(Connection connection, List<AppendEvent> events) {
        if (events.isEmpty()) {
            return;
        }

        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT append_events_batch(?, ?, ?)")) {

            // Prepare arrays for append_events_batch function
            String[] types = events.stream().map(AppendEvent::type).toArray(String[]::new);
            String[] tagArrays = events.stream()
                    .map(event -> convertTagsToPostgresArray(event.tags()))
                    .toArray(String[]::new);
            String[] dataStrings = events.stream()
                    .map(event -> new String(event.data()))
                    .toArray(String[]::new);

            stmt.setArray(1, connection.createArrayOf("text", types));
            stmt.setArray(2, connection.createArrayOf("text", tagArrays));
            stmt.setArray(3, connection.createArrayOf("jsonb", dataStrings));

            stmt.execute();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to append events with connection", e);
        }
    }

    /**
     * Private method to append events conditionally using a provided connection.
     * Used internally by ConnectionScopedEventStore.
     */
    private void appendIfWithConnection(Connection connection, List<AppendEvent> events, AppendCondition condition) {
        if (events.isEmpty()) {
            return;
        }

        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT append_events_if(?::text[], ?::text[], ?::jsonb[], ?::text[], ?::text[], ?)")) {

            // Prepare arrays for append_events_batch_if function
            String[] types = events.stream().map(AppendEvent::type).toArray(String[]::new);
            String[] tagArrays = events.stream()
                    .map(event -> convertTagsToPostgresArray(event.tags()))
                    .toArray(String[]::new);
            String[] dataStrings = events.stream()
                    .map(event -> new String(event.data()))
                    .toArray(String[]::new);

            // Extract event types and tags from failIfEventsMatch query
            List<String> eventTypes = condition != null ? condition.failIfEventsMatch().items().stream()
                    .flatMap(item -> item.eventTypes().stream())
                    .distinct()
                    .toList() : List.of();

            List<String> conditionTags = condition != null ? condition.failIfEventsMatch().items().stream()
                    .flatMap(item -> item.tags().stream())
                    .map(tag -> tag.key() + "=" + tag.value())
                    .distinct()
                    .toList() : List.of();

            long position = condition != null ? condition.afterCursor().position().value() : 0L;

            stmt.setArray(1, connection.createArrayOf("text", types));
            stmt.setArray(2, connection.createArrayOf("text", tagArrays));
            stmt.setArray(3, connection.createArrayOf("jsonb", dataStrings));
            stmt.setArray(4, connection.createArrayOf("text", eventTypes.toArray(new String[0])));
            stmt.setArray(5, connection.createArrayOf("text", conditionTags.toArray(new String[0])));
            stmt.setLong(6, position);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String jsonResult = rs.getString(1);
                    // Parse JSONB result
                    try {
                        Map<String, Object> result = objectMapper.readValue(jsonResult, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                        });

                        // Check result and throw ConcurrencyException if condition failed
                        if (!(Boolean) result.get("success")) {
                            throw new ConcurrencyException("AppendCondition violated: " + result.get("message"));
                        }

                    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                        throw new RuntimeException("Failed to parse JSONB result", e);
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to append events with condition using connection", e);
        }
    }

    @Override
    public EventStore withConnection(Connection connection) {
        return new ConnectionScopedEventStore(connection);
    }

    @Override
    public <T> T executeInTransaction(Function<EventStore, T> operation) {
        try (Connection connection = dataSource.getConnection()) {
            // Apply configured transaction isolation level
            int isolationLevel = mapIsolationLevel(config.getTransactionIsolation());
            connection.setTransactionIsolation(isolationLevel);
            connection.setAutoCommit(false);

            try {
                EventStore txStore = withConnection(connection);
                T result = operation.apply(txStore);
                connection.commit();
                log.debug("Transaction committed successfully");
                return result;
            } catch (Exception e) {
                try {
                    connection.rollback();
                    log.debug("Transaction rolled back due to error");
                } catch (SQLException rollbackEx) {
                    log.error("Failed to rollback transaction", rollbackEx);
                    // Don't mask the original exception
                }
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to execute transaction", e);
        }
    }

    /**
     * Map transaction isolation level string to JDBC constant.
     */
    private int mapIsolationLevel(String level) {
        return switch (level) {
            case "READ_UNCOMMITTED" -> Connection.TRANSACTION_READ_UNCOMMITTED;
            case "READ_COMMITTED" -> Connection.TRANSACTION_READ_COMMITTED;
            case "REPEATABLE_READ" -> Connection.TRANSACTION_REPEATABLE_READ;
            case "SERIALIZABLE" -> Connection.TRANSACTION_SERIALIZABLE;
            default -> {
                log.warn("Unknown isolation level: {}, using READ_COMMITTED", level);
                yield Connection.TRANSACTION_READ_COMMITTED;
            }
        };
    }

    // Connection-based methods for ConnectionScopedEventStore

    /**
     * Private method to query events using a provided connection.
     * Used internally by ConnectionScopedEventStore.
     */
    private List<StoredEvent> queryWithConnection(Connection connection, Query query, Cursor after) {
        try {
            // Build SQL query directly instead of using the function
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT type, tags, data, transaction_id, position, occurred_at ");
            sql.append("FROM events ");

            List<Object> params = new ArrayList<>();

            // Handle null cursor
            if (after != null) {
                sql.append("WHERE position > ? ");
                params.add(after.position().value());
            }

            // Build OR conditions for each QueryItem (go-crablet style)
            if (query != null && !query.items().isEmpty()) {
                List<String> orConditions = new ArrayList<>();

                for (QueryItem item : query.items()) {
                    StringBuilder condition = new StringBuilder("(");
                    List<String> andConditions = new ArrayList<>();

                    // Handle event types for this QueryItem
                    if (item.hasEventTypes() && !item.eventTypes().isEmpty()) {
                        andConditions.add("type = ANY(?)");
                        params.add(item.eventTypes().toArray(new String[0]));
                    }

                    // Handle tags for this QueryItem
                    if (item.hasTags() && !item.tags().isEmpty()) {
                        List<String> tagStrings = item.tags().stream()
                                .map(tag -> tag.key() + "=" + tag.value())
                                .collect(Collectors.toList());
                        andConditions.add("tags @> ?::TEXT[]");
                        params.add(tagStrings.toArray(new String[0]));
                    }

                    if (!andConditions.isEmpty()) {
                        condition.append(String.join(" AND ", andConditions));
                        condition.append(")");
                        orConditions.add(condition.toString());
                    }
                }

                if (!orConditions.isEmpty()) {
                    if (after != null) {
                        sql.append("AND ");
                    } else {
                        sql.append("WHERE ");
                    }
                    sql.append("(").append(String.join(" OR ", orConditions)).append(")");
                }
            }

            sql.append("ORDER BY position ASC");

            try (PreparedStatement stmt = connection.prepareStatement(sql.toString())) {
                // Set fetch size for memory efficiency
                stmt.setFetchSize(config.getFetchSize());

                for (int i = 0; i < params.size(); i++) {
                    Object param = params.get(i);
                    if (param instanceof String[]) {
                        stmt.setArray(i + 1, connection.createArrayOf("text", (String[]) param));
                    } else {
                        stmt.setObject(i + 1, param);
                    }
                }

                List<StoredEvent> events = new ArrayList<>();
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        StoredEvent event = new StoredEvent(
                                rs.getString("type"),
                                parseTagsFromArray(rs.getArray("tags")),
                                rs.getBytes("data"),
                                rs.getString("transaction_id"),
                                rs.getLong("position"),
                                rs.getTimestamp("occurred_at").toInstant()
                        );
                        events.add(event);
                    }
                }
                return events;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query events using connection", e);
        }
    }

    /**
     * Private method to project state using a provided connection.
     * Used internally by ConnectionScopedEventStore.
     */
    private <T> ProjectionResult<T> projectWithConnection(Connection connection, List<StateProjector<T>> projectors, Cursor after, Class<T> stateType) {
        try {
            // Build query from projectors (DCB spec)
            Query query = buildQueryFromProjectors(projectors);

            // Query events using the connection
            List<StoredEvent> events = queryWithConnection(connection, query, after);

            // Apply projectors
            T state = projectors.isEmpty()
                    ? null
                    : projectors.get(0).getInitialState();

            for (StoredEvent event : events) {
                for (StateProjector<T> projector : projectors) {
                    if (projector.handles(event)) {
                        state = projector.transition(state, event);
                    }
                }
            }

            // Return cursor of last processed event
            Cursor newCursor = events.isEmpty()
                    ? after
                    : Cursor.of(
                    events.get(events.size() - 1).position(),
                    events.get(events.size() - 1).occurredAt(),
                    events.get(events.size() - 1).transactionId()
            );

            return new ProjectionResult<>(state, newCursor);
        } catch (Exception e) {
            throw new RuntimeException("Failed to project state using connection", e);
        }
    }

    /**
     * Private method to project state using a provided connection (generic version).
     * Used internally by ConnectionScopedEventStore.
     */
    @SuppressWarnings("unchecked")
    private ProjectionResult<Map<String, Object>> projectWithConnection(Connection connection, List<StateProjector> projectors, Cursor after) {
        if (projectors == null) {
            throw new IllegalArgumentException("Projectors cannot be null");
        }
        if (after == null) {
            throw new IllegalArgumentException("Cursor cannot be null");
        }

        try {
            // Build query from projectors (DCB spec)
            Query query = buildQueryFromProjectors((List<StateProjector<Map<String, Object>>>) (List<?>) projectors);

            // Query events using the connection
            List<StoredEvent> events = queryWithConnection(connection, query, after);

            // Apply projectors
            Map<String, Object> state = projectors.isEmpty()
                    ? new HashMap<>()
                    : (Map<String, Object>) projectors.get(0).getInitialState();

            for (StoredEvent event : events) {
                for (StateProjector projector : projectors) {
                    if (projector.handles(event)) {
                        state = (Map<String, Object>) projector.transition(state, event);
                    }
                }
            }

            // Return cursor of last processed event
            Cursor newCursor = events.isEmpty()
                    ? after
                    : Cursor.of(
                    events.get(events.size() - 1).position(),
                    events.get(events.size() - 1).occurredAt(),
                    events.get(events.size() - 1).transactionId()
            );

            return new ProjectionResult<>(state, newCursor);
        } catch (Exception e) {
            throw new RuntimeException("Failed to project state using connection", e);
        }
    }

    // Helper methods


    /**
     * Build query from projectors following DCB specification.
     * DCB spec: "queries can be automatically deferred from the decision model definition"
     * See: https://dcb.events/#reading-events
     */
    private <T> Query buildQueryFromProjectors(List<StateProjector<T>> projectors) {
        if (projectors == null || projectors.isEmpty()) {
            return Query.empty();
        }

        List<QueryItem> items = projectors.stream()
                .map(p -> QueryItem.of(p.getEventTypes(), p.getTags()))
                .toList();

        return Query.of(items);
    }

    /**
     * Parse tags from PostgreSQL array.
     */
    private List<Tag> parseTagsFromArray(Array tagArray) throws SQLException {
        if (tagArray == null) {
            return new ArrayList<>();
        }
        String[] tagStrings = (String[]) tagArray.getArray();
        return Arrays.stream(tagStrings)
                .map(tagStr -> {
                    String[] parts = tagStr.split("=", 2);
                    return new Tag(parts[0], parts.length > 1 ? parts[1] : "");
                })
                .collect(Collectors.toList());
    }

    /**
     * Convert tags to PostgreSQL array format.
     * Optimized to use StringBuilder to reduce temporary String object creation.
     *
     * @param tags List of Tag objects to convert
     * @return PostgreSQL array format string like "{key1=value1,key2=value2}"
     */
    private String convertTagsToPostgresArray(List<Tag> tags) {
        if (tags == null || tags.isEmpty()) {
            return "{}";
        }

        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Tag tag : tags) {
            if (tag != null && tag.key() != null && tag.value() != null) {
                if (!first) sb.append(',');
                sb.append(tag.key()).append('=').append(tag.value());
                first = false;
            }
        }
        return sb.append('}').toString();
    }

    /**
     * Parse PostgreSQL tag array into List of Tag objects.
     * Optimized to use indexOf() + substring() instead of split() for 3-5x better performance.
     *
     * @param tagArray Array of tag strings in format "key=value"
     * @return List of parsed Tag objects
     */
    private List<Tag> parseTags(String[] tagArray) {
        List<Tag> tags = new ArrayList<>(tagArray.length);  // Pre-sized to avoid resizing
        for (String tagStr : tagArray) {
            int eqIndex = tagStr.indexOf('=');  // Faster than split()
            if (eqIndex > 0) {
                tags.add(new Tag(
                        tagStr.substring(0, eqIndex),
                        tagStr.substring(eqIndex + 1)
                ));
            } else {
                tags.add(new Tag(tagStr, ""));
            }
        }
        return tags;
    }


    @Override
    public void storeCommand(Command command, String transactionId) {
        try (Connection connection = dataSource.getConnection()) {
            storeCommandWithConnection(connection, command, transactionId);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to store command", e);
        }
    }

    @Override
    public String getCurrentTransactionId() {
        try (Connection connection = dataSource.getConnection()) {
            return getCurrentTransactionIdWithConnection(connection);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get current transaction ID", e);
        }
    }

    /**
     * Store a command using a provided connection.
     * Used internally by ConnectionScopedEventStore.
     */
    private void storeCommandWithConnection(Connection connection, Command command, String transactionId) {
        try {
            // Serialize command to JSONB
            String commandJson = objectMapper.writeValueAsString(command);

            String sql = """
                    INSERT INTO commands (transaction_id, type, data, metadata, occurred_at)
                    VALUES (?::xid8, ?, ?::jsonb, ?::jsonb, CURRENT_TIMESTAMP)
                    """;

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, transactionId);
                stmt.setString(2, command.getCommandType());
                stmt.setString(3, commandJson);

                // Create metadata JSON with command type and wallet ID if available
                String metadataJson = createCommandMetadata(command);
                stmt.setString(4, metadataJson);

                stmt.executeUpdate();
            }
        } catch (SQLException | com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("Failed to store command with connection", e);
        }
    }

    /**
     * Get current transaction ID using a provided connection.
     * Used internally by ConnectionScopedEventStore.
     */
    private String getCurrentTransactionIdWithConnection(Connection connection) {
        try {
            String sql = "SELECT pg_current_xact_id()::TEXT";
            try (PreparedStatement stmt = connection.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
                throw new RuntimeException("Failed to get transaction ID");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get current transaction ID with connection", e);
        }
    }

    /**
     * Create metadata JSON for a command.
     * Uses client-provided metadata if available, otherwise creates minimal metadata.
     */
    private String createCommandMetadata(Command command) {
        // Use client-provided metadata if available
        String clientMetadata = command.getMetadata();
        if (clientMetadata != null && !clientMetadata.trim().isEmpty()) {
            return clientMetadata;
        }

        // Fallback to minimal metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("command_type", command.getCommandType());

        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize command metadata", e);
        }
    }

    // Inner class for connection-scoped EventStore
    private class ConnectionScopedEventStore implements EventStore {
        private final Connection connection;

        public ConnectionScopedEventStore(Connection connection) {
            this.connection = connection;
        }

        @Override
        public List<StoredEvent> query(Query query, Cursor after) {
            // Use the connection for querying
            return JDBCEventStore.this.queryWithConnection(connection, query, after);
        }

        @Override
        public void append(List<AppendEvent> events) {
            JDBCEventStore.this.appendWithConnection(connection, events);
        }

        @Override
        public void appendIf(List<AppendEvent> events, AppendCondition condition) {
            JDBCEventStore.this.appendIfWithConnection(connection, events, condition);
        }

        @Override
        public <T> ProjectionResult<T> project(List<StateProjector<T>> projectors, Cursor after, Class<T> stateType) {
            return JDBCEventStore.this.projectWithConnection(connection, projectors, after, stateType);
        }

        @Override
        public ProjectionResult<Map<String, Object>> project(List<StateProjector> projectors, Cursor after) {
            return JDBCEventStore.this.projectWithConnection(connection, projectors, after);
        }

        @Override
        public EventStore withConnection(Connection connection) {
            return new ConnectionScopedEventStore(connection);
        }

        @Override
        public <T> T executeInTransaction(Function<EventStore, T> operation) {
            // Delegate to parent's implementation
            return JDBCEventStore.this.executeInTransaction(operation);
        }

        @Override
        public void storeCommand(Command command, String transactionId) {
            JDBCEventStore.this.storeCommandWithConnection(connection, command, transactionId);
        }

        @Override
        public String getCurrentTransactionId() {
            return JDBCEventStore.this.getCurrentTransactionIdWithConnection(connection);
        }
    }
}

