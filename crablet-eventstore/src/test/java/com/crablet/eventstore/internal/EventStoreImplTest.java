package com.crablet.eventstore.internal;

import com.crablet.eventstore.AppendEvent;
import com.crablet.eventstore.CommandAuditStore;
import com.crablet.eventstore.EventStore;
import com.crablet.eventstore.EventStoreConfig;
import com.crablet.eventstore.EventStoreException;
import com.crablet.eventstore.StoredEvent;
import com.crablet.eventstore.StreamPosition;
import com.crablet.eventstore.metrics.EventTypeMetric;
import com.crablet.eventstore.metrics.EventsAppendedMetric;
import com.crablet.eventstore.query.EventDeserializer;
import com.crablet.eventstore.query.EventRepository;
import com.crablet.eventstore.query.ProjectionResult;
import com.crablet.eventstore.query.Query;
import com.crablet.eventstore.query.StateProjector;
import com.crablet.test.config.CrabletFlywayMigration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.context.ApplicationEventPublisher;
import org.testcontainers.containers.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Direct unit test for EventStoreImpl without Spring framework.
 * This test verifies that JaCoCo can properly instrument EventStoreImpl
 * by instantiating it directly without any Spring proxies or component scanning.
 */
class EventStoreImplTest {

    private static PostgreSQLContainer<?> postgres;
    private static DataSource dataSource;
    private EventStoreImpl eventStoreImpl;
    private EventStore eventStore;
    private ObjectMapper objectMapper;
    private ClockProviderImpl clockProvider;
    private ApplicationEventPublisher eventPublisher;

    @BeforeAll
    static void startDatabase() {
        postgres = new PostgreSQLContainer<>("postgres:17.2")
                .withDatabaseName("crablet_test")
                .withUsername("test")
                .withPassword("test");
        postgres.start();

        // Create DataSource
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(postgres.getJdbcUrl());
        ds.setUser(postgres.getUsername());
        ds.setPassword(postgres.getPassword());
        dataSource = ds;

        CrabletFlywayMigration.migrate(dataSource);
    }

