package com.crablet.core.impl;

import com.crablet.core.AppendCondition;
import com.crablet.core.AppendEvent;
import com.crablet.core.ClockProvider;
import com.crablet.core.Command;
import com.crablet.core.ConcurrencyException;
import com.crablet.core.Cursor;
import com.crablet.core.DCBViolation;
import com.crablet.core.EventDeserializer;
import com.crablet.core.EventStore;
import com.crablet.core.EventStoreException;
import com.crablet.core.ProjectionResult;
import com.crablet.core.Query;
import com.crablet.core.QueryItem;
import com.crablet.core.QuerySqlBuilder;
import com.crablet.core.StateProjector;
import com.crablet.core.StoredEvent;
import com.crablet.core.Tag;
import com.fasterxml.jackson.core.JsonProcessingException;
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
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * JDBC-based implementation of EventStore using PostgreSQL functions.
 * This implementation uses the existing PostgreSQL schema and functions.
 * 
 * <p><strong>Read/Write Separation:</strong>
 * <ul>
 *   <li>Read operations (project) use read-only connections</li>
 *   <li>Write operations (append, appendIf, storeCommand) use write connections</li>
 *   <li>Transactions (executeInTransaction) use write connections as they may include writes</li>
 * </ul>
 */
@Component
public class EventStoreImpl implements EventStore {

    private static final Logger log = LoggerFactory.getLogger(EventStoreImpl.class);

    /**
     * SQL statements for PostgreSQL functions and queries.
     * Extracted as constants for maintainability and readability.
     */
    private static final String APPEND_EVENTS_BATCH_SQL = 
        "SELECT append_events_batch(?, ?, ?::jsonb[], ?::TIMESTAMP WITH TIME ZONE)";

    private static final String APPEND_EVENTS_IF_SQL = 
        "SELECT append_events_if(?, ?, ?::jsonb[], ?, ?, ?, ?, ?, ?::TIMESTAMP WITH TIME ZONE)";

    private static final String APPEND_EVENTS_IF_CONNECTION_SQL = 
        "SELECT append_events_if(?::text[], ?::text[], ?::jsonb[], ?::text[], ?::text[], ?, ?::text[], ?::text[], ?::TIMESTAMP WITH TIME ZONE)";

    private static final String INSERT_COMMAND_SQL = """
        INSERT INTO commands (transaction_id, type, data, metadata, occurred_at)
        VALUES (?::xid8, ?, ?::jsonb, ?::jsonb, ?::TIMESTAMP WITH TIME ZONE)
        """;

    private static final String GET_CURRENT_TRANSACTION_ID_SQL = 
        "SELECT pg_current_xact_id()::TEXT";

    private final DataSource writeDataSource;
    private final DataSource readDataSource;
    private final ObjectMapper objectMapper;
    private final EventStoreConfig config;
    private final ClockProvider clock;
    private final QuerySqlBuilder sqlBuilder;

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

    /**
     * Singleton EventDeserializer for event deserialization.
     * Stateless implementation reused across all projections.
     */
    private final EventDeserializer eventDeserializer = new EventDeserializerImpl();

    /**
     * Implementation of EventDeserializer.
     * Provides deserialization utilities using the shared ObjectMapper.
     */
    private class EventDeserializerImpl implements EventDeserializer {
        @Override
        public <E> E deserialize(StoredEvent event, Class<E> eventType) {
            try {
                return objectMapper.readValue(event.data(), eventType);
            } catch (java.io.IOException e) {
                throw new RuntimeException("Failed to deserialize event type=" + 
                    event.type() + " to " + eventType.getName(), e);
            }
        }
    }

