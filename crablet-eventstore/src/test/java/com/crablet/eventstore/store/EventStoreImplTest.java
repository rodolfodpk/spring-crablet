package com.crablet.eventstore.store;

import com.crablet.eventstore.clock.ClockProviderImpl;
import com.crablet.eventstore.dcb.AppendCondition;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.context.ApplicationEventPublisher;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

/**
 * Direct unit test for EventStoreImpl without Spring framework.
 * This test verifies that JaCoCo can properly instrument EventStoreImpl
 * by instantiating it directly without any Spring proxies or component scanning.
 */
class EventStoreImplTest {

    private static PostgreSQLContainer<?> postgres;
    private static DataSource dataSource;
    private EventStore eventStore;
    private ObjectMapper objectMapper;

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

        // Run Flyway migrations
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();
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
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        // Create EventStoreConfig with defaults
        EventStoreConfig config = new EventStoreConfig();
        config.setPersistCommands(false);
        config.setTransactionIsolation("READ_COMMITTED");
        config.setFetchSize(1000);

        // Create ClockProvider
        com.crablet.eventstore.clock.ClockProvider clockProvider = new ClockProviderImpl();

        // Directly instantiate EventStoreImpl - NO SPRING INVOLVEMENT
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        eventStore = new EventStoreImpl(
                dataSource,      // writeDataSource
                dataSource,      // readDataSource
                objectMapper,
                config,
                clockProvider,
                eventPublisher
        );
    }
    
    // Simple test event record
    record TestEvent(String id, String message, Instant timestamp) {}

    @Test
    void shouldAppendEventSuccessfully() {
        // Given
        String testId = UUID.randomUUID().toString();
        TestEvent event = new TestEvent(testId, "Test message", Instant.now());

        AppendEvent appendEvent = AppendEvent.builder("TestEvent")
                .tag("test_id", testId)
                .tag("category", "test")
                .data(event)
                .build();

        // When - This call should be tracked by JaCoCo
        String transactionId = eventStore.appendIf(List.of(appendEvent), AppendCondition.empty());

        // Then - Event was persisted successfully and transaction ID returned
        assertNotNull(transactionId);
        assertFalse(transactionId.isEmpty());
    }

    @Test
    void shouldHandleMultipleAppends() {
        // Given
        String testId1 = UUID.randomUUID().toString();
        String testId2 = UUID.randomUUID().toString();
        TestEvent event1 = new TestEvent(testId1, "Message 1", Instant.now());
        TestEvent event2 = new TestEvent(testId2, "Message 2", Instant.now());

        List<AppendEvent> events = List.of(
                AppendEvent.builder("TestEvent")
                        .tag("test_id", testId1)
                        .tag("category", "test")
                        .data(event1)
                        .build(),
                AppendEvent.builder("TestEvent")
                        .tag("test_id", testId2)
                        .tag("category", "test")
                        .data(event2)
                        .build()
        );

        // When
        String transactionId = eventStore.appendIf(events, AppendCondition.empty());

        // Then - Both events were appended and transaction ID returned
        assertNotNull(transactionId);
        assertFalse(transactionId.isEmpty());
    }

    @Test
    void shouldExecuteInTransaction() {
        // Given
        String testId = UUID.randomUUID().toString();
        TestEvent event = new TestEvent(testId, "Transaction Test", Instant.now());

        // When - Execute in transaction (tests executeInTransaction method)
        String result = eventStore.executeInTransaction(txEventStore -> {
            AppendEvent appendEvent = AppendEvent.builder("TestEvent")
                    .tag("test_id", testId)
                    .tag("category", "test")
                    .data(event)
                    .build();

            txEventStore.appendIf(List.of(appendEvent), AppendCondition.empty());
            return testId;
        });

        // Then - Transaction completed and returned correct result
        assertEquals(testId, result);
        
        // Verify event was actually stored
        com.crablet.eventstore.query.EventRepository eventRepository = 
            new com.crablet.eventstore.query.EventRepositoryImpl(dataSource, 
                new EventStoreConfig());
        com.crablet.eventstore.query.Query query = 
            com.crablet.eventstore.query.Query.forEventAndTag("TestEvent", "test_id", testId);
        List<com.crablet.eventstore.store.StoredEvent> storedEvents = 
            eventRepository.query(query, null);
        assertEquals(1, storedEvents.size(), "Event should be stored in database");
    }
}

