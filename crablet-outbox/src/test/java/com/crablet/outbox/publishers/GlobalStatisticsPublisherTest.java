package com.crablet.outbox.publishers;

import com.crablet.outbox.config.GlobalStatisticsConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests for GlobalStatisticsPublisher.
 * Tests statistics tracking, getters, and reset functionality.
 */
@DisplayName("GlobalStatisticsPublisher Unit Tests")
class GlobalStatisticsPublisherTest {

    private GlobalStatisticsPublisher publisher;
    private GlobalStatisticsConfig config;

    @BeforeEach
    void setUp() {
        config = new GlobalStatisticsConfig();
        config.setEnabled(true);
        config.setLogIntervalSeconds(1L); // Short interval for testing
        config.setLogLevel("INFO");
        
        publisher = new GlobalStatisticsPublisher(config);
        publisher.reset(); // Start fresh
    }

    @Test
    @DisplayName("Should initialize with zero events")
    void shouldInitialize_WithZeroEvents() {
        // Then
        assertThat(publisher.getTotalEventsProcessed()).isEqualTo(0);
        assertThat(publisher.getEventsForTopic("topic1")).isEqualTo(0);
        assertThat(publisher.getEventsForPublisher("publisher1")).isEqualTo(0);
        assertThat(publisher.getEventsForTopicPublisher("topic1", "publisher1")).isEqualTo(0);
        assertThat(publisher.getEventsForType("EventType")).isEqualTo(0);
    }

