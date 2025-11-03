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
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for LogPublisher.
 * Tests publisher behavior, logging, and edge cases.
 */
@DisplayName("LogPublisher Unit Tests")
class LogPublisherTest {

    private LogPublisher publisher;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        publisher = new LogPublisher();
        
        // Set up log capture
        Logger logger = (Logger) LoggerFactory.getLogger(LogPublisher.class);
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
        assertThat(name).isEqualTo("LogPublisher");
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
    @DisplayName("Should publish empty batch without exception")
    void shouldPublishEmptyBatch_WithoutException() {
        // Given
        List<StoredEvent> events = List.of();

        // When & Then - Should not throw
        assertThatCode(() -> publisher.publishBatch(events))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should publish single event")
    void shouldPublishSingleEvent() throws PublishException {
        // Given
        StoredEvent event = new StoredEvent(
                "WalletOpened",
                List.of(new com.crablet.eventstore.store.Tag("wallet_id", "wallet-123")),
                "{\"walletId\":\"wallet-123\"}".getBytes(),
                "tx-123",
                100L,
                Instant.now()
        );
        List<StoredEvent> events = List.of(event);

        // When
        publisher.publishBatch(events);

        // Then - Verify logs were written (check log appender)
        List<ILoggingEvent> logEvents = logAppender.list;
        assertThat(logEvents).hasSize(2); // Batch header + event detail
        assertThat(logEvents.get(0).getFormattedMessage()).contains("Publishing batch of 1 events");
        assertThat(logEvents.get(1).getFormattedMessage()).contains("WalletOpened");
    }

    @Test
    @DisplayName("Should publish multiple events")
    void shouldPublishMultipleEvents() throws PublishException {
        // Given
        List<StoredEvent> events = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            events.add(new StoredEvent(
                    "DepositMade",
                    List.of(),
                    ("{\"amount\":" + i + "}").getBytes(),
                    "tx-" + i,
                    100L + i,
                    Instant.now()
            ));
        }

        // When
        publisher.publishBatch(events);

        // Then - Verify logs
        List<ILoggingEvent> logEvents = logAppender.list;
        assertThat(logEvents).hasSize(4); // 1 batch header + 3 event details
        assertThat(logEvents.get(0).getFormattedMessage()).contains("Publishing batch of 3 events");
    }

    @Test
    @DisplayName("Should log event type, data, and position for each event")
    void shouldLogEventTypeDataAndPosition_ForEachEvent() throws PublishException {
        // Given
        StoredEvent event = new StoredEvent(
                "WithdrawalMade",
                List.of(),
                "{\"amount\":50}".getBytes(),
                "tx-456",
                200L,
                Instant.now()
        );
        List<StoredEvent> events = List.of(event);

        // When
        publisher.publishBatch(events);

        // Then - Verify event details are logged
        List<ILoggingEvent> logEvents = logAppender.list;
        String eventLog = logEvents.get(1).getFormattedMessage();
        assertThat(eventLog).contains("WithdrawalMade");
        assertThat(eventLog).contains("{\"amount\":50}");
        assertThat(eventLog).contains("200");
    }

    @Test
    @DisplayName("Should handle events with binary data")
    void shouldHandleEvents_WithBinaryData() throws PublishException {
        // Given
        byte[] binaryData = new byte[]{0x01, 0x02, 0x03, 0x04};
        StoredEvent event = new StoredEvent(
                "BinaryEvent",
                List.of(),
                binaryData,
                "tx-789",
                300L,
                Instant.now()
        );
        List<StoredEvent> events = List.of(event);

        // When & Then - Should not throw
        assertThatCode(() -> publisher.publishBatch(events))
                .doesNotThrowAnyException();

        // Verify logs
        List<ILoggingEvent> logEvents = logAppender.list;
        assertThat(logEvents.get(1).getFormattedMessage()).contains("BinaryEvent");
        assertThat(logEvents.get(1).getFormattedMessage()).contains("300");
    }

    @Test
    @DisplayName("Should handle events with special characters in data")
    void shouldHandleEvents_WithSpecialCharactersInData() throws PublishException {
        // Given
        String dataWithSpecialChars = "{\"message\":\"Hello\\nWorld\\tTest\"}";
        StoredEvent event = new StoredEvent(
                "SpecialEvent",
                List.of(),
                dataWithSpecialChars.getBytes(),
                "tx-special",
                400L,
                Instant.now()
        );
        List<StoredEvent> events = List.of(event);

        // When & Then - Should not throw
        assertThatCode(() -> publisher.publishBatch(events))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should handle very large batch")
    void shouldHandleVeryLargeBatch() throws PublishException {
        // Given
        List<StoredEvent> events = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            events.add(new StoredEvent(
                    "Event" + i,
                    List.of(),
                    ("data-" + i).getBytes(),
                    "tx-" + i,
                    1000L + i,
                    Instant.now()
            ));
        }

        // When & Then - Should not throw
        assertThatCode(() -> publisher.publishBatch(events))
                .doesNotThrowAnyException();

        // Verify batch header
        List<ILoggingEvent> logEvents = logAppender.list;
        assertThat(logEvents.get(0).getFormattedMessage()).contains("Publishing batch of 1000 events");
    }
}

