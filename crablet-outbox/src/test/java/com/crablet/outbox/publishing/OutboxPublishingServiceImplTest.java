package com.crablet.outbox.publishing;

import com.crablet.eventstore.ClockProvider;
import com.crablet.eventstore.StoredEvent;
import com.crablet.outbox.OutboxPublisher;
import com.crablet.outbox.PublishException;
import com.crablet.outbox.publishers.GlobalStatisticsPublisher;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@DisplayName("OutboxPublishingServiceImpl Unit Tests")
class OutboxPublishingServiceImplTest {

    @Test
    @DisplayName("Should publish full batch when publisher prefers batch mode")
    void shouldPublishFullBatch_WhenPublisherPrefersBatchMode() {
        RecordingPublisher publisher = new RecordingPublisher("batch", OutboxPublisher.PublishMode.BATCH);
        OutboxPublishingServiceImpl service = serviceFor(publisher);
        List<StoredEvent> events = List.of(event(1), event(2));

        int published = service.publish("wallet", "batch", events);

        assertThat(published).isEqualTo(2);
        assertThat(publisher.calls).hasSize(1);
        assertThat(publisher.calls.get(0)).containsExactlyElementsOf(events);
    }

    @Test
    @DisplayName("Should publish one event per call when publisher prefers individual mode")
    void shouldPublishOneEventPerCall_WhenPublisherPrefersIndividualMode() {
        RecordingPublisher publisher = new RecordingPublisher("individual", OutboxPublisher.PublishMode.INDIVIDUAL);
        OutboxPublishingServiceImpl service = serviceFor(publisher);
        List<StoredEvent> events = List.of(event(1), event(2), event(3));

        int published = service.publish("wallet", "individual", events);

        assertThat(published).isEqualTo(3);
        assertThat(publisher.calls).hasSize(3);
        assertThat(publisher.calls.get(0)).containsExactly(events.get(0));
        assertThat(publisher.calls.get(1)).containsExactly(events.get(1));
        assertThat(publisher.calls.get(2)).containsExactly(events.get(2));
    }

    @Test
    @DisplayName("Should stop individual publishing at first publisher failure")
    void shouldStopIndividualPublishing_AtFirstPublisherFailure() {
        FailingIndividualPublisher publisher = new FailingIndividualPublisher("individual", 2);
        OutboxPublishingServiceImpl service = serviceFor(publisher);
        List<StoredEvent> events = List.of(event(1), event(2), event(3));

        assertThatThrownBy(() -> service.publish("wallet", "individual", events))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to publish events");

        assertThat(publisher.calls).hasSize(2);
        assertThat(publisher.calls.get(0)).containsExactly(events.get(0));
        assertThat(publisher.calls.get(1)).containsExactly(events.get(1));
    }

    private static OutboxPublishingServiceImpl serviceFor(OutboxPublisher publisher) {
        return new OutboxPublishingServiceImpl(
                Map.of(publisher.getName(), publisher),
                ClockProvider.systemDefault(),
                CircuitBreakerRegistry.ofDefaults(),
                mock(GlobalStatisticsPublisher.class),
                mock(ApplicationEventPublisher.class));
    }

    private static StoredEvent event(long position) {
        return new StoredEvent(
                "WalletOpened",
                List.of(),
                "{}".getBytes(StandardCharsets.UTF_8),
                "tx-" + position,
                position,
                Instant.parse("2026-01-01T00:00:00Z"));
    }

    private static final class RecordingPublisher implements OutboxPublisher {
        private final String name;
        private final PublishMode mode;
        private final List<List<StoredEvent>> calls = new ArrayList<>();

        private RecordingPublisher(String name, PublishMode mode) {
            this.name = name;
            this.mode = mode;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public void publishBatch(List<StoredEvent> events) throws PublishException {
            calls.add(List.copyOf(events));
        }

        @Override
        public boolean isHealthy() {
            return true;
        }

        @Override
        public PublishMode getPreferredMode() {
            return mode;
        }
    }

    private static final class FailingIndividualPublisher implements OutboxPublisher {
        private final String name;
        private final int failOnCall;
        private final List<List<StoredEvent>> calls = new ArrayList<>();

        private FailingIndividualPublisher(String name, int failOnCall) {
            this.name = name;
            this.failOnCall = failOnCall;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public void publishBatch(List<StoredEvent> events) throws PublishException {
            calls.add(List.copyOf(events));
            if (calls.size() == failOnCall) {
                throw new PublishException("downstream unavailable");
            }
        }

        @Override
        public boolean isHealthy() {
            return true;
        }

        @Override
        public PublishMode getPreferredMode() {
            return PublishMode.INDIVIDUAL;
        }
    }
}
