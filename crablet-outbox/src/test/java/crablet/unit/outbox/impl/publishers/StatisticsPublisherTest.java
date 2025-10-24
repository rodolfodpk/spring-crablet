package crablet.unit.outbox.impl.publishers;

import com.crablet.core.StoredEvent;
import com.crablet.outbox.impl.publishers.StatisticsPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatisticsPublisherTest {

    @Mock
    private Environment environment;

    private StatisticsPublisher publisher;

    @BeforeEach
    void setUp() {
        when(environment.getProperty("crablet.outbox.publishers.statistics.log-interval-seconds", "10"))
            .thenReturn("5");
        
        publisher = new StatisticsPublisher(environment);
    }

    @Test
    void shouldReturnCorrectName() {
        // When
        String name = publisher.getName();

        // Then
        assertThat(name).isEqualTo("StatisticsPublisher");
    }

    @Test
    void shouldReturnHealthy() {
        // When
        boolean healthy = publisher.isHealthy();

        // Then
        assertThat(healthy).isTrue();
    }

    @Test
    void shouldProcessSingleEvent() throws Exception {
        // Given
        StoredEvent event = createStoredEvent("TestEvent", 1L);

        // When
        publisher.publishBatch(List.of(event));

        // Then
        assertThat(publisher.isHealthy()).isTrue();
    }

    @Test
    void shouldProcessMultipleEvents() throws Exception {
        // Given
        List<StoredEvent> events = List.of(
            createStoredEvent("EventType1", 1L),
            createStoredEvent("EventType2", 2L),
            createStoredEvent("EventType1", 3L)
        );

        // When
        publisher.publishBatch(events);

        // Then
        assertThat(publisher.isHealthy()).isTrue();
    }

    @Test
    void shouldProcessEmptyBatch() throws Exception {
        // Given
        List<StoredEvent> events = List.of();

        // When
        publisher.publishBatch(events);

        // Then
        assertThat(publisher.isHealthy()).isTrue();
    }

    @Test
    void shouldUseDefaultLogIntervalWhenNotConfigured() {
        // Given
        when(environment.getProperty("crablet.outbox.publishers.statistics.log-interval-seconds", "10"))
            .thenReturn("10"); // Use default value instead of null

        // When
        StatisticsPublisher publisherWithDefault = new StatisticsPublisher(environment);

        // Then
        assertThat(publisherWithDefault.getName()).isEqualTo("StatisticsPublisher");
        assertThat(publisherWithDefault.isHealthy()).isTrue();
    }

    // Helper method
    private StoredEvent createStoredEvent(String eventType, Long position) {
        return new StoredEvent(
            eventType,
            List.of(), // Empty tags list
            "test data".getBytes(),
            "tx-" + position,
            position,
            Instant.now()
        );
    }
}
