package com.crablet.eventstore.integration;

import com.crablet.eventstore.AppendEvent;
import com.crablet.eventstore.ConcurrencyException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Benchmark: idempotency check latency under concurrent load.
 *
 * Establishes a timing baseline for appendIdempotent using the crablet_events.tags GIN index.
 * Run with -Dgroups=benchmark to include in a benchmark-only suite.
 * Output: P50 and P99 of single-writer idempotency check duration,
 * and success/contention counts under 20 concurrent writers sharing the same tag.
 */
@Tag("benchmark")
@DisplayName("crablet_events.tags GIN idempotency benchmark")
@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "spring.profiles.active=test")
class EventsTagsIdempotencyBenchmarkTest extends AbstractCrabletTest {

    private static final int SEED_EVENTS    = 10_000;
    private static final int SAMPLE_COUNT   = 100;
    private static final int CONCURRENT_WRITERS = 20;

    @Test
    @DisplayName("P50/P99 of single-writer idempotency check against 10k events")
    void singleWriterIdempotencyLatency() {
        seedEvents(SEED_EVENTS);

        List<Long> samples = new ArrayList<>(SAMPLE_COUNT);

        for (int i = 0; i < SAMPLE_COUNT; i++) {
            String tag = "bench-" + UUID.randomUUID();
            long start = System.nanoTime();
            eventStore.appendIdempotent(
                List.of(AppendEvent.builder("BenchEvent").tag("bench_id", tag).data("{}").build()),
                "BenchEvent", "bench_id", tag
            );
            samples.add(System.nanoTime() - start);
        }

        Collections.sort(samples);
        long p50 = samples.get(SAMPLE_COUNT / 2) / 1_000_000;
        long p99 = samples.get((int) (SAMPLE_COUNT * 0.99)) / 1_000_000;

        System.out.printf("[benchmark] idempotency single-writer (%d events seed): P50=%dms P99=%dms%n",
                SEED_EVENTS, p50, p99);

        assertThat(p99).as("P99 idempotency check should complete within 2s").isLessThan(2_000);
    }

    @Test
    @DisplayName("Concurrent idempotency: 20 writers sharing the same tag against 10k events")
    void concurrentWritersSharedTag() throws InterruptedException {
        seedEvents(SEED_EVENTS);

        String sharedTag = "shared-" + UUID.randomUUID();
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger duplicates = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(CONCURRENT_WRITERS);
        CountDownLatch done  = new CountDownLatch(CONCURRENT_WRITERS);

        long start = System.nanoTime();

        try (ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_WRITERS)) {
            for (int i = 0; i < CONCURRENT_WRITERS; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    try { ready.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    try {
                        eventStore.appendIdempotent(
                            List.of(AppendEvent.builder("BenchEvent").tag("bench_id", sharedTag).data("{}").build()),
                            "BenchEvent", "bench_id", sharedTag
                        );
                        successes.incrementAndGet();
                    } catch (ConcurrencyException e) {
                        duplicates.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }
            done.await(30, TimeUnit.SECONDS);
        }

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        System.out.printf("[benchmark] idempotency concurrent (%d writers, %d events seed): " +
                        "successes=%d duplicates=%d elapsed=%dms%n",
                CONCURRENT_WRITERS, SEED_EVENTS, successes.get(), duplicates.get(), elapsedMs);

        assertThat(successes.get()).as("exactly one writer should succeed").isEqualTo(1);
        assertThat(duplicates.get()).as("remaining writers should be rejected as duplicates")
                .isEqualTo(CONCURRENT_WRITERS - 1);
    }

    private void seedEvents(int count) {
        int batchSize = 500;
        for (int i = 0; i < count; i += batchSize) {
            List<AppendEvent> batch = new ArrayList<>(batchSize);
            for (int j = i; j < Math.min(i + batchSize, count); j++) {
                batch.add(AppendEvent.builder("SeedEvent")
                        .tag("seed_id", "seed-" + j)
                        .tag("category", "cat-" + (j % 10))
                        .data("{}")
                        .build());
            }
            eventStore.appendCommutative(batch);
        }
    }
}
