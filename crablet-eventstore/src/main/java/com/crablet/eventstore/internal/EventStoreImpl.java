package com.crablet.eventstore.internal;

import com.crablet.eventstore.AppendCondition;
import com.crablet.eventstore.AppendConditionBuilder;
import com.crablet.eventstore.AppendEvent;
import com.crablet.eventstore.ClockProvider;
import com.crablet.eventstore.CommandAuditStore;
import com.crablet.eventstore.ConcurrencyException;
import com.crablet.eventstore.CorrelationContext;
import com.crablet.eventstore.DCBViolation;
import com.crablet.eventstore.EventStore;
import com.crablet.eventstore.EventStoreConfig;
import com.crablet.eventstore.EventStoreException;
import com.crablet.eventstore.StoredEvent;
import com.crablet.eventstore.StreamPosition;
import com.crablet.eventstore.Tag;
import com.crablet.eventstore.metrics.ConcurrencyViolationMetric;
import com.crablet.eventstore.metrics.EventTypeMetric;
import com.crablet.eventstore.metrics.EventsAppendedMetric;
import com.crablet.eventstore.query.EventDeserializer;
import com.crablet.eventstore.query.ProjectionResult;
import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.query.StateProjector;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.RowMapper;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * JDBC-based implementation of EventStore using PostgreSQL functions.
 * This implementation uses the existing PostgreSQL schema and functions.
 *
 * <p><strong>Read/Write Separation:</strong>
 * <ul>
 *   <li>Read operations (project) use read-only connections</li>
 *   <li>Write operations (appendCommutative, appendNonCommutative, appendIdempotent) use write connections</li>
 *   <li>Command audit writes use the transaction-scoped write connection</li>
 *   <li>Transactions (executeInTransaction) use write connections as they may include writes</li>
 * </ul>
 *
 * <p><strong>Spring Integration:</strong>
 * This class does NOT have @Component annotation to avoid Spring proxying issues with JaCoCo coverage.
 * Users must define an explicit @Bean in their configuration:
 *
 * <pre>{@code
 * @Configuration
 * public class EventStoreConfig {
 *
 *     @Bean
 *     public EventStore eventStore(
 *             WriteDataSource writeDataSource,
 *             ReadDataSource readDataSource,
 *             ObjectMapper objectMapper,
 *             EventStoreConfig config,
 *             ClockProvider clock,
 *             ApplicationEventPublisher eventPublisher) {
 *         return new EventStoreImpl(
 *             writeDataSource.dataSource(),
 *             readDataSource.dataSource(),
 *             objectMapper,
 *             config,
 *             clock,
 *             eventPublisher);
 *     }
 * }
 * }</pre>
 */
public class EventStoreImpl implements EventStore, CommandAuditStore {

    private static final Logger log = LoggerFactory.getLogger(EventStoreImpl.class);

    /**
     * SQL statements for PostgreSQL functions and queries.
     * Extracted as constants for maintainability and readability.
     */
    private static final String APPEND_EVENTS_IF_SQL =
        "SELECT append_events_if(?, ?, ?::jsonb[], ?, ?, ?, ?, ?, ?::TIMESTAMP WITH TIME ZONE, ?::uuid, ?, ?::text, ?::text)";

    private static final String APPEND_EVENTS_IF_CONNECTION_SQL =
        "SELECT append_events_if(?::text[], ?::text[], ?::jsonb[], ?::text[], ?::text[], ?, ?::text[], ?::text[], ?::TIMESTAMP WITH TIME ZONE, ?::uuid, ?, ?::text, ?::text)";

    private static final String STORE_COMMAND_SQL = """
        INSERT INTO crablet_commands (command_id, transaction_id, type, data, metadata, occurred_at)
        VALUES (COALESCE(?::uuid, gen_random_uuid()), pg_current_xact_id(), ?, ?::jsonb, ?::jsonb, ?::TIMESTAMP WITH TIME ZONE)
        ON CONFLICT (command_id) DO NOTHING
        """;

