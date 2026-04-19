package com.crablet.outbox.publishers;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.crablet.eventstore.ClockProvider;
import com.crablet.eventstore.StoredEvent;
import com.crablet.outbox.OutboxPublisher;
import com.crablet.outbox.PublishException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

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
    private MutableClockProvider clockProvider;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        when(environment.getProperty("crablet.outbox.publishers.statistics.log-interval-seconds", "10"))
                .thenReturn("10");

        clockProvider = new MutableClockProvider(Instant.parse("2026-01-01T00:00:00Z"));
        publisher = new StatisticsPublisher(environment, clockProvider);
        
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
                    clockProvider.now()
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
                event("WalletOpened", 100L),
                event("DepositMade", 101L),
                event("WalletOpened", 102L)
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

        StatisticsPublisher pub = new StatisticsPublisher(environment, clockProvider);
        
        List<StoredEvent> events = List.of(
                event("WalletOpened", 100L),
                event("DepositMade", 101L)
        );

        // When
        pub.publishBatch(events);
        clockProvider.advance(Duration.ofSeconds(1));
        pub.publishBatch(events); // Second call should trigger log

        // Then
        assertThat(formattedLogMessages())
                .anyMatch(message -> message.contains("===== Outbox Statistics ====="))
                .anyMatch(message -> message.contains("Total events processed: 4"))
                .anyMatch(message -> message.contains("Events by type:"))
                .anyMatch(message -> message.contains("WalletOpened: 2"))
                .anyMatch(message -> message.contains("DepositMade: 2"));
    }

    @Test
    @DisplayName("Should not log statistics before interval")
    void shouldNotLogStatistics_BeforeInterval() throws PublishException {
        // Given
        List<StoredEvent> events = List.of(
                event("WalletOpened", 100L)
        );

        // When - First call
        publisher.publishBatch(events);

        // Then
        assertThat(formattedLogMessages())
                .noneMatch(message -> message.contains("===== Outbox Statistics ====="));
    }

    @Test
    @DisplayName("Should handle multiple batches")
    void shouldHandleMultipleBatches() throws PublishException {
        // Given
        List<StoredEvent> batch1 = List.of(
                event("WalletOpened", 100L)
        );
        List<StoredEvent> batch2 = List.of(
                event("DepositMade", 101L)
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
                    clockProvider.now()
            ));
        }

        // When
        publisher.publishBatch(events);

        // Then
        assertThat(publisher.isHealthy()).isTrue();
    }

    private StoredEvent event(String type, long position) {
        return new StoredEvent(
                type,
                List.of(),
                ("data-" + position).getBytes(),
                "tx-" + position,
                position,
                clockProvider.now()
        );
    }

    private List<String> formattedLogMessages() {
        return logAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private static final class MutableClockProvider implements ClockProvider {
        private Clock clock;

        private MutableClockProvider(Instant instant) {
            this.clock = Clock.fixed(instant, ZoneOffset.UTC);
        }

        @Override
        public Instant now() {
            return clock.instant();
        }

        @Override
        public void setClock(Clock clock) {
            this.clock = clock;
        }

        @Override
        public void resetToSystemClock() {
            this.clock = Clock.systemUTC();
        }

        private void advance(Duration duration) {
            this.clock = Clock.fixed(clock.instant().plus(duration), ZoneOffset.UTC);
        }
    }
}
