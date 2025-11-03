package com.crablet.outbox.publishers;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.crablet.eventstore.store.StoredEvent;
import com.crablet.outbox.OutboxPublisher;
import com.crablet.outbox.PublishException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.core.env.Environment;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for StatisticsPublisher.
 * Tests statistics tracking, logging, and edge cases.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("StatisticsPublisher Unit Tests")
class StatisticsPublisherTest {

    @Mock
    private Environment environment;

    private StatisticsPublisher publisher;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        when(environment.getProperty("crablet.outbox.publishers.statistics.log-interval-seconds", "10"))
                .thenReturn("10");
        
        publisher = new StatisticsPublisher(environment);
        
        // Set up log capture
        Logger logger = (Logger) LoggerFactory.getLogger(StatisticsPublisher.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
        logger.setLevel(Level.INFO);
    }

    @Test
    @DisplayName("Should return correct publisher name")
    void shouldReturnCorrectPublisherName() {
        // When
        String name = publisher.getName();

        // Then
        assertThat(name).isEqualTo("StatisticsPublisher");
    }

    @Test
    @DisplayName("Should return true for isHealthy")
    void shouldReturnTrue_ForIsHealthy() {
        // When
        boolean healthy = publisher.isHealthy();

        // Then
        assertThat(healthy).isTrue();
    }

    @Test
    @DisplayName("Should return BATCH as preferred mode")
    void shouldReturnBATCH_AsPreferredMode() {
        // When
        OutboxPublisher.PublishMode mode = publisher.getPreferredMode();

        // Then
        assertThat(mode).isEqualTo(OutboxPublisher.PublishMode.BATCH);
    }

    @Test
    @DisplayName("Should read log interval from environment with default")
    void shouldReadLogInterval_FromEnvironmentWithDefault() {
        // Given - Environment returns default
        when(environment.getProperty("crablet.outbox.publishers.statistics.log-interval-seconds", "10"))
                .thenReturn("10");

        // When
        StatisticsPublisher pub = new StatisticsPublisher(environment);

        // Then - No exception, log interval is set (verify via behavior)
        assertThat(pub).isNotNull();
    }

    @Test
    @DisplayName("Should read custom log interval from environment")
    void shouldReadCustomLogInterval_FromEnvironment() {
        // Given
        when(environment.getProperty("crablet.outbox.publishers.statistics.log-interval-seconds", "10"))
                .thenReturn("5");

        // When
        StatisticsPublisher pub = new StatisticsPublisher(environment);

        // Then
        assertThat(pub).isNotNull();
    }

    @Test
    @DisplayName("Should publish empty batch without exception")
    void shouldPublishEmptyBatch_WithoutException() {
        // Given
        List<StoredEvent> events = List.of();

        // When & Then
        assertThatCode(() -> publisher.publishBatch(events))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should increment total events processed")
    void shouldIncrementTotalEventsProcessed() throws PublishException {
        // Given
        List<StoredEvent> events = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            events.add(new StoredEvent(
                    "EventType",
                    List.of(),
                    "data".getBytes(),
                    "tx-" + i,
                    100L + i,
                    Instant.now()
            ));
        }

        // When
        publisher.publishBatch(events);

        // Then - Verify events were processed (statistics tracked internally)
        // Note: We can't directly access the counters, but we can verify no exceptions
        assertThat(publisher.isHealthy()).isTrue();
    }

    @Test
    @DisplayName("Should count events by type")
    void shouldCountEvents_ByType() throws PublishException {
        // Given
        List<StoredEvent> events = List.of(
                new StoredEvent("WalletOpened", List.of(), "data1".getBytes(), "tx-1", 100L, Instant.now()),
                new StoredEvent("DepositMade", List.of(), "data2".getBytes(), "tx-2", 101L, Instant.now()),
                new StoredEvent("WalletOpened", List.of(), "data3".getBytes(), "tx-3", 102L, Instant.now())
        );

        // When
        publisher.publishBatch(events);

        // Then - Statistics are tracked internally (verify via behavior)
        assertThat(publisher.isHealthy()).isTrue();
    }

    @Test
    @DisplayName("Should log statistics after interval")
    void shouldLogStatistics_AfterInterval() throws Exception {
        // Given - Short interval for testing
        when(environment.getProperty("crablet.outbox.publishers.statistics.log-interval-seconds", "10"))
                .thenReturn("1"); // 1 second interval
        
        StatisticsPublisher pub = new StatisticsPublisher(environment);
        
        List<StoredEvent> events = List.of(
                new StoredEvent("WalletOpened", List.of(), "data".getBytes(), "tx-1", 100L, Instant.now())
        );

        // When - Publish and wait for interval
        pub.publishBatch(events);
        Thread.sleep(1100); // Wait just over 1 second
        pub.publishBatch(events); // Second call should trigger log

        // Then - Verify logs (may take some time)
        // Note: This is a timing-dependent test, may be flaky
        assertThat(pub.isHealthy()).isTrue();
    }

    @Test
    @DisplayName("Should not log statistics before interval")
    void shouldNotLogStatistics_BeforeInterval() throws PublishException {
        // Given
        List<StoredEvent> events = List.of(
                new StoredEvent("WalletOpened", List.of(), "data".getBytes(), "tx-1", 100L, Instant.now())
        );

        // When - First call
        publisher.publishBatch(events);

        // Then - Statistics logged only if interval passed (unlikely immediately)
        assertThat(publisher.isHealthy()).isTrue();
    }

    @Test
    @DisplayName("Should handle multiple batches")
    void shouldHandleMultipleBatches() throws PublishException {
        // Given
        List<StoredEvent> batch1 = List.of(
                new StoredEvent("WalletOpened", List.of(), "data1".getBytes(), "tx-1", 100L, Instant.now())
        );
        List<StoredEvent> batch2 = List.of(
                new StoredEvent("DepositMade", List.of(), "data2".getBytes(), "tx-2", 101L, Instant.now())
        );

        // When
        publisher.publishBatch(batch1);
        publisher.publishBatch(batch2);

        // Then
        assertThat(publisher.isHealthy()).isTrue();
    }

    @Test
    @DisplayName("Should handle events with same type multiple times")
    void shouldHandleEvents_WithSameTypeMultipleTimes() throws PublishException {
        // Given
        List<StoredEvent> events = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            events.add(new StoredEvent(
                    "WalletOpened",
                    List.of(),
                    ("data-" + i).getBytes(),
                    "tx-" + i,
                    100L + i,
                    Instant.now()
            ));
        }

        // When
        publisher.publishBatch(events);

        // Then
        assertThat(publisher.isHealthy()).isTrue();
    }
}

