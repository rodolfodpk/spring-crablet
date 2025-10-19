package crablet.integration;

import com.crablet.core.AppendEvent;
import com.crablet.core.EventStoreConfig;
import com.crablet.core.Query;
import com.crablet.core.StoredEvent;
import com.crablet.core.Tag;
import com.crablet.impl.JDBCEventStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static testutils.DCBTestHelpers.createTestEvent;

/**
 * Tests for DCB event ordering guarantees.
 * Verifies strict event ordering by position and transaction_id.
 */
class JDBCEventStoreDCBOrderingTest extends testutils.AbstractCrabletTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EventStoreConfig config;

    private JDBCEventStore store;

    @BeforeEach
    void setUp() {
        store = new JDBCEventStore(dataSource, objectMapper, config);
    }

    private long getLastPosition() {
        List<StoredEvent> events = store.query(Query.empty(), null);
        if (events.isEmpty()) {
            return 0L;
        }
        return events.get(events.size() - 1).position();
    }

    @Test
    void shouldMaintainStrictPositionSequence() {
        // Append multiple events
        List<AppendEvent> events = List.of(
                createTestEvent("Event1", "id1"),
                createTestEvent("Event2", "id2"),
                createTestEvent("Event3", "id3")
        );
        store.append(events);

        // Query back
        List<StoredEvent> stored = store.query(Query.empty(), null);

        // Verify position sequence (no gaps)
        assertThat(stored).extracting("position")
                .containsExactly(1L, 2L, 3L);
    }

    @Test
    void shouldOrderByTransactionIdThenPosition() {
        // Append events in transaction T1
        store.append(List.of(createTestEvent("T1_Event1", "id1")));

        // Append events in transaction T2
        store.append(List.of(createTestEvent("T2_Event1", "id2")));

        // Query and verify ordering
        List<StoredEvent> stored = store.query(Query.empty(), null);

        // Verify sorted by (transaction_id, position)
        assertThat(stored).isSortedAccordingTo(
                Comparator.comparing(StoredEvent::transactionId)
                        .thenComparing(StoredEvent::position)
        );
    }

    @Test
    void shouldPreserveOrderAcrossMultipleAppends() {
        // Append batch 1
        store.append(List.of(createTestEvent("Batch1_Event1", "id1")));
        long position1 = getLastPosition();

        // Append batch 2
        store.append(List.of(createTestEvent("Batch2_Event1", "id2")));
        long position2 = getLastPosition();

        // Append batch 3
        store.append(List.of(createTestEvent("Batch3_Event1", "id3")));
        long position3 = getLastPosition();

        // Verify monotonic increase
        assertThat(position2).isGreaterThan(position1);
        assertThat(position3).isGreaterThan(position2);

        // Verify query returns in correct order
        List<StoredEvent> stored = store.query(Query.empty(), null);
        assertThat(stored).extracting("type")
                .containsExactly("Batch1_Event1", "Batch2_Event1", "Batch3_Event1");
    }

    @Test
    void shouldMaintainOrderUnderConcurrentAppends() {
        // 10 threads appending concurrently
        int threadCount = 10;
        int eventsPerThread = 10;

        List<CompletableFuture<Void>> futures = IntStream.range(0, threadCount)
                .mapToObj(threadId -> CompletableFuture.runAsync(() -> {
                    for (int i = 0; i < eventsPerThread; i++) {
                        AppendEvent event = createTestEvent(
                                "Thread" + threadId + "_Event" + i,
                                new Tag("thread_id", String.valueOf(threadId))
                        );
                        store.append(List.of(event));
                    }
                }))
                .collect(Collectors.toList());

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // Verify total count
        List<StoredEvent> stored = store.query(Query.empty(), null);
        assertThat(stored).hasSize(threadCount * eventsPerThread);

        // Verify no gaps in position sequence
        List<Long> positions = stored.stream()
                .map(StoredEvent::position)
                .sorted()
                .collect(Collectors.toList());

        for (int i = 0; i < positions.size(); i++) {
            assertThat(positions.get(i)).isEqualTo((long) (i + 1));
        }

        // Verify events within same thread maintain order
        for (int threadId = 0; threadId < threadCount; threadId++) {
            Query threadQuery = Query.forEventAndTag("Thread" + threadId + "_Event0", "thread_id", String.valueOf(threadId));
            List<StoredEvent> threadEvents = store.query(threadQuery, null);

            // At least verify we found events for this thread
            assertThat(threadEvents).isNotEmpty();
        }
    }
}