    @AfterAll
    static void stopDatabase() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    @BeforeEach
    void setUp() {
        // Create ObjectMapper with Java Time support
        objectMapper = JsonMapper.builder().build();

        // Create EventStoreConfig with defaults
        EventStoreConfig config = new EventStoreConfig();
        config.setPersistCommands(false);
        config.setTransactionIsolation("READ_COMMITTED");
        config.setFetchSize(1000);

        // Create ClockProvider
        clockProvider = new ClockProviderImpl();
        clockProvider.setClock(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

        // Directly instantiate EventStoreImpl - NO SPRING INVOLVEMENT
        eventPublisher = mock(ApplicationEventPublisher.class);
        eventStoreImpl = new EventStoreImpl(
                dataSource,      // writeDataSource
                dataSource,      // readDataSource
                objectMapper,
                config,
                clockProvider,
                eventPublisher
        );
        eventStore = eventStoreImpl;
    }

    @Test
    void transactionPublishesAppendMetricsOnlyAfterCommit() {
        eventStore.executeInTransaction(txStore -> {
            txStore.appendCommutative(List.of(typedAppendEvent("CommittedMetricEvent")));
            verify(eventPublisher, never()).publishEvent(Mockito.any());
            return true;
        });

        verify(eventPublisher).publishEvent(new EventsAppendedMetric(1));
        verify(eventPublisher).publishEvent(new EventTypeMetric("CommittedMetricEvent"));
    }

    @Test
    void transactionDoesNotPublishAppendMetricsAfterRollback() {
        assertThrows(IllegalStateException.class, () -> eventStore.executeInTransaction(txStore -> {
            txStore.appendCommutative(List.of(typedAppendEvent("RolledBackMetricEvent")));
            throw new IllegalStateException("force rollback");
        }));

        verify(eventPublisher, never()).publishEvent(Mockito.any());
    }
    
    // Simple test event record
    record TestEvent(String id, String message, Instant timestamp) {}

    private AppendEvent appendEvent(String id, String message) {
        return AppendEvent.builder("TestEvent")
                .tag("test_id", id)
                .tag("category", "test")
                .data(new TestEvent(id, message, clockProvider.now()))
                .build();
    }

    private AppendEvent typedAppendEvent(String type) {
        String id = UUID.randomUUID().toString();
        return AppendEvent.builder(type)
                .tag("test_id", id)
                .tag("category", "test")
                .data(new TestEvent(id, type, clockProvider.now()))
                .build();
    }

    @Test
    @SuppressWarnings("NullAway")
    void appendCommutative_acceptsNullTagsOnEvent() {
        String testId = UUID.randomUUID().toString();
        AppendEvent event = new AppendEvent(
                "TestEvent",
                null,
                "{\"id\":\"" + testId + "\",\"message\":\"n\",\"timestamp\":\"2026-01-01T00:00:00Z\"}");

        assertThatCode(() -> eventStore.appendCommutative(List.of(event))).doesNotThrowAnyException();
    }

    @Test
    @SuppressWarnings("NullAway") // intentionally passing null to verify constructor validation
    void shouldRejectInvalidConstructorArguments() {
        EventStoreConfig config = new EventStoreConfig();
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

        assertThrows(IllegalArgumentException.class,
                () -> new EventStoreImpl(null, dataSource, objectMapper, config, clockProvider, eventPublisher));
        assertThrows(IllegalArgumentException.class,
                () -> new EventStoreImpl(dataSource, null, objectMapper, config, clockProvider, eventPublisher));
        assertThrows(IllegalArgumentException.class,
                () -> new EventStoreImpl(dataSource, dataSource, null, config, clockProvider, eventPublisher));
        assertThrows(IllegalArgumentException.class,
                () -> new EventStoreImpl(dataSource, dataSource, objectMapper, null, clockProvider, eventPublisher));
        assertThrows(IllegalArgumentException.class,
                () -> new EventStoreImpl(dataSource, dataSource, objectMapper, config, null, eventPublisher));
        assertThrows(IllegalArgumentException.class,
                () -> new EventStoreImpl(dataSource, dataSource, objectMapper, config, clockProvider, null));
    }

    @Test
    void shouldAppendEventSuccessfully() {
        // Given
        String testId = UUID.randomUUID().toString();
        AppendEvent appendEvent = appendEvent(testId, "Test message");

        // When - This call should be tracked by JaCoCo
        String transactionId = eventStore.appendCommutative(List.of(appendEvent));

        // Then - Event was persisted successfully and transaction ID returned
        assertNotNull(transactionId);
        assertFalse(transactionId.isEmpty());
    }

    @Test
    void appendCommutativeWithNotifyChannelDeliversPgNotifyFromAppendFunction() throws Exception {
        EventStore notifyingStore = new EventStoreImpl(
                dataSource, dataSource, objectMapper, newConfig(),
                clockProvider, mock(ApplicationEventPublisher.class), "crablet_events");

        try (Connection listenConn = dataSource.getConnection()) {
            listen(listenConn, "crablet_events");

            notifyingStore.appendCommutative(List.of(appendEvent(UUID.randomUUID().toString(), "notify")));

            PGNotification[] notifications = notifications(listenConn, 5000);
            assertThat(notifications)
                    .as("expected notification from append_events_if")
                    .isNotNull()
                    .isNotEmpty();
            assertThat(notifications[0].getName()).isEqualTo("crablet_events");
            assertThat(notifications[0].getParameter()).contains("TestEvent");
            assertThat(notifications[0].getParameter()).contains("test_id");
        }
    }

    @Test
    void appendCommutativeWithoutNotifyChannelDoesNotInvokePgNotify() throws Exception {
        try (Connection listenConn = dataSource.getConnection()) {
            listen(listenConn, "crablet_events");

            eventStore.appendCommutative(List.of(appendEvent(UUID.randomUUID().toString(), "no-notify")));

            assertThat(notifications(listenConn, 500)).isNullOrEmpty();
        }
    }

    @Test
    void rolledBackTransactionDoesNotDeliverPgNotify() throws Exception {
        EventStore notifyingStore = new EventStoreImpl(
                dataSource, dataSource, objectMapper, newConfig(),
                clockProvider, mock(ApplicationEventPublisher.class), "crablet_events");

        try (Connection listenConn = dataSource.getConnection()) {
            listen(listenConn, "crablet_events");

            assertThrows(IllegalStateException.class, () -> notifyingStore.executeInTransaction(txEventStore -> {
                txEventStore.appendCommutative(List.of(appendEvent(UUID.randomUUID().toString(), "rollback-notify")));
                throw new IllegalStateException("rollback");
            }));

            assertThat(notifications(listenConn, 500)).isNullOrEmpty();
        }
    }

    @Test
    void multipleAppendsInCommittedTransactionDeliverOneNotificationPerDistinctPayload() throws Exception {
        EventStore notifyingStore = new EventStoreImpl(
                dataSource, dataSource, objectMapper, newConfig(),
                clockProvider, mock(ApplicationEventPublisher.class), "crablet_events");

        try (Connection listenConn = dataSource.getConnection()) {
            listen(listenConn, "crablet_events");

            notifyingStore.executeInTransaction(txEventStore -> {
                txEventStore.appendCommutative(List.of(typedAppendEvent("TxNotifyOne")));
                txEventStore.appendCommutative(List.of(typedAppendEvent("TxNotifyTwo")));
                return "committed";
            });

            PGNotification[] notifications = notifications(listenConn, 5000);
            assertThat(notifications)
                    .as("expected both deferred transaction notifications after commit")
                    .isNotNull()
                    .hasSize(2);
            List<String> payloads = List.of(notifications).stream()
                    .map(PGNotification::getParameter)
                    .toList();
            assertThat(payloads)
                    .containsExactlyInAnyOrder("TxNotifyOne|category,test_id", "TxNotifyTwo|category,test_id");
        }
    }

    @Test
    void sqlDirectCallWithOldElevenArgumentSignatureStillSucceeds() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            ResultSet rs = statement.executeQuery("""
                    SELECT append_events_if(
                        ARRAY['SqlDirectEvent']::text[],
                        ARRAY['{sql_id=old_signature}']::text[],
                        ARRAY['{}'::jsonb]::jsonb[],
                        NULL::text[],
                        NULL::text[],
                        NULL::bigint,
                        NULL::text[],
                        NULL::text[],
                        CURRENT_TIMESTAMP,
                        NULL::uuid,
                        NULL::bigint
                    )
                    """);

            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).contains("\"success\": true");
        }

        assertThat(eventStore.exists(Query.forEventAndTag("SqlDirectEvent", "sql_id", "old_signature")))
                .isTrue();
    }

    @Test
    void oversizedNotifyPayloadWarningDoesNotFailAppend() throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT append_events_if(
                         ARRAY['OversizedNotifyEvent']::text[],
                         ARRAY['{sql_id=oversized_payload}']::text[],
                         ARRAY['{}'::jsonb]::jsonb[],
                         NULL::text[],
                         NULL::text[],
                         NULL::bigint,
                         NULL::text[],
                         NULL::text[],
                         CURRENT_TIMESTAMP,
                         NULL::uuid,
                         NULL::bigint,
                         ?::text,
                         ?::text
                     )
                     """)) {
            statement.setString(1, "crablet_events");
            statement.setString(2, "x".repeat(9000));

            ResultSet rs = statement.executeQuery();

            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).contains("\"success\": true");
            assertThat(statement.getWarnings() != null)
                    .as("expected pg_notify warning to be surfaced on the statement")
                    .isTrue();
        }

        assertThat(eventStore.exists(Query.forEventAndTag("OversizedNotifyEvent", "sql_id", "oversized_payload")))
                .isTrue();
    }

    @Test
    void shouldHandleMultipleAppends() {
        // Given
        String testId1 = UUID.randomUUID().toString();
        String testId2 = UUID.randomUUID().toString();

        List<AppendEvent> events = List.of(
                appendEvent(testId1, "Message 1"),
                appendEvent(testId2, "Message 2")
        );

        // When
        String transactionId = eventStore.appendCommutative(events);

        // Then - Both events were appended and transaction ID returned
        assertNotNull(transactionId);
        assertFalse(transactionId.isEmpty());
    }

    @Test
    void shouldExecuteInTransaction() {
        // Given
        String testId = UUID.randomUUID().toString();

        // When - Execute in transaction (tests executeInTransaction method)
        String result = eventStore.executeInTransaction(txEventStore -> {
            txEventStore.appendCommutative(List.of(appendEvent(testId, "Transaction Test")));
            return testId;
        });

        // Then - Transaction completed and returned correct result
        assertEquals(testId, result);
        
        // Verify event was actually stored
        EventRepository eventRepository =
            new EventRepositoryImpl(dataSource,
                new EventStoreConfig());
        Query query =
            Query.forEventAndTag("TestEvent", "test_id", testId);
        List<StoredEvent> storedEvents =
            eventRepository.query(query, null);
        assertEquals(1, storedEvents.size(), "Event should be stored in database");
    }

    @Test
    void shouldRejectEmptyAppendThroughPublicApi() {
        assertThrows(IllegalArgumentException.class, () -> eventStore.appendCommutative(List.of()));
    }

    @Test
    void shouldCheckWhetherEventsExist() {
        String testId = UUID.randomUUID().toString();
        Query query = Query.forEventAndTag("TestEvent", "test_id", testId);

        assertFalse(eventStore.exists(query));

        eventStore.appendCommutative(List.of(appendEvent(testId, "Exists Test")));

        assertTrue(eventStore.exists(query));
    }

    @Test
    void shouldProjectMatchingEventsAndDeserializePayloads() {
        String matchingId = UUID.randomUUID().toString();
        String skippedId = UUID.randomUUID().toString();
        eventStore.appendCommutative(List.of(
                appendEvent(matchingId, "first"),
                appendEvent(matchingId, "second"),
                appendEvent(skippedId, "skipped")
        ));

        Query query = Query.forEventAndTag("TestEvent", "test_id", matchingId);
        StateProjector<String> projector = new StateProjector<>() {
            @Override
            public List<String> getEventTypes() {
                return List.of("TestEvent");
            }

            @Override
            public String getInitialState() {
                return "";
            }

            @Override
            public String transition(String currentState, StoredEvent event, EventDeserializer deserializer) {
                TestEvent testEvent = deserializer.deserialize(event, TestEvent.class);
                return currentState + testEvent.message() + ";";
            }
        };

        ProjectionResult<String> result = eventStore.project(query, StreamPosition.zero(), String.class, List.of(projector));

        assertEquals("first;second;", result.state());
        assertNotNull(result.streamPosition());
    }

    @Test
    void shouldStoreCommandAuditRecord() throws Exception {
        String testId = UUID.randomUUID().toString();
        String transactionId = eventStore.executeInTransaction(txStore -> {
            String txId = txStore.appendCommutative(List.of(appendEvent(testId, "Command Audit")));
            ((CommandAuditStore) txStore).storeCommand(
                    "{\"id\":\"" + testId + "\"}", "TestCommand", Instant.now());
            return txId;
        });

        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(
                     "SELECT type, data, metadata FROM crablet_commands WHERE transaction_id = ?::xid8")) {
            stmt.setString(1, transactionId);
            try (ResultSet rs = stmt.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("TestCommand", rs.getString("type"));
                assertTrue(rs.getString("data").contains(testId));
                assertTrue(rs.getString("metadata").contains("TestCommand"));
            }
        }
    }

    @Test
    void shouldStoreCommandAuditRecordThroughPublicStore() throws Exception {
        UUID commandId = UUID.randomUUID();
        String testId = UUID.randomUUID().toString();

        boolean inserted = ((CommandAuditStore) eventStoreImpl).storeCommandIfAbsent(
                "{\"id\":\"" + testId + "\"}",
                "PublicStoreCommand",
                commandId,
                Instant.parse("2026-01-02T03:04:05Z"));
        boolean duplicate = ((CommandAuditStore) eventStoreImpl).storeCommandIfAbsent(
                "{\"id\":\"" + testId + "\"}",
                "PublicStoreCommand",
                commandId,
                Instant.parse("2026-01-02T03:04:05Z"));

        assertThat(inserted).isTrue();
        assertThat(duplicate).isFalse();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(
                     "SELECT type, data, occurred_at FROM crablet_commands WHERE command_id = ?")) {
            stmt.setObject(1, commandId);
            try (ResultSet rs = stmt.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("PublicStoreCommand", rs.getString("type"));
                assertTrue(rs.getString("data").contains(testId));
                assertEquals(Instant.parse("2026-01-02T03:04:05Z"), rs.getTimestamp("occurred_at").toInstant());
                assertFalse(rs.next());
            }
        }
    }

    @Test
    void shouldStoreCommandAuditRecordThroughTransactionScopedStoreIfAbsent() throws Exception {
        UUID commandId = UUID.randomUUID();
        String testId = UUID.randomUUID().toString();

        boolean inserted = eventStore.executeInTransaction(txStore ->
                ((CommandAuditStore) txStore).storeCommandIfAbsent(
                        "{\"id\":\"" + testId + "\"}",
                        "ScopedIdempotentCommand",
                        commandId,
                        Instant.parse("2026-01-02T03:04:05Z")));

        assertThat(inserted).isTrue();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(
                     "SELECT type, data, occurred_at FROM crablet_commands WHERE command_id = ?")) {
            stmt.setObject(1, commandId);
            try (ResultSet rs = stmt.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("ScopedIdempotentCommand", rs.getString("type"));
                assertTrue(rs.getString("data").contains(testId));
                assertEquals(Instant.parse("2026-01-02T03:04:05Z"), rs.getTimestamp("occurred_at").toInstant());
                assertFalse(rs.next());
            }
        }
    }

    @Test
    void shouldStoreCommandAuditRecordThroughPublicAuditStore() throws Exception {
        String testId = UUID.randomUUID().toString();
        Instant occurredAt = Instant.parse("2026-01-02T03:04:05Z");

        boolean inserted = ((CommandAuditStore) eventStoreImpl).storeCommand(
                "{\"id\":\"" + testId + "\"}",
                "PublicAuditCommand",
                occurredAt);

        assertThat(inserted).isTrue();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(
                     "SELECT type, data, occurred_at FROM crablet_commands WHERE type = ? AND data @> ?::jsonb")) {
            stmt.setString(1, "PublicAuditCommand");
            stmt.setString(2, "{\"id\":\"" + testId + "\"}");
            try (ResultSet rs = stmt.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("PublicAuditCommand", rs.getString("type"));
                assertTrue(rs.getString("data").contains(testId));
                assertEquals(occurredAt, rs.getTimestamp("occurred_at").toInstant());
                assertFalse(rs.next());
            }
        }
    }

    @Test
    void shouldWrapConnectionFailureWhenStoringCommandAuditRecord() throws Exception {
        DataSource failingWriteDataSource = mock(DataSource.class);
        Mockito.when(failingWriteDataSource.getConnection()).thenThrow(new SQLException("connection down"));
        EventStoreImpl failingStore = new EventStoreImpl(
                failingWriteDataSource, dataSource, objectMapper, newConfig(),
                clockProvider, mock(ApplicationEventPublisher.class));

        EventStoreException ex = assertThrows(EventStoreException.class,
                () -> ((CommandAuditStore) failingStore).storeCommandIfAbsent(
                        "{}", "FailingCommand", UUID.randomUUID(), clockProvider.now()));

        assertThat(ex).hasMessage("Failed to store command")
                .hasCauseInstanceOf(SQLException.class);
    }

    @Test
    void shouldWrapConnectionFailureWhenStoringAuditOnlyCommandRecord() throws Exception {
        DataSource failingWriteDataSource = mock(DataSource.class);
        Mockito.when(failingWriteDataSource.getConnection()).thenThrow(new SQLException("connection down"));
        EventStoreImpl failingStore = new EventStoreImpl(
                failingWriteDataSource, dataSource, objectMapper, newConfig(),
                clockProvider, mock(ApplicationEventPublisher.class));

        EventStoreException ex = assertThrows(EventStoreException.class,
                () -> ((CommandAuditStore) failingStore).storeCommand(
                        "{}", "FailingCommand", clockProvider.now()));

        assertThat(ex).hasMessage("Failed to store command")
                .hasCauseInstanceOf(SQLException.class);
    }

    @Test
    void shouldWrapPrepareStatementFailureWhenStoringCommandAuditRecord() throws Exception {
        Connection failingConnection = mock(Connection.class);
        Mockito.when(failingConnection.prepareStatement(Mockito.anyString()))
                .thenThrow(new SQLException("prepare failed"));
        DataSource failingWriteDataSource = mock(DataSource.class);
        Mockito.when(failingWriteDataSource.getConnection()).thenReturn(failingConnection);
        EventStoreImpl failingStore = new EventStoreImpl(
                failingWriteDataSource, dataSource, objectMapper, newConfig(),
                clockProvider, mock(ApplicationEventPublisher.class));

        EventStoreException ex = assertThrows(EventStoreException.class,
                () -> ((CommandAuditStore) failingStore).storeCommandIfAbsent(
                        "{}", "FailingCommand", UUID.randomUUID(), clockProvider.now()));

        assertThat(ex).hasMessage("Failed to store command")
                .hasCauseInstanceOf(SQLException.class);
    }

    @Test
    void shouldRollbackTransactionWhenOperationFails() {
        String testId = UUID.randomUUID().toString();

        assertThrows(IllegalStateException.class, () -> eventStore.executeInTransaction(txEventStore -> {
            txEventStore.appendCommutative(List.of(appendEvent(testId, "Rollback Test")));
            throw new IllegalStateException("rollback");
        }));

        assertFalse(eventStore.exists(Query.forEventAndTag("TestEvent", "test_id", testId)));
    }

    @Test
    void existsUsesReadDataSource() {
        CountingDataSource readCounter = new CountingDataSource(dataSource);
        CountingDataSource writeCounter = new CountingDataSource(dataSource);
        EventStoreImpl withCounters = new EventStoreImpl(
                writeCounter, readCounter, objectMapper, newConfig(),
                clockProvider, mock(ApplicationEventPublisher.class));

        withCounters.exists(Query.forEventAndTag("NoOp", "test_id", UUID.randomUUID().toString()));

        assertThat(readCounter.getConnectionCount()).isGreaterThan(0);
        assertThat(writeCounter.getConnectionCount()).isEqualTo(0);
    }

    @Test
    void appendCommutativeUsesWriteDataSource() {
        CountingDataSource readCounter = new CountingDataSource(dataSource);
        CountingDataSource writeCounter = new CountingDataSource(dataSource);
        EventStoreImpl withCounters = new EventStoreImpl(
                writeCounter, readCounter, objectMapper, newConfig(),
                clockProvider, mock(ApplicationEventPublisher.class));

        withCounters.appendCommutative(List.of(appendEvent(UUID.randomUUID().toString(), "write-routing")));

        assertThat(writeCounter.getConnectionCount()).isGreaterThan(0);
        assertThat(readCounter.getConnectionCount()).isEqualTo(0);
    }

    private EventStoreConfig newConfig() {
        EventStoreConfig c = new EventStoreConfig();
        c.setPersistCommands(false);
        c.setTransactionIsolation("READ_COMMITTED");
        c.setFetchSize(1000);
        return c;
    }

    private static void listen(Connection connection, String channel) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("LISTEN " + channel);
        }
    }

    private static PGNotification[] notifications(Connection connection, int timeoutMillis) throws SQLException {
        return connection.unwrap(PGConnection.class).getNotifications(timeoutMillis);
    }

    static class CountingDataSource implements DataSource {
        private final DataSource delegate;
        private final AtomicInteger count = new AtomicInteger();

        CountingDataSource(DataSource delegate) {
            this.delegate = delegate;
        }

        @Override
        public Connection getConnection() throws SQLException {
            count.incrementAndGet();
            return delegate.getConnection();
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            count.incrementAndGet();
            return delegate.getConnection(username, password);
        }

        int getConnectionCount() {
            return count.get();
        }

        @Override public PrintWriter getLogWriter() throws SQLException { return delegate.getLogWriter(); }
        @Override public void setLogWriter(PrintWriter out) throws SQLException { delegate.setLogWriter(out); }
        @Override public void setLoginTimeout(int s) throws SQLException { delegate.setLoginTimeout(s); }
        @Override public int getLoginTimeout() throws SQLException { return delegate.getLoginTimeout(); }
        @Override public Logger getParentLogger() throws SQLFeatureNotSupportedException { return delegate.getParentLogger(); }
        @Override public <T> T unwrap(Class<T> iface) throws SQLException { return delegate.unwrap(iface); }
        @Override public boolean isWrapperFor(Class<?> iface) throws SQLException { return delegate.isWrapperFor(iface); }
    }

    @Test
    void shouldWrapProjectionFailures() {
        String testId = UUID.randomUUID().toString();
        eventStore.appendCommutative(List.of(appendEvent(testId, "Projection Failure")));

        StateProjector<String> failingProjector = new StateProjector<>() {
            @Override
            public List<String> getEventTypes() {
                return List.of("TestEvent");
            }

            @Override
            public String getInitialState() {
                return "";
            }

            @Override
            public String transition(String currentState, StoredEvent event, EventDeserializer deserializer) {
                throw new IllegalStateException("projection failed");
            }
        };

        assertThrows(EventStoreException.class, () -> eventStore.project(
                Query.forEventAndTag("TestEvent", "test_id", testId),
                StreamPosition.zero(),
                String.class,
                List.of(failingProjector)));
    }
}
