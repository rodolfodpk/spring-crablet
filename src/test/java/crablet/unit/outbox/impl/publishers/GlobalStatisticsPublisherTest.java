package crablet.unit.outbox.impl.publishers;

import com.crablet.outbox.impl.GlobalStatisticsConfig;
import com.crablet.outbox.impl.publishers.GlobalStatisticsPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for GlobalStatisticsPublisher.
 */
class GlobalStatisticsPublisherTest {
    
    private GlobalStatisticsConfig config;
    private GlobalStatisticsPublisher publisher;
    
    @BeforeEach
    void setUp() {
        config = new GlobalStatisticsConfig();
        config.setEnabled(true);
        config.setLogIntervalSeconds(1); // Short interval for testing
        config.setLogLevel("INFO");
        
        publisher = new GlobalStatisticsPublisher(config);
    }
    
    @Test
    void shouldRecordSingleEvent() {
        // Given
        String topic = "test-topic";
        String publisherName = "CountDownLatchPublisher";
        String eventType = "TestEvent";
        
        // When
        publisher.recordEvent(topic, publisherName, eventType);
        
        // Then
        assertThat(publisher.getTotalEventsProcessed()).isEqualTo(1);
        assertThat(publisher.getEventsForTopic(topic)).isEqualTo(1);
        assertThat(publisher.getEventsForPublisher(publisherName)).isEqualTo(1);
        assertThat(publisher.getEventsForTopicPublisher(topic, publisherName)).isEqualTo(1);
        assertThat(publisher.getEventsForType(eventType)).isEqualTo(1);
    }
    
    @Test
    void shouldRecordMultipleEvents() {
        // Given
        String topic1 = "topic1";
        String topic2 = "topic2";
        String publisher1 = "Publisher1";
        String publisher2 = "Publisher2";
        String eventType1 = "EventType1";
        String eventType2 = "EventType2";
        
        // When
        publisher.recordEvent(topic1, publisher1, eventType1);
        publisher.recordEvent(topic1, publisher2, eventType1);
        publisher.recordEvent(topic2, publisher1, eventType2);
        publisher.recordEvent(topic2, publisher2, eventType2);
        
        // Then
        assertThat(publisher.getTotalEventsProcessed()).isEqualTo(4);
        
        // Per-topic statistics
        assertThat(publisher.getEventsForTopic(topic1)).isEqualTo(2);
        assertThat(publisher.getEventsForTopic(topic2)).isEqualTo(2);
        
        // Per-publisher statistics
        assertThat(publisher.getEventsForPublisher(publisher1)).isEqualTo(2);
        assertThat(publisher.getEventsForPublisher(publisher2)).isEqualTo(2);
        
        // Per topic-publisher pair statistics
        assertThat(publisher.getEventsForTopicPublisher(topic1, publisher1)).isEqualTo(1);
        assertThat(publisher.getEventsForTopicPublisher(topic1, publisher2)).isEqualTo(1);
        assertThat(publisher.getEventsForTopicPublisher(topic2, publisher1)).isEqualTo(1);
        assertThat(publisher.getEventsForTopicPublisher(topic2, publisher2)).isEqualTo(1);
        
        // Per-event-type statistics
        assertThat(publisher.getEventsForType(eventType1)).isEqualTo(2);
        assertThat(publisher.getEventsForType(eventType2)).isEqualTo(2);
    }
    
    @Test
    void shouldHandleConcurrentUpdates() throws InterruptedException {
        // Given
        int numThreads = 10;
        int eventsPerThread = 100;
        Thread[] threads = new Thread[numThreads];
        
        // When
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < eventsPerThread; j++) {
                    publisher.recordEvent("topic-" + threadId, "publisher-" + threadId, "EventType-" + j);
                }
            });
            threads[i].start();
        }
        
        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }
        
        // Then
        int expectedTotal = numThreads * eventsPerThread;
        assertThat(publisher.getTotalEventsProcessed()).isEqualTo(expectedTotal);
        
        // Verify per-topic statistics
        for (int i = 0; i < numThreads; i++) {
            assertThat(publisher.getEventsForTopic("topic-" + i)).isEqualTo(eventsPerThread);
            assertThat(publisher.getEventsForPublisher("publisher-" + i)).isEqualTo(eventsPerThread);
        }
    }
    
    @Test
    void shouldResetStatistics() {
        // Given
        publisher.recordEvent("topic1", "publisher1", "EventType1");
        publisher.recordEvent("topic2", "publisher2", "EventType2");
        
        assertThat(publisher.getTotalEventsProcessed()).isEqualTo(2);
        
        // When
        publisher.reset();
        
        // Then
        assertThat(publisher.getTotalEventsProcessed()).isEqualTo(0);
        assertThat(publisher.getEventsForTopic("topic1")).isEqualTo(0);
        assertThat(publisher.getEventsForPublisher("publisher1")).isEqualTo(0);
        assertThat(publisher.getEventsForTopicPublisher("topic1", "publisher1")).isEqualTo(0);
        assertThat(publisher.getEventsForType("EventType1")).isEqualTo(0);
    }
    
    @Test
    void shouldReturnZeroForNonExistentKeys() {
        // When/Then
        assertThat(publisher.getEventsForTopic("non-existent")).isEqualTo(0);
        assertThat(publisher.getEventsForPublisher("non-existent")).isEqualTo(0);
        assertThat(publisher.getEventsForTopicPublisher("topic", "publisher")).isEqualTo(0);
        assertThat(publisher.getEventsForType("non-existent")).isEqualTo(0);
    }
    
    @Test
    void shouldHandleEmptyTopicPublisherKey() {
        // When
        publisher.recordEvent("", "", "EventType");
        
        // Then
        assertThat(publisher.getTotalEventsProcessed()).isEqualTo(1);
        assertThat(publisher.getEventsForTopic("")).isEqualTo(1);
        assertThat(publisher.getEventsForPublisher("")).isEqualTo(1);
        assertThat(publisher.getEventsForTopicPublisher("", "")).isEqualTo(1);
    }
}