    @Test
    @DisplayName("Should increment total events when recordEvent is called")
    void shouldIncrementTotalEvents_WhenRecordEventCalled() {
        // When
        publisher.recordEvent("topic1", "publisher1", "WalletOpened");

        // Then
        assertThat(publisher.getTotalEventsProcessed()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should track events per topic")
    void shouldTrackEvents_PerTopic() {
        // When
        publisher.recordEvent("topic1", "publisher1", "WalletOpened");
        publisher.recordEvent("topic1", "publisher1", "DepositMade");
        publisher.recordEvent("topic2", "publisher1", "UserCreated");

        // Then
        assertThat(publisher.getEventsForTopic("topic1")).isEqualTo(2);
        assertThat(publisher.getEventsForTopic("topic2")).isEqualTo(1);
    }

    @Test
    @DisplayName("Should track events per publisher")
    void shouldTrackEvents_PerPublisher() {
        // When
        publisher.recordEvent("topic1", "publisher1", "WalletOpened");
        publisher.recordEvent("topic1", "publisher2", "DepositMade");
        publisher.recordEvent("topic2", "publisher1", "UserCreated");

        // Then
        assertThat(publisher.getEventsForPublisher("publisher1")).isEqualTo(2);
        assertThat(publisher.getEventsForPublisher("publisher2")).isEqualTo(1);
    }

    @Test
    @DisplayName("Should track events per topic-publisher pair")
    void shouldTrackEvents_PerTopicPublisherPair() {
        // When
        publisher.recordEvent("topic1", "publisher1", "WalletOpened");
        publisher.recordEvent("topic1", "publisher1", "DepositMade");
        publisher.recordEvent("topic1", "publisher2", "WithdrawalMade");
        publisher.recordEvent("topic2", "publisher1", "UserCreated");

        // Then
        assertThat(publisher.getEventsForTopicPublisher("topic1", "publisher1")).isEqualTo(2);
        assertThat(publisher.getEventsForTopicPublisher("topic1", "publisher2")).isEqualTo(1);
        assertThat(publisher.getEventsForTopicPublisher("topic2", "publisher1")).isEqualTo(1);
    }

    @Test
    @DisplayName("Should track events per type")
    void shouldTrackEvents_PerType() {
        // When
        publisher.recordEvent("topic1", "publisher1", "WalletOpened");
        publisher.recordEvent("topic1", "publisher1", "WalletOpened");
        publisher.recordEvent("topic1", "publisher1", "DepositMade");
        publisher.recordEvent("topic2", "publisher1", "WalletOpened");

        // Then
        assertThat(publisher.getEventsForType("WalletOpened")).isEqualTo(3);
        assertThat(publisher.getEventsForType("DepositMade")).isEqualTo(1);
    }

    @Test
    @DisplayName("Should handle multiple events with same topic and publisher")
    void shouldHandleMultipleEvents_WithSameTopicAndPublisher() {
        // When
        for (int i = 0; i < 100; i++) {
            publisher.recordEvent("topic1", "publisher1", "EventType");
        }

        // Then
        assertThat(publisher.getTotalEventsProcessed()).isEqualTo(100);
        assertThat(publisher.getEventsForTopic("topic1")).isEqualTo(100);
        assertThat(publisher.getEventsForPublisher("publisher1")).isEqualTo(100);
        assertThat(publisher.getEventsForTopicPublisher("topic1", "publisher1")).isEqualTo(100);
        assertThat(publisher.getEventsForType("EventType")).isEqualTo(100);
    }

    @Test
    @DisplayName("Should handle events with different topics")
    void shouldHandleEvents_WithDifferentTopics() {
        // When
        publisher.recordEvent("topic1", "publisher1", "EventType");
        publisher.recordEvent("topic2", "publisher1", "EventType");
        publisher.recordEvent("topic3", "publisher1", "EventType");

        // Then
        assertThat(publisher.getTotalEventsProcessed()).isEqualTo(3);
        assertThat(publisher.getEventsForTopic("topic1")).isEqualTo(1);
        assertThat(publisher.getEventsForTopic("topic2")).isEqualTo(1);
        assertThat(publisher.getEventsForTopic("topic3")).isEqualTo(1);
    }

    @Test
    @DisplayName("Should handle events with different publishers")
    void shouldHandleEvents_WithDifferentPublishers() {
        // When
        publisher.recordEvent("topic1", "publisher1", "EventType");
        publisher.recordEvent("topic1", "publisher2", "EventType");
        publisher.recordEvent("topic1", "publisher3", "EventType");

        // Then
        assertThat(publisher.getTotalEventsProcessed()).isEqualTo(3);
        assertThat(publisher.getEventsForPublisher("publisher1")).isEqualTo(1);
        assertThat(publisher.getEventsForPublisher("publisher2")).isEqualTo(1);
        assertThat(publisher.getEventsForPublisher("publisher3")).isEqualTo(1);
    }

    @Test
    @DisplayName("Should return zero for non-existent topic")
    void shouldReturnZero_ForNonExistentTopic() {
        // When
        publisher.recordEvent("topic1", "publisher1", "EventType");

        // Then
        assertThat(publisher.getEventsForTopic("non-existent")).isEqualTo(0);
    }

    @Test
    @DisplayName("Should return zero for non-existent publisher")
    void shouldReturnZero_ForNonExistentPublisher() {
        // When
        publisher.recordEvent("topic1", "publisher1", "EventType");

        // Then
        assertThat(publisher.getEventsForPublisher("non-existent")).isEqualTo(0);
    }

    @Test
    @DisplayName("Should return zero for non-existent topic-publisher pair")
    void shouldReturnZero_ForNonExistentTopicPublisherPair() {
        // When
        publisher.recordEvent("topic1", "publisher1", "EventType");

        // Then
        assertThat(publisher.getEventsForTopicPublisher("topic1", "non-existent")).isEqualTo(0);
        assertThat(publisher.getEventsForTopicPublisher("non-existent", "publisher1")).isEqualTo(0);
    }

    @Test
    @DisplayName("Should return zero for non-existent event type")
    void shouldReturnZero_ForNonExistentEventType() {
        // When
        publisher.recordEvent("topic1", "publisher1", "EventType");

        // Then
        assertThat(publisher.getEventsForType("non-existent")).isEqualTo(0);
    }

    @Test
    @DisplayName("Should reset all statistics")
    void shouldReset_AllStatistics() {
        // Given
        publisher.recordEvent("topic1", "publisher1", "WalletOpened");
        publisher.recordEvent("topic1", "publisher1", "DepositMade");
        publisher.recordEvent("topic2", "publisher2", "UserCreated");

        // When
        publisher.reset();

        // Then
        assertThat(publisher.getTotalEventsProcessed()).isEqualTo(0);
        assertThat(publisher.getEventsForTopic("topic1")).isEqualTo(0);
        assertThat(publisher.getEventsForTopic("topic2")).isEqualTo(0);
        assertThat(publisher.getEventsForPublisher("publisher1")).isEqualTo(0);
        assertThat(publisher.getEventsForPublisher("publisher2")).isEqualTo(0);
        assertThat(publisher.getEventsForTopicPublisher("topic1", "publisher1")).isEqualTo(0);
        assertThat(publisher.getEventsForType("WalletOpened")).isEqualTo(0);
    }

    @Test
    @DisplayName("Should handle topic-publisher pair key format correctly")
    void shouldHandleTopicPublisherPairKeyFormat_Correctly() {
        // When
        publisher.recordEvent("topic1", "publisher1", "EventType");

        // Then - Verify key format is "topic:publisher"
        assertThat(publisher.getEventsForTopicPublisher("topic1", "publisher1")).isEqualTo(1);
    }

    @Test
    @DisplayName("Should handle empty topic name")
    void shouldHandleEmptyTopicName() {
        // When & Then - Should not throw
        assertThatCode(() -> publisher.recordEvent("", "publisher1", "EventType"))
                .doesNotThrowAnyException();
        
        assertThat(publisher.getEventsForTopic("")).isEqualTo(1);
    }

    @Test
    @DisplayName("Should handle empty publisher name")
    void shouldHandleEmptyPublisherName() {
        // When & Then - Should not throw
        assertThatCode(() -> publisher.recordEvent("topic1", "", "EventType"))
                .doesNotThrowAnyException();
        
        assertThat(publisher.getEventsForPublisher("")).isEqualTo(1);
    }

    @Test
    @DisplayName("Should handle empty event type")
    void shouldHandleEmptyEventType() {
        // When & Then - Should not throw
        assertThatCode(() -> publisher.recordEvent("topic1", "publisher1", ""))
                .doesNotThrowAnyException();
        
        assertThat(publisher.getEventsForType("")).isEqualTo(1);
    }

    @Test
    @DisplayName("Should handle concurrent events")
    void shouldHandleConcurrentEvents() throws InterruptedException {
        // Given
        int threadCount = 10;
        int eventsPerThread = 10;
        Thread[] threads = new Thread[threadCount];

        // When - Create threads that record events concurrently
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < eventsPerThread; j++) {
                    publisher.recordEvent("topic" + threadId, "publisher" + threadId, "EventType");
                }
            });
            threads[i].start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // Then
        assertThat(publisher.getTotalEventsProcessed()).isEqualTo(threadCount * eventsPerThread);
    }
}

