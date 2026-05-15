package com.crablet.eventstore.integration;

import com.crablet.eventstore.AppendEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Benchmark: write-path throughput with tag-dense events after V7/V8 event_tags maintenance.
 *
 * Establishes a timing baseline for appendCommutative with 4 tags per event —
 * the worst-case write amplification scenario (4 extra event_tags rows per event).
 * Run with -Dgroups=benchmark to include in a benchmark-only suite.
 * Output: P50 and P99 of single-event append duration.
 */
@Tag("benchmark")
@DisplayName("event_tags append write-path benchmark")
@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "spring.profiles.active=test")
class EventTagsAppendBenchmarkTest extends AbstractCrabletTest {

    private static final int SAMPLE_COUNT = 200;
    private static final int TAGS_PER_EVENT = 4;

    @Test
    @DisplayName("P50/P99 of single-event append with 4 tags (write amplification baseline)")
    void singleEventAppendWithDenseTags() {
        List<Long> samples = new ArrayList<>(SAMPLE_COUNT);

        for (int i = 0; i < SAMPLE_COUNT; i++) {
            String id = UUID.randomUUID().toString();
            long start = System.nanoTime();
            eventStore.appendCommutative(List.of(
                AppendEvent.builder("BenchWriteEvent")
                    .tag("entity_id", id)
                    .tag("tenant_id", "tenant-" + (i % 10))
                    .tag("category", "cat-" + (i % 5))
                    .tag("region", "region-" + (i % 3))
                    .data("{}")
                    .build()
            ));
            samples.add(System.nanoTime() - start);
        }

        Collections.sort(samples);
        long p50 = samples.get(SAMPLE_COUNT / 2) / 1_000_000;
        long p99 = samples.get((int) (SAMPLE_COUNT * 0.99)) / 1_000_000;

        System.out.printf("[benchmark] append write %d-tag event (%d samples): P50=%dms P99=%dms%n",
                TAGS_PER_EVENT, SAMPLE_COUNT, p50, p99);

        assertThat(p99).as("P99 single-event append should complete within 2s").isLessThan(2_000);
    }
}
