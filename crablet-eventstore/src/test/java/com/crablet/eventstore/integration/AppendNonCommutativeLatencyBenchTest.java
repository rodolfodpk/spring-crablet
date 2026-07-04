package com.crablet.eventstore.integration;

import com.crablet.eventstore.AppendEvent;
import com.crablet.eventstore.StreamPosition;
import com.crablet.eventstore.Tag;
import com.crablet.eventstore.query.ProjectionResult;
import com.crablet.eventstore.query.Query;
import com.crablet.examples.wallet.events.DepositMade;
import com.crablet.examples.wallet.events.WalletOpened;
import com.crablet.examples.wallet.projections.WalletBalanceState;
import com.crablet.examples.wallet.projections.WalletBalanceStateProjector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rough latency guard for the SERIALIZABLE isolation bump in appendIf's non-commutative path
 * (uncontended, sequential case - the common case for most real usage). Manual comparison against
 * the pre-fix READ_COMMITTED baseline measured ~3.19ms/call vs. ~3.51ms/call with the fix
 * (~10% overhead) on a local Testcontainers instance. This test doesn't pin that exact number
 * (too environment-sensitive) - it only guards against a gross regression.
 */
@DisplayName("appendNonCommutative uncontended latency")
@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "spring.profiles.active=test")
class AppendNonCommutativeLatencyBenchTest extends com.crablet.test.AbstractPostgresEventStoreTest {

    @Test
    @DisplayName("N sequential uncontended appendNonCommutative calls stay within a generous latency ceiling")
    void sequentialUncontendedAppends() {
        int warmup = 20;
        int iterations = 200;

        for (int i = 0; i < warmup; i++) {
            runOne("warmup-" + UUID.randomUUID());
        }

        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            runOne("bench-" + UUID.randomUUID());
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        double avgMsPerCall = elapsedMs / (double) iterations;

        System.out.println("BENCH sequential uncontended appendNonCommutative: " + iterations
                + " calls in " + elapsedMs + "ms, avg=" + avgMsPerCall + "ms/call");

        // Generous ceiling - guards against a gross regression, not a precise perf assertion.
        assertThat(avgMsPerCall).isLessThan(50.0);
    }

    private void runOne(String walletId) {
        eventStore.appendCommutative(List.of(
                AppendEvent.builder("WalletOpened")
                        .tag("wallet_id", walletId)
                        .data(WalletOpened.of(walletId, "Bench", 1000))
                        .build()
        ));

        Query query = Query.forEventsAndTags(
                List.of("WalletOpened", "DepositMade"),
                List.of(new Tag("wallet_id", walletId))
        );
        ProjectionResult<WalletBalanceState> projection = eventStore.project(
                query, StreamPosition.zero(), WalletBalanceState.class, List.of(new WalletBalanceStateProjector())
        );
        StreamPosition position = Objects.requireNonNull(projection.streamPosition());

        eventStore.appendNonCommutative(
                List.of(AppendEvent.builder("DepositMade")
                        .tag("wallet_id", walletId)
                        .tag("deposit_id", "dep-" + walletId)
                        .data(DepositMade.of("dep-" + walletId, walletId, 500, 1500, "Bench deposit"))
                        .build()),
                query,
                position
        );
    }
}