    @Autowired
    public EventStoreImpl(
            @Qualifier("primaryDataSource") DataSource writeDataSource,
            @Qualifier("readDataSource") DataSource readDataSource,
            ObjectMapper objectMapper,
            EventStoreConfig config,
            ClockProvider clock) {
        if (writeDataSource == null) {
            throw new IllegalArgumentException("writeDataSource must not be null");
        }
        if (readDataSource == null) {
            throw new IllegalArgumentException("readDataSource must not be null");
        }
        if (objectMapper == null) {
            throw new IllegalArgumentException("ObjectMapper must not be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("EventStoreConfig must not be null");
        }
        if (clock == null) {
            throw new IllegalArgumentException("ClockProvider must not be null");
        }
        this.writeDataSource = writeDataSource;
        this.readDataSource = readDataSource;
        this.objectMapper = objectMapper;
        this.config = config;
        this.clock = clock;
        this.sqlBuilder = new QuerySqlBuilderImpl();
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
                    .map(event -> serializeEventData(event.eventData()))
                    .toArray(String[]::new);

            // Call append_events_batch function with JSONB[] cast using raw JDBC
            try (Connection connection = writeDataSource.getConnection();
                 PreparedStatement stmt = connection.prepareStatement(APPEND_EVENTS_BATCH_SQL)) {

                stmt.setArray(1, connection.createArrayOf("varchar", types));
                stmt.setArray(2, connection.createArrayOf("varchar", tagArrays));
                stmt.setArray(3, connection.createArrayOf("jsonb", dataStrings));
                stmt.setTimestamp(4, java.sql.Timestamp.from(clock.now()));

                stmt.execute();
            }
        } catch (SQLException e) {
            // Handle PostgreSQL function errors like go-crablet does
            String sqlState = e.getSQLState();

            // Handle PostgreSQL procedural errors (P0001, etc.)
            if (sqlState != null && sqlState.startsWith("P")) {
                throw new EventStoreException("PostgreSQL procedural error (" + sqlState + "): " + e.getMessage(), e);
            }

            throw new EventStoreException("Failed to append events", e);
        } catch (Exception e) {
            throw new EventStoreException("Failed to append events", e);
        }
    }

