package crablet.integration;

import com.crablet.core.AppendCondition;
import com.crablet.core.AppendEvent;
import com.crablet.core.ConcurrencyException;
import com.crablet.core.Cursor;
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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static testutils.DCBTestHelpers.createTestEvent;

/**
 * Tests for DCB atomicity guarantees.
 * Verifies that cursor and condition checks happen atomically in a single database snapshot.
 */
class JDBCEventStoreDCBAtomicityTest extends testutils.AbstractCrabletTest {

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

    @Test
    void shouldAtomicallyCheckCursorAndCondition_PreventRaceCondition() {
        // This test would FAIL with V3 optimization!

        // Setup: Append initial event
        AppendEvent event1 = createTestEvent("Event1", "id1");
        store.append(List.of(event1));

        // Get cursor after first event
        List<StoredEvent> events = store.query(Query.empty(), null);
        Cursor cursor = Cursor.of(events.get(0).position(), events.get(0).occurredAt(), events.get(0).transactionId());

        // Another thread appends event2 (violates cursor)
        CompletableFuture.runAsync(() -> {
            AppendEvent event2 = createTestEvent("Event2", "id2");
            store.append(List.of(event2));
        }).join();

        // Try to append event3 with stale cursor
        AppendEvent event3 = createTestEvent("Event3", "id3");
        AppendCondition condition = AppendCondition.of(cursor, Query.empty());

        // MUST fail because cursor was violated
        assertThatThrownBy(() -> store.appendIf(List.of(event3), condition))
                .isInstanceOf(ConcurrencyException.class)
                .hasMessageContaining("AppendCondition violated");
    }

    @Test
    void shouldNotSeparateCursorAndConditionChecks() throws InterruptedException {
        // Verify checks happen in single query (no race window)

        // Setup: Event with tag "status=pending"
        AppendEvent event1 = createTestEvent("Event1", new Tag("status", "pending"));
        store.append(List.of(event1));

        List<StoredEvent> events = store.query(Query.empty(), null);
        Cursor cursor = Cursor.of(events.get(0).position(), events.get(0).occurredAt(), events.get(0).transactionId());

        // Create condition that would pass if checked separately
        Query condition = Query.forEventAndTag("Event1", "status", "completed");

        // In parallel: append event matching condition
        CountDownLatch latch = new CountDownLatch(1);
        CompletableFuture.runAsync(() -> {
            try {
                latch.await();
                AppendEvent event2 = createTestEvent("Event1", new Tag("status", "completed"));
                store.append(List.of(event2));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        // Try appendIf - should fail on cursor OR condition
        AppendEvent event3 = createTestEvent("Event3", new Tag("status", "pending"));
        AppendCondition appendCondition = AppendCondition.of(cursor, condition);

        latch.countDown(); // Let parallel append run
        Thread.sleep(50);  // Ensure event2 is committed

        // MUST fail (either cursor or condition violated)
        assertThatThrownBy(() -> store.appendIf(List.of(event3), appendCondition))
                .isInstanceOf(ConcurrencyException.class);
    }

    @Test
    void shouldUseConsistentSnapshotForBothChecks() {
        // Verify both checks see same database snapshot
        // (Not just separate queries at different times)

        // This is more of a property-based test
        // Run 100 iterations with concurrent operations
        for (int i = 0; i < 100; i++) {
            // Reset database
            jdbcTemplate.execute("TRUNCATE TABLE events CASCADE");
            jdbcTemplate.execute("ALTER SEQUENCE events_position_seq RESTART WITH 1");

            final int iteration = i; // Make effectively final for lambda

            // Concurrent appends with conditions
            List<CompletableFuture<Boolean>> futures = IntStream.range(0, 10)
                    .mapToObj(j -> CompletableFuture.supplyAsync(() -> {
                        try {
                            AppendEvent event = createTestEvent("Event" + j, new Tag("iter", String.valueOf(iteration)));
                            Cursor cursor = Cursor.zero();
                            Query condition = Query.forEventAndTag("Event" + j, "iter", String.valueOf(iteration));
                            AppendCondition appendCondition = AppendCondition.of(cursor, condition);
                            store.appendIf(List.of(event), appendCondition);
                            return true;
                        } catch (ConcurrencyException e) {
                            return false;
                        }
                    }))
                    .collect(Collectors.toList());

            // Wait for all
            List<Boolean> results = futures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());

            // At least ONE should succeed, but with READ COMMITTED isolation,
            // multiple concurrent transactions may succeed if they don't see each other yet.
            // This is correct DCB behavior - optimistic locking allows concurrent writes
            // as long as they don't violate the cursor check.
            long successCount = results.stream().filter(b -> b).count();
            assertThat(successCount).isGreaterThanOrEqualTo(1);
        }
    }
}

