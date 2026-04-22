package com.crablet.eventpoller.internal.sharedfetch;

import com.crablet.eventstore.StoredEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Stream gatherer event windowing")
class StreamGathererWindowingTest {

    @Test
    @DisplayName("Fixed windows preserve event order and include trailing partial window")
    void fixedWindowsPreserveOrderAndTrailingPartialWindow() {
        List<List<Long>> windows = StoredEventWindows.fixed(events(1, 2, 3, 4, 5), 2)
                .stream()
                .map(window -> window.stream().map(StoredEvent::position).toList())
                .toList();

        assertThat(windows).containsExactly(
                List.of(1L, 2L),
                List.of(3L, 4L),
                List.of(5L));
    }

    @Test
    @DisplayName("Fixed windows are immutable")
    void fixedWindowsAreImmutable() {
        List<StoredEvent> window = StoredEventWindows.fixed(events(1), 1).getFirst();

        assertThatThrownBy(() -> window.add(event(2)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Sliding windows support adjacent event analysis")
    void slidingWindowsSupportAdjacentEventAnalysis() {
        List<List<Long>> adjacentGaps = StoredEventWindows.adjacentPairs(events(1, 2, 5, 6))
                .stream()
                .filter(window -> window.get(1).position() - window.get(0).position() > 1)
                .map(window -> window.stream().map(StoredEvent::position).toList())
                .toList();

        assertThat(adjacentGaps).containsExactly(List.of(2L, 5L));
    }

    private static StoredEvent event(long position) {
        return new StoredEvent(
                "TypeA",
                List.of(),
                "{}".getBytes(StandardCharsets.UTF_8),
                "tx-" + position,
                position,
                Instant.EPOCH);
    }

    private static List<StoredEvent> events(long... positions) {
        return java.util.stream.LongStream.of(positions)
                .mapToObj(StreamGathererWindowingTest::event)
                .toList();
    }
}