    private final DataSource writeDataSource;
    private final DataSource readDataSource;
    private final ObjectMapper objectMapper;
    private final EventStoreConfig config;
    private final ClockProvider clock;
    private final QuerySqlBuilder sqlBuilder;
    private final ApplicationEventPublisher eventPublisher;
    private final @Nullable String notifyChannel;

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

        UUID correlationId = rs.getObject("correlation_id", UUID.class);
        Long causationId   = (Long) rs.getObject("causation_id");
        return new StoredEvent(type, tags, data, transactionId, position, occurredAt,
                               correlationId, causationId);
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
                return Objects.requireNonNull(objectMapper.readValue(event.data(), eventType));
            } catch (Exception e) {
                throw new RuntimeException("Failed to deserialize event type=" +
                    event.type() + " to " + eventType.getName(), e);
            }
        }
    }

    /**
     * Creates a new EventStoreImpl.
     *
     * @param writeDataSource data source for write operations
     * @param readDataSource data source for read operations
     * @param objectMapper Jackson object mapper for JSON serialization
     * @param config event store configuration
     * @param clock clock provider for timestamps
     * @param eventPublisher event publisher for metrics (required).
     *                       Spring Boot automatically provides an ApplicationEventPublisher bean.
     *                       See crablet-metrics-micrometer for automatic metrics collection.
     */
    public EventStoreImpl(
            DataSource writeDataSource,
            DataSource readDataSource,
            ObjectMapper objectMapper,
            EventStoreConfig config,
            ClockProvider clock,
            ApplicationEventPublisher eventPublisher) {
        this(writeDataSource, readDataSource, objectMapper, config, clock, eventPublisher, null);
    }

    public EventStoreImpl(
            DataSource writeDataSource,
            DataSource readDataSource,
            ObjectMapper objectMapper,
            EventStoreConfig config,
            ClockProvider clock,
            ApplicationEventPublisher eventPublisher,
            @Nullable String notifyChannel) {
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
        if (eventPublisher == null) {
            throw new IllegalArgumentException("eventPublisher must not be null");
        }
        this.writeDataSource = writeDataSource;
        this.readDataSource = readDataSource;
        this.objectMapper = objectMapper;
        this.config = config;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
        this.notifyChannel = notifyChannel;
        this.sqlBuilder = new QuerySqlBuilderImpl();
    }

    @Override
    public String appendCommutative(List<AppendEvent> events) {
        return appendIf(events, AppendCondition.empty());
    }

    @Override
    public String appendNonCommutative(
            List<AppendEvent> events, Query decisionModel, StreamPosition streamPosition) {
        return appendIf(events, AppendConditionBuilder.of(decisionModel, streamPosition).build());
    }

    @Override
    public String appendIdempotent(
            List<AppendEvent> events,
            String eventType,
            String tagKey,
            String tagValue) {
        return appendIf(events, AppendCondition.idempotent(eventType, tagKey, tagValue));
    }

    @Override
    public String appendIdempotent(List<AppendEvent> events, Query idempotencyQuery) {
        return appendIf(events, AppendCondition.idempotent(idempotencyQuery));
    }

    @Override
    public String appendConditional(List<AppendEvent> events, AppendCondition condition) {
        return appendIf(events, condition);
    }

    String appendIf(List<AppendEvent> events, AppendCondition condition) {
        if (events.isEmpty()) {
            throw new IllegalArgumentException("Cannot append empty events list");
        }

        try {
            // Extract concurrency check (with stream position)
            List<String> concurrencyTypes = condition.concurrencyQuery().items().stream()
                    .flatMap(item -> item.eventTypes().stream())
                    .distinct()
                    .toList();

            List<String> concurrencyTags = condition.concurrencyQuery().items().stream()
                    .flatMap(item -> item.tags().stream())
                    .map(tag -> tag.key() + "=" + tag.value())
                    .distinct()
                    .toList();

            // Extract idempotency check (no stream position)
            List<String> idempotencyTypes = null;
            List<String> idempotencyTags = null;
            if (!condition.idempotencyQuery().items().isEmpty()) {
                idempotencyTypes = condition.idempotencyQuery().items().stream()
                        .flatMap(item -> item.eventTypes().stream())
                        .distinct()
                        .toList();
                idempotencyTags = condition.idempotencyQuery().items().stream()
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
            String notifyPayload = encodeNotifyPayload(events);

            // Determine stream position: NULL for empty conditions (no concurrency check), actual value otherwise
            Long afterPosition = null;
            if (!concurrencyTypes.isEmpty() || !concurrencyTags.isEmpty()) {
                // Only use stream position if we're actually doing a concurrency check
                afterPosition = condition.afterPosition().position();
            }

            // Call append_events_if with dual conditions
            try (Connection connection = writeDataSource.getConnection();
                 PreparedStatement stmt = connection.prepareStatement(APPEND_EVENTS_IF_SQL)) {

                stmt.setArray(1, connection.createArrayOf("varchar", types));
                stmt.setArray(2, connection.createArrayOf("varchar", tagArrays));
                stmt.setArray(3, connection.createArrayOf("jsonb", dataStrings));
                stmt.setObject(4, concurrencyTypes.isEmpty() ? null : concurrencyTypes.toArray(new String[0]));
                stmt.setObject(5, concurrencyTags.isEmpty() ? null : concurrencyTags.toArray(new String[0]));
                stmt.setObject(6, afterPosition);
                stmt.setObject(7, idempotencyTypes != null && !idempotencyTypes.isEmpty() ? idempotencyTypes.toArray(new String[0]) : null);
                stmt.setObject(8, idempotencyTags != null && !idempotencyTags.isEmpty() ? idempotencyTags.toArray(new String[0]) : null);
                stmt.setTimestamp(9, Timestamp.from(clock.now()));
                stmt.setObject(10, CorrelationContext.correlationId());
                stmt.setObject(11, CorrelationContext.causationId());
                stmt.setString(12, notifyChannel);
                stmt.setString(13, notifyPayload);

                try (ResultSet rs = stmt.executeQuery()) {
                    // Fail fast: Check if we have a result
                    if (!rs.next()) {
                        throw new RuntimeException("No result from append_events_if");
                    }

                    String jsonResult = rs.getString(1);
                    String transactionId = parseAppendResult(jsonResult, events);

                    // Publish metrics events after successful append
                    eventPublisher.publishEvent(new EventsAppendedMetric(events.size()));
                    for (AppendEvent event : events) {
                        eventPublisher.publishEvent(new EventTypeMetric(event.type()));
                    }

                    return transactionId;
                }
            }

        } catch (ConcurrencyException e) {
            // Publish concurrency violation metric
            eventPublisher.publishEvent(new ConcurrencyViolationMetric());
            throw e;
        } catch (SQLException e) {
            throw handleSQLException(e);
        } catch (Exception e) {
            throw handleGenericException(e);
        }
    }

    @Override
    public <T> ProjectionResult<T> project(
            Query query, StreamPosition after, Class<T> stateType, List<StateProjector<T>> projectors) {
        if (projectors.isEmpty()) {
            throw new IllegalArgumentException("Projectors must not be empty");
        }

        try {
            // Build SQL using existing helper
            StringBuilder sql = new StringBuilder("SELECT type, tags, data, transaction_id, position, occurred_at, correlation_id, causation_id FROM crablet_events");
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
                        if (param instanceof String[] strings) {
                            stmt.setArray(i + 1, connection.createArrayOf("text", strings));
                        } else {
                            stmt.setObject(i + 1, param);
                        }
                    }

                    // Stream and project incrementally
                    EventDeserializer deserializer = this.eventDeserializer;
                    T state = projectors.get(0).getInitialState();
                    StreamPosition lastStreamPosition = after;

                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            StoredEvent event = EVENT_ROW_MAPPER.mapRow(rs, 0);

                            // Apply projectors - pass deserializer
                            // Deserialization errors will bubble up, failing the entire projection
                            for (StateProjector<T> projector : projectors) {
                                if (handlesEventType(projector, event)) {
                                    state = projector.transition(state, event, deserializer);
                                }
                            }

                            // Track stream position
                            lastStreamPosition = StreamPosition.of(event.position(), event.occurredAt(), event.transactionId());
                        }
                    }

                    connection.commit(); // Commit read-only transaction
                    return ProjectionResult.of(state, lastStreamPosition);

                } catch (Exception e) {
                    connection.rollback();
                    throw e;
                }
            }
        } catch (Exception e) {
            throw new EventStoreException("Failed to project state", e);
        }
    }

    @Override
    public boolean exists(Query query) {
        try (Connection connection = readDataSource.getConnection()) {
            connection.setReadOnly(true);
            return existsWithConnection(connection, query);
        } catch (EventStoreException e) {
            throw e;
        } catch (Exception e) {
            throw new EventStoreException("Failed to check event existence", e);
        }
    }

    /**
     * Private method to append events conditionally using a provided connection.
     * Used internally by ConnectionScopedEventStore.
     */
    private String appendIfWithConnection(Connection connection, List<AppendEvent> events, AppendCondition condition) {
        if (events.isEmpty()) {
            throw new IllegalArgumentException("Cannot append empty events list");
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
            String notifyPayload = encodeNotifyPayload(events);

            // Extract concurrency check (with stream position)
            List<String> concurrencyTypes = condition != null ? condition.concurrencyQuery().items().stream()
                    .flatMap(item -> item.eventTypes().stream())
                    .distinct()
                    .toList() : List.of();

            List<String> concurrencyTags = condition != null ? condition.concurrencyQuery().items().stream()
                    .flatMap(item -> item.tags().stream())
                    .map(tag -> tag.key() + "=" + tag.value())
                    .distinct()
                    .toList() : List.of();

            // Extract idempotency check (no stream position)
            List<String> idempotencyTypes = null;
            List<String> idempotencyTags = null;
            if (condition != null && !condition.idempotencyQuery().items().isEmpty()) {
                idempotencyTypes = condition.idempotencyQuery().items().stream()
                        .flatMap(item -> item.eventTypes().stream())
                        .distinct()
                        .toList();
                idempotencyTags = condition.idempotencyQuery().items().stream()
                        .flatMap(item -> item.tags().stream())
                        .map(tag -> tag.key() + "=" + tag.value())
                        .distinct()
                        .sorted()  // Ensure deterministic order for consistent hash
                        .toList();
            }

            // Determine stream position: NULL for empty conditions (no concurrency check), actual value otherwise
            Long position = null;
            if (condition != null && (!concurrencyTypes.isEmpty() || !concurrencyTags.isEmpty())) {
                // Only use stream position if we're actually doing a concurrency check
                position = condition.afterPosition().position();
            }

            stmt.setArray(1, connection.createArrayOf("text", types));
            stmt.setArray(2, connection.createArrayOf("text", tagArrays));
            stmt.setArray(3, connection.createArrayOf("jsonb", dataStrings));
            stmt.setArray(4, connection.createArrayOf("text", concurrencyTypes.toArray(new String[0])));
            stmt.setArray(5, connection.createArrayOf("text", concurrencyTags.toArray(new String[0])));
            stmt.setObject(6, position);
            stmt.setArray(7, idempotencyTypes != null && !idempotencyTypes.isEmpty() ? connection.createArrayOf("text", idempotencyTypes.toArray(new String[0])) : null);
            stmt.setArray(8, idempotencyTags != null && !idempotencyTags.isEmpty() ? connection.createArrayOf("text", idempotencyTags.toArray(new String[0])) : null);
            stmt.setTimestamp(9, Timestamp.from(clock.now()));
            stmt.setObject(10, CorrelationContext.correlationId());
            stmt.setObject(11, CorrelationContext.causationId());
            stmt.setString(12, notifyChannel);
            stmt.setString(13, notifyPayload);

            try (ResultSet rs = stmt.executeQuery()) {
                // Fail fast: Check if we have a result
                if (!rs.next()) {
                    throw new RuntimeException("No result from append_events_if");
                }

                String jsonResult = rs.getString(1);
                return parseAppendResult(jsonResult, events);
            }
        } catch (ConcurrencyException e) {
            eventPublisher.publishEvent(new ConcurrencyViolationMetric());
            throw e;
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
     * Private method to project state using a provided connection.
     * Used internally by ConnectionScopedEventStore.
     *
     * <p>Note: Connection is NOT marked read-only since it may be part
     * of a larger transaction that includes writes (via executeInTransaction).
     *
     * @param connection Existing connection from transaction context
     * @param query The query to filter events
     * @param after StreamPosition to project events after
     * @param projectors List of projectors to apply
     * @return ProjectionResult with final state and stream position
     */
    private <T> ProjectionResult<T> projectWithConnection(
            Connection connection, Query query, StreamPosition after, List<StateProjector<T>> projectors) {
        try {
            // Build SQL using existing helper
            StringBuilder sql = new StringBuilder("SELECT type, tags, data, transaction_id, position, occurred_at, correlation_id, causation_id FROM crablet_events");
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
                    if (param instanceof String[] strings) {
                        stmt.setArray(i + 1, connection.createArrayOf("text", strings));
                    } else {
                        stmt.setObject(i + 1, param);
                    }
                }

                // Stream and project incrementally
                EventDeserializer deserializer = this.eventDeserializer;
                T state = projectors.get(0).getInitialState();
                StreamPosition lastStreamPosition = after;

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        StoredEvent event = EVENT_ROW_MAPPER.mapRow(rs, 0);

                        // Apply projectors - pass deserializer
                        for (StateProjector<T> projector : projectors) {
                            if (handlesEventType(projector, event)) {
                                state = projector.transition(state, event, deserializer);
                            }
                        }

                        // Track stream position
                        lastStreamPosition = StreamPosition.of(event.position(), event.occurredAt(), event.transactionId());
                    }
                }

                return ProjectionResult.of(state, lastStreamPosition);
            }
        } catch (Exception e) {
            throw new EventStoreException("Failed to project state using connection", e);
        }
    }

    private boolean existsWithConnection(Connection connection, Query query) {
        try {
            List<Object> params = new ArrayList<>();
            String whereClause = sqlBuilder.buildWhereClause(query, null, params);
            StringBuilder sql = new StringBuilder("SELECT EXISTS(SELECT 1 FROM crablet_events");
            if (!whereClause.isEmpty()) {
                sql.append(" WHERE ").append(whereClause);
            }
            sql.append(") AS result");

            try (PreparedStatement stmt = connection.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    Object param = params.get(i);
                    if (param instanceof String[] strings) {
                        stmt.setArray(i + 1, connection.createArrayOf("text", strings));
                    } else {
                        stmt.setObject(i + 1, param);
                    }
                }
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next() && rs.getBoolean("result");
                }
            }
        } catch (Exception e) {
            throw new EventStoreException("Failed to check event existence", e);
        }
    }

    // Helper methods



    /**
     * Check if projector handles this event type.
     */
    private <T> boolean handlesEventType(StateProjector<T> projector, StoredEvent event) {
        List<String> eventTypes = projector.getEventTypes();
        return eventTypes.isEmpty() || eventTypes.contains(event.type());
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
        if (eventData instanceof String s) {
            return s;
        }

        // If already a byte[], convert to String (assume it's UTF-8 JSON)
        if (eventData instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }

        try {
            // Serialize using the concrete type
            // This preserves type information including polymorphism discriminators
            return objectMapper.writerFor(eventData.getClass()).writeValueAsString(eventData);
        } catch (JacksonException e) {
            throw new EventStoreException(
                "Failed to serialize event data: " + eventData.getClass().getName(),
                e
            );
        }
    }

    /**
     * Parse the JSON result from append_events_if PostgreSQL function.
     * Uses fail-fast principles to validate and extract transaction ID.
     *
     * @param jsonResult The JSON string result from PostgreSQL function
     * @param events The events being appended (null for appendIfWithConnection, used for metrics)
     * @return The transaction ID from the result
     * @throws ConcurrencyException if the append condition was violated
     * @throws EventStoreException if the result is invalid
     */
    private String parseAppendResult(String jsonResult, @Nullable List<AppendEvent> events) {
        // Fail fast: Validate result exists
        if (jsonResult == null || jsonResult.trim().isEmpty()) {
            throw new ConcurrencyException("AppendIf condition failed: no result");
        }

        // Try to parse as JSON
        try {
            Map<String, Object> result = objectMapper.readValue(jsonResult,
                new tools.jackson.core.type.TypeReference<Map<String, Object>>() {});

            // Fail fast: Validate success is a boolean
            Object successObj = result.get("success");
            if (!(successObj instanceof Boolean success)) {
                log.warn("Unexpected success value type: {}", successObj);
                throw new ConcurrencyException("AppendCondition violated: " + result.get("message"));
            }

            // Fail fast: Check if condition failed
            if (!success) {
                // Parse DCB violation details (only in appendIf, not appendIfWithConnection)
                if (events != null) {
                    String errorCode = (String) result.getOrDefault("error_code", "DCB_VIOLATION");
                    String message = (String) result.getOrDefault("message", "append condition violated");

                    Number matchingCount = (Number) result.get("matching_events_count");
                    int matchingEventsCount = matchingCount != null ? matchingCount.intValue() : 0;

                    DCBViolation violation = new DCBViolation(errorCode, message, matchingEventsCount);
                    throw new ConcurrencyException("AppendCondition violated: " + message, violation);
                } else {
                    throw new ConcurrencyException("AppendCondition violated: " + result.get("message"));
                }
            }

            // Success - extract and return transaction ID
            String transactionId = (String) result.get("transaction_id");

            // Fail fast: Validate transaction ID exists
            if (transactionId == null) {
                log.error("PostgreSQL function returned success but no transaction_id - this should not happen");
                throw new EventStoreException("PostgreSQL function did not return transaction_id");
            }

            return transactionId;

        } catch (ConcurrencyException e) {
            // Re-throw ConcurrencyException immediately
            throw e;
        } catch (JacksonException e) {
            // Fail fast: Check if it's a recognizable error message
            if (jsonResult.contains("AppendIf condition failed") || jsonResult.contains("condition failed")) {
                throw new ConcurrencyException("AppendCondition violated: " + jsonResult);
            }

            // Log and rethrow parsing exception
            log.error("Failed to parse JSONB result: {}", e.getMessage());
            throw new RuntimeException("Failed to parse JSONB result: " + jsonResult, e);
        }
    }

    /**
     * Handle SQLException from append operations with fail-fast error handling.
     */
    private RuntimeException handleSQLException(SQLException e) {
        String sqlState = e.getSQLState();

        // Fail fast: Handle PostgreSQL RAISE EXCEPTION (P0001)
        if ("P0001".equals(sqlState)) {
            String message = e.getMessage();
            if (message != null && message.contains("AppendIf condition failed")) {
                return new ConcurrencyException("Concurrent modification: " + message);
            }
            return new ConcurrencyException("PostgreSQL function error: " + message);
        }

        // Fail fast: Handle other PostgreSQL-specific errors
        if (sqlState != null && sqlState.startsWith("P")) {
            return new EventStoreException("PostgreSQL procedural error (" + sqlState + "): " + e.getMessage(), e);
        }

        return new EventStoreException("Failed to append events with condition", e);
    }

    /**
     * Handle generic Exception from append operations with fail-fast error handling.
     */
    private RuntimeException handleGenericException(Exception e) {
        // Fail fast: Check for AppendIf condition failure
        if (e.getMessage() != null && e.getMessage().contains("AppendIf condition failed")) {
            return new ConcurrencyException("Concurrent modification: " + e.getMessage());
        }

        return new EventStoreException("Failed to append events", e);
    }

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

    private @Nullable String encodeNotifyPayload(List<AppendEvent> events) {
        if (notifyChannel == null) {
            return null;
        }
        Set<String> eventTypes = new HashSet<>();
        for (AppendEvent event : events) {
            eventTypes.add(event.type());
        }
        return PostgresNotifyPayload.encodePayload(eventTypes, collectTagKeys(events));
    }

    private static Set<String> collectTagKeys(List<AppendEvent> events) {
        Set<String> keys = new HashSet<>();
        for (AppendEvent e : events) {
            if (e.tags() == null) {
                continue;
            }
            for (Tag t : e.tags()) {
                if (t.key() != null) {
                    keys.add(t.key());
                }
            }
        }
        return keys;
    }


    @Override
    public boolean storeCommand(String commandJson, String commandType, Instant occurredAt) {
        try (Connection connection = writeDataSource.getConnection()) {
            return storeCommandWithConnection(connection, commandJson, commandType, null, occurredAt);
        } catch (SQLException e) {
            throw new EventStoreException("Failed to store command", e);
        }
    }

    @Override
    public boolean storeCommandIfAbsent(String commandJson, String commandType, UUID commandId, Instant occurredAt) {
        try (Connection connection = writeDataSource.getConnection()) {
            return storeCommandWithConnection(connection, commandJson, commandType, commandId, occurredAt);
        } catch (SQLException e) {
            throw new EventStoreException("Failed to store command", e);
        }
    }

    private boolean storeCommandWithConnection(Connection connection, String commandJson,
                                               String commandType, @Nullable UUID commandId,
                                               Instant occurredAt) {
        try (PreparedStatement stmt = connection.prepareStatement(STORE_COMMAND_SQL)) {
            stmt.setObject(1, commandId);
            stmt.setString(2, commandType);
            stmt.setString(3, commandJson);
            stmt.setString(4, createCommandMetadata(commandType));
            stmt.setTimestamp(5, Timestamp.from(occurredAt));
            return stmt.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new EventStoreException("Failed to store command", e);
        }
    }

    /**
     * Create metadata JSON for a command.
     * Stores minimal metadata (command type only).
     */
    private String createCommandMetadata(@Nullable String commandType) {
        // Store minimal metadata (command type only)
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("command_type", commandType);

        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (tools.jackson.core.JacksonException e) {
            throw new EventStoreException("Failed to serialize command metadata", e);
        }
    }

    // Inner class for connection-scoped EventStore
    private class ConnectionScopedEventStore implements EventStore, CommandAuditStore {
        private final Connection connection;

        private ConnectionScopedEventStore(Connection connection) {
            this.connection = connection;
        }

        @Override
        public String appendCommutative(List<AppendEvent> events) {
            return EventStoreImpl.this.appendIfWithConnection(connection, events, AppendCondition.empty());
        }

        @Override
        public String appendNonCommutative(
                List<AppendEvent> events, Query decisionModel, StreamPosition streamPosition) {
            return EventStoreImpl.this.appendIfWithConnection(connection, events, AppendConditionBuilder.of(decisionModel, streamPosition).build());
        }

        @Override
        public String appendIdempotent(
                List<AppendEvent> events,
                String eventType,
                String tagKey,
                String tagValue) {
            return EventStoreImpl.this.appendIfWithConnection(connection, events, AppendCondition.idempotent(eventType, tagKey, tagValue));
        }

        @Override
        public String appendIdempotent(List<AppendEvent> events, Query idempotencyQuery) {
            return EventStoreImpl.this.appendIfWithConnection(connection, events, AppendCondition.idempotent(idempotencyQuery));
        }

        @Override
        public String appendConditional(List<AppendEvent> events, AppendCondition condition) {
            return EventStoreImpl.this.appendIfWithConnection(connection, events, condition);
        }

        @Override
        public <T> ProjectionResult<T> project(
                Query query, StreamPosition after, Class<T> stateType, List<StateProjector<T>> projectors) {
            return EventStoreImpl.this.projectWithConnection(connection, query, after, projectors);
        }

        @Override
        public boolean exists(Query query) {
            return EventStoreImpl.this.existsWithConnection(connection, query);
        }

        @Override
        public <T> T executeInTransaction(Function<EventStore, T> operation) {
            // Delegate to parent's implementation
            return EventStoreImpl.this.executeInTransaction(operation);
        }

        @Override
        public boolean storeCommand(String commandJson, String commandType, Instant occurredAt) {
            return EventStoreImpl.this.storeCommandWithConnection(
                    connection, commandJson, commandType, null, occurredAt);
        }

        @Override
        public boolean storeCommandIfAbsent(String commandJson, String commandType,
                                            UUID commandId, Instant occurredAt) {
            return EventStoreImpl.this.storeCommandWithConnection(
                    connection, commandJson, commandType, commandId, occurredAt);
        }

    }
}
