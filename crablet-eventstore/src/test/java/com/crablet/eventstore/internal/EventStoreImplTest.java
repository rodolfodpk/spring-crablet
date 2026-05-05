package com.crablet.eventstore.internal;

import com.crablet.eventstore.AppendEvent;
import com.crablet.eventstore.CommandAuditStore;
import com.crablet.eventstore.EventStore;
import com.crablet.eventstore.EventStoreConfig;
import com.crablet.eventstore.EventStoreException;
import com.crablet.eventstore.StoredEvent;
import com.crablet.eventstore.StreamPosition;
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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

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
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
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
    
    // Simple test event record
    record TestEvent(String id, String message, Instant timestamp) {}

    private AppendEvent appendEvent(String id, String message) {
        return AppendEvent.builder("TestEvent")
                .tag("test_id", id)
                .tag("category", "test")
                .data(new TestEvent(id, message, clockProvider.now()))
                .build();
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
    void packagePrivateAppendShouldIgnoreEmptyInput() {
        eventStoreImpl.append(List.of());
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
        String transactionId = eventStore.appendCommutative(List.of(appendEvent(testId, "Command Audit")));

        ((CommandAuditStore) eventStore).storeCommand("{\"id\":\"" + testId + "\"}", "TestCommand", transactionId);

        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(
                     "SELECT type, data, metadata FROM commands WHERE transaction_id = ?::xid8")) {
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