    @Override
    public void appendIf(List<AppendEvent> events, AppendCondition condition) {
        if (events.isEmpty()) {
            return;
        }

        try {
            // Extract concurrency check (with cursor)
            List<String> concurrencyTypes = condition.stateChanged().items().stream()
                    .flatMap(item -> item.eventTypes().stream())
                    .distinct()
                    .toList();

            List<String> concurrencyTags = condition.stateChanged().items().stream()
                    .flatMap(item -> item.tags().stream())
                    .map(tag -> tag.key() + "=" + tag.value())
                    .distinct()
                    .toList();

            // Extract idempotency check (no cursor)
            List<String> idempotencyTypes = null;
            List<String> idempotencyTags = null;
            if (condition.alreadyExists() != null) {
                idempotencyTypes = condition.alreadyExists().items().stream()
                        .flatMap(item -> item.eventTypes().stream())
                        .distinct()
                        .toList();
                idempotencyTags = condition.alreadyExists().items().stream()
                        .flatMap(item -> item.tags().stream())
                        .map(tag -> tag.key() + "=" + tag.value())
                        .distinct()
                        .sorted()  // Ensure deterministic order for consistent hash
                        .toList();
            }

            // Prepare event data
            String[] types = events.stream().map(AppendEvent::type).toArray(String[]::new);
            String[] tagArrays = events.stream()
                    .map(event -> convertTagsToPostgresArray(event.tags()))
                    .toArray(String[]::new);
            String[] dataStrings = events.stream()
                    .map(event -> serializeEventData(event.eventData()))
                    .toArray(String[]::new);

            // Call append_events_if with dual conditions
            try (Connection connection = writeDataSource.getConnection();
                 PreparedStatement stmt = connection.prepareStatement(APPEND_EVENTS_IF_SQL)) {

                stmt.setArray(1, connection.createArrayOf("varchar", types));
                stmt.setArray(2, connection.createArrayOf("varchar", tagArrays));
                stmt.setArray(3, connection.createArrayOf("jsonb", dataStrings));
                stmt.setObject(4, concurrencyTypes.isEmpty() ? null : concurrencyTypes.toArray(new String[0]));
                stmt.setObject(5, concurrencyTags.isEmpty() ? null : concurrencyTags.toArray(new String[0]));
                stmt.setObject(6, condition.afterCursor().position().value());
                stmt.setObject(7, idempotencyTypes != null && !idempotencyTypes.isEmpty() ? idempotencyTypes.toArray(new String[0]) : null);
                stmt.setObject(8, idempotencyTags != null && !idempotencyTags.isEmpty() ? idempotencyTags.toArray(new String[0]) : null);
                stmt.setTimestamp(9, java.sql.Timestamp.from(clock.now()));

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
                                    // Parse DCB violation details
                                    String errorCode = (String) result.getOrDefault("error_code", "DCB_VIOLATION");
                                    String message = (String) result.getOrDefault("message", "append condition violated");
                                    
                                    Number matchingCount = (Number) result.get("matching_events_count");
                                    int matchingEventsCount = matchingCount != null ? matchingCount.intValue() : 0;
                                    
                                    // Create DCBViolation with details
                                    DCBViolation violation = new DCBViolation(errorCode, message, matchingEventsCount);
                                    
                                    // Throw with violation details
                                    throw new ConcurrencyException("AppendCondition violated: " + message, violation);
                                }
                            } else {
                                // Handle case where success is not a boolean
                                log.warn("Unexpected success value type: {}", successObj);
                                throw new ConcurrencyException("AppendCondition violated: " + result.get("message"));
                            }
                        } catch (ConcurrencyException e) {
                            // Re-throw ConcurrencyException immediately
                            throw e;
                        } catch (Exception parseException) {
                            // Handle JSON parsing exceptions
                            // Check if it's a recognizable error message before throwing parsing exception
                            if (jsonResult.contains("AppendIf condition failed") || jsonResult.contains("condition failed")) {
                                throw new ConcurrencyException("AppendCondition violated: " + jsonResult);
                            }
                            // Log and rethrow parsing exception
                            log.error("Failed to parse JSONB result: {}", parseException.getMessage());
                            throw new RuntimeException("Failed to parse JSONB result: " + jsonResult, parseException);
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
                    throw new ConcurrencyException("Concurrent modification: " + message);
                }
                // Other P0001 errors from PostgreSQL functions
                throw new ConcurrencyException("PostgreSQL function error: " + message);
            }

            // Handle other PostgreSQL-specific errors
            if (sqlState != null && sqlState.startsWith("P")) {
                throw new EventStoreException("PostgreSQL procedural error (" + sqlState + "): " + e.getMessage(), e);
            }

            throw new EventStoreException("Failed to append events with condition", e);
        } catch (Exception e) {
            // Fallback: check message content for backward compatibility
            if (e.getMessage() != null && e.getMessage().contains("AppendIf condition failed")) {
                throw new ConcurrencyException("Concurrent modification: " + e.getMessage());
            }

            throw new EventStoreException("Failed to append events", e);
        }
    }

    @Override
    public <T> ProjectionResult<T> project(Query query, Cursor after, Class<T> stateType, List<StateProjector<T>> projectors) {
        if (query == null) {
            throw new NullPointerException("Query must not be null");
        }
        if (after == null) {
            throw new NullPointerException("Cursor must not be null");
        }
        if (stateType == null) {
            throw new NullPointerException("State type must not be null");
        }
        if (projectors == null || projectors.isEmpty()) {
            throw new IllegalArgumentException("Projectors must not be null or empty");
        }

        try {
            // Build SQL using existing helper
            StringBuilder sql = new StringBuilder("SELECT type, tags, data, transaction_id, position, occurred_at FROM events");
            List<Object> params = new ArrayList<>();
            String whereClause = sqlBuilder.buildWhereClause(query, after, params);
            if (!whereClause.isEmpty()) {
                sql.append(" WHERE ").append(whereClause);
            }
            sql.append(" ORDER BY transaction_id, position ASC");
            
            // Stream with server-side cursor
            try (Connection connection = readDataSource.getConnection()) {
                connection.setReadOnly(true);  // Read-only operation
                connection.setAutoCommit(false); // Required for server-side cursor
                
                try (PreparedStatement stmt = connection.prepareStatement(
                        sql.toString(),
                        ResultSet.TYPE_FORWARD_ONLY,
                        ResultSet.CONCUR_READ_ONLY)) {
                    
                    stmt.setFetchSize(config.getFetchSize());
                    
                    // Set parameters using existing logic
                    for (int i = 0; i < params.size(); i++) {
                        Object param = params.get(i);
                        if (param instanceof String[]) {
                            stmt.setArray(i + 1, connection.createArrayOf("text", (String[]) param));
                        } else {
                            stmt.setObject(i + 1, param);
                        }
                    }
                    
                    // Stream and project incrementally
                    EventDeserializer deserializer = this.eventDeserializer;
                    T state = projectors.get(0).getInitialState();
                    Cursor lastCursor = after;
                    
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            StoredEvent event = EVENT_ROW_MAPPER.mapRow(rs, 0);
                            
                            // Apply projectors - pass deserializer
                            // Deserialization errors will bubble up, failing the entire projection
                            for (StateProjector<T> projector : projectors) {
                                if (projector.handles(event, deserializer)) {
                                    state = projector.transition(state, event, deserializer);
                                }
                            }
                            
                            // Track cursor
                            lastCursor = Cursor.of(event.position(), event.occurredAt(), event.transactionId());
                        }
                    }
                    
                    connection.commit(); // Commit read-only transaction
                    return ProjectionResult.of(state, lastCursor);
                    
                } catch (Exception e) {
                    connection.rollback();
                    throw e;
                }
            }
        } catch (Exception e) {
            throw new EventStoreException("Failed to project state", e);
        }
    }

    /**
     * Private method to append events using a provided connection.
     * Used internally by ConnectionScopedEventStore.
     */
    private void appendWithConnection(Connection connection, List<AppendEvent> events) {
        if (events.isEmpty()) {
            return;
        }

        try (PreparedStatement stmt = connection.prepareStatement(APPEND_EVENTS_BATCH_SQL)) {

            // Prepare arrays for append_events_batch function
            String[] types = events.stream().map(AppendEvent::type).toArray(String[]::new);
            String[] tagArrays = events.stream()
                    .map(event -> convertTagsToPostgresArray(event.tags()))
                    .toArray(String[]::new);
            String[] dataStrings = events.stream()
                    .map(event -> serializeEventData(event.eventData()))
                    .toArray(String[]::new);

            stmt.setArray(1, connection.createArrayOf("text", types));
            stmt.setArray(2, connection.createArrayOf("text", tagArrays));
            stmt.setArray(3, connection.createArrayOf("jsonb", dataStrings));
            stmt.setTimestamp(4, java.sql.Timestamp.from(clock.now()));

            stmt.execute();
        } catch (SQLException e) {
            throw new EventStoreException("Failed to append events with connection", e);
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

        try (PreparedStatement stmt = connection.prepareStatement(APPEND_EVENTS_IF_CONNECTION_SQL)) {

            // Prepare arrays for append_events_batch_if function
            String[] types = events.stream().map(AppendEvent::type).toArray(String[]::new);
            String[] tagArrays = events.stream()
                    .map(event -> convertTagsToPostgresArray(event.tags()))
                    .toArray(String[]::new);
            String[] dataStrings = events.stream()
                    .map(event -> serializeEventData(event.eventData()))
                    .toArray(String[]::new);

            // Extract concurrency check (with cursor)
            List<String> concurrencyTypes = condition != null ? condition.stateChanged().items().stream()
                    .flatMap(item -> item.eventTypes().stream())
                    .distinct()
                    .toList() : List.of();

            List<String> concurrencyTags = condition != null ? condition.stateChanged().items().stream()
                    .flatMap(item -> item.tags().stream())
                    .map(tag -> tag.key() + "=" + tag.value())
                    .distinct()
                    .toList() : List.of();

            // Extract idempotency check (no cursor)
            List<String> idempotencyTypes = null;
            List<String> idempotencyTags = null;
            if (condition != null && condition.alreadyExists() != null) {
                idempotencyTypes = condition.alreadyExists().items().stream()
                        .flatMap(item -> item.eventTypes().stream())
                        .distinct()
                        .toList();
                idempotencyTags = condition.alreadyExists().items().stream()
                        .flatMap(item -> item.tags().stream())
                        .map(tag -> tag.key() + "=" + tag.value())
                        .distinct()
                        .sorted()  // Ensure deterministic order for consistent hash
                        .toList();
            }

            long position = condition != null ? condition.afterCursor().position().value() : 0L;

            stmt.setArray(1, connection.createArrayOf("text", types));
            stmt.setArray(2, connection.createArrayOf("text", tagArrays));
            stmt.setArray(3, connection.createArrayOf("jsonb", dataStrings));
            stmt.setArray(4, connection.createArrayOf("text", concurrencyTypes.toArray(new String[0])));
            stmt.setArray(5, connection.createArrayOf("text", concurrencyTags.toArray(new String[0])));
            stmt.setLong(6, position);
            stmt.setArray(7, idempotencyTypes != null && !idempotencyTypes.isEmpty() ? connection.createArrayOf("text", idempotencyTypes.toArray(new String[0])) : null);
            stmt.setArray(8, idempotencyTags != null && !idempotencyTags.isEmpty() ? connection.createArrayOf("text", idempotencyTags.toArray(new String[0])) : null);
            stmt.setTimestamp(9, java.sql.Timestamp.from(clock.now()));

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
            throw new EventStoreException("Failed to append events with condition using connection", e);
        }
    }

    /**
     * Create a connection-scoped EventStore for internal transaction management.
     * This is a private implementation detail - not exposed in public API.
     */
    private EventStore createConnectionScopedStore(Connection connection) {
        return new ConnectionScopedEventStore(connection);
    }

    @Override
    public <T> T executeInTransaction(Function<EventStore, T> operation) {
        try (Connection connection = writeDataSource.getConnection()) {
            // Apply configured transaction isolation level
            int isolationLevel = mapIsolationLevel(config.getTransactionIsolation());
            connection.setTransactionIsolation(isolationLevel);
            connection.setAutoCommit(false);

            try {
                EventStore txStore = createConnectionScopedStore(connection);
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
            throw new EventStoreException("Failed to execute transaction", e);
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
                    if (!item.eventTypes().isEmpty()) {
                        andConditions.add("type = ANY(?)");
                        params.add(item.eventTypes().toArray(new String[0]));
                    }

                    // Handle tags for this QueryItem
                    if (!item.tags().isEmpty()) {
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

            sql.append("ORDER BY transaction_id, position ASC");

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
            throw new EventStoreException("Failed to query events using connection", e);
        }
    }

    /**
     * Private method to project state using a provided connection.
     * Used internally by ConnectionScopedEventStore.
     * 
     * <p>Note: Connection is NOT marked read-only since it may be part
     * of a larger transaction that includes writes (via executeInTransaction).
     * 
     * @param connection Existing connection from transaction context
     * @param query The query to filter events
     * @param after Cursor to project events after
     * @param stateType The type of state to project
     * @param projectors List of projectors to apply
     * @return ProjectionResult with final state and cursor
     */
    private <T> ProjectionResult<T> projectWithConnection(Connection connection, Query query, Cursor after, Class<T> stateType, List<StateProjector<T>> projectors) {
        try {
            // Build SQL using existing helper
            StringBuilder sql = new StringBuilder("SELECT type, tags, data, transaction_id, position, occurred_at FROM events");
            List<Object> params = new ArrayList<>();
            String whereClause = sqlBuilder.buildWhereClause(query, after, params);
            if (!whereClause.isEmpty()) {
                sql.append(" WHERE ").append(whereClause);
            }
            sql.append(" ORDER BY transaction_id, position ASC");
            
            // Stream with server-side cursor
            try (PreparedStatement stmt = connection.prepareStatement(
                    sql.toString(),
                    ResultSet.TYPE_FORWARD_ONLY,
                    ResultSet.CONCUR_READ_ONLY)) {
                
                stmt.setFetchSize(config.getFetchSize());
                
                // Set parameters using existing logic
                for (int i = 0; i < params.size(); i++) {
                    Object param = params.get(i);
                    if (param instanceof String[]) {
                        stmt.setArray(i + 1, connection.createArrayOf("text", (String[]) param));
                    } else {
                        stmt.setObject(i + 1, param);
                    }
                }
                
                // Stream and project incrementally
                EventDeserializer deserializer = this.eventDeserializer;
                T state = projectors.get(0).getInitialState();
                Cursor lastCursor = after;
                
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        StoredEvent event = EVENT_ROW_MAPPER.mapRow(rs, 0);
                        
                        // Apply projectors - pass deserializer
                        for (StateProjector<T> projector : projectors) {
                            if (projector.handles(event, deserializer)) {
                                state = projector.transition(state, event, deserializer);
                            }
                        }
                        
                        // Track cursor
                        lastCursor = Cursor.of(event.position(), event.occurredAt(), event.transactionId());
                    }
                }
                
                return ProjectionResult.of(state, lastCursor);
            }
        } catch (Exception e) {
            throw new EventStoreException("Failed to project state using connection", e);
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
     * Serialize event data object to JSON string.
     * Used internally when appending events to convert Object to String.
     * Serializes using the object's actual type to preserve type information.
     * If eventData is already a String or byte[], assume it's already JSON and return it as-is.
     */
    private String serializeEventData(Object eventData) {
        // If already a String, assume it's JSON and return as-is
        if (eventData instanceof String) {
            return (String) eventData;
        }
        
        // If already a byte[], convert to String (assume it's UTF-8 JSON)
        if (eventData instanceof byte[]) {
            return new String((byte[]) eventData, StandardCharsets.UTF_8);
        }
        
        try {
            // Serialize using the concrete type
            // This preserves type information including polymorphism discriminators
            return objectMapper.writerFor(eventData.getClass()).writeValueAsString(eventData);
        } catch (JsonProcessingException e) {
            throw new EventStoreException(
                "Failed to serialize event data: " + eventData.getClass().getName(), 
                e
            );
        }
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
        try (Connection connection = writeDataSource.getConnection()) {
            storeCommandWithConnection(connection, command, transactionId);
        } catch (SQLException e) {
            throw new EventStoreException("Failed to store command", e);
        }
    }

    @Override
    public String getCurrentTransactionId() {
        try (Connection connection = writeDataSource.getConnection()) {
            return getCurrentTransactionIdWithConnection(connection);
        } catch (SQLException e) {
            throw new EventStoreException("Failed to get current transaction ID", e);
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

            try (PreparedStatement stmt = connection.prepareStatement(INSERT_COMMAND_SQL)) {
                stmt.setString(1, transactionId);
                stmt.setString(2, command.getCommandType());
                stmt.setString(3, commandJson);

                // Create metadata JSON with command type and wallet ID if available
                String metadataJson = createCommandMetadata(command);
                stmt.setString(4, metadataJson);
                stmt.setTimestamp(5, java.sql.Timestamp.from(clock.now()));

                stmt.executeUpdate();
            }
        } catch (SQLException | com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new EventStoreException("Failed to store command with connection", e);
        }
    }

    /**
     * Get current transaction ID using a provided connection.
     * Used internally by ConnectionScopedEventStore.
     */
    private String getCurrentTransactionIdWithConnection(Connection connection) {
        try {
            try (PreparedStatement stmt = connection.prepareStatement(GET_CURRENT_TRANSACTION_ID_SQL);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
                throw new RuntimeException("Failed to get transaction ID");
            }
        } catch (SQLException e) {
            throw new EventStoreException("Failed to get current transaction ID with connection", e);
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
            throw new EventStoreException("Failed to serialize command metadata", e);
        }
    }

    // Inner class for connection-scoped EventStore
    private class ConnectionScopedEventStore implements EventStore {
        private final Connection connection;

        public ConnectionScopedEventStore(Connection connection) {
            this.connection = connection;
        }

        @Override
        public void append(List<AppendEvent> events) {
            EventStoreImpl.this.appendWithConnection(connection, events);
        }

        @Override
        public void appendIf(List<AppendEvent> events, AppendCondition condition) {
            EventStoreImpl.this.appendIfWithConnection(connection, events, condition);
        }

        @Override
        public <T> ProjectionResult<T> project(Query query, Cursor after, Class<T> stateType, List<StateProjector<T>> projectors) {
            return EventStoreImpl.this.projectWithConnection(connection, query, after, stateType, projectors);
        }

        @Override
        public <T> T executeInTransaction(Function<EventStore, T> operation) {
            // Delegate to parent's implementation
            return EventStoreImpl.this.executeInTransaction(operation);
        }

        @Override
        public void storeCommand(Command command, String transactionId) {
            EventStoreImpl.this.storeCommandWithConnection(connection, command, transactionId);
        }

        @Override
        public String getCurrentTransactionId() {
            return EventStoreImpl.this.getCurrentTransactionIdWithConnection(connection);
        }
    }
}

