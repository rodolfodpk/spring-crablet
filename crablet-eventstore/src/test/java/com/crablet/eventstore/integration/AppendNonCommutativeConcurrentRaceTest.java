package com.crablet.eventstore.integration;

import com.crablet.eventstore.AppendEvent;
import com.crablet.eventstore.ConcurrencyException;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for a genuinely-simultaneous appendNonCommutative race.
 *
 * <p>Existing coverage (EventStoreTest.shouldThrowConcurrencyExceptionWhenAppendIfWithStaleStreamPosition)
 * only exercises a *staggered* race: writer A's appendCommutative fully commits before writer B's
 * appendNonCommutative even starts its conflict check. Under that ordering, PostgreSQL's snapshot
 * isolation detects the conflict on its own, per DCB_AND_CRABLET.md's example timeline.
 *
 * <p>This test instead fires two threads at appendNonCommutative with the *same* stale StreamPosition
 * genuinely concurrently (via a CountDownLatch start gate). Under plain READ_COMMITTED, this race
 * let both transactions succeed ~95% of the time in manual verification, because snapshot isolation's
 * conflict check (transaction_id < pg_snapshot_xmin(...)) cannot see a peer's not-yet-committed row.
 * appendIf now bumps to SERIALIZABLE for this exact case (see EventStoreImpl.appendIf), which closes
 * the window via Postgres's SSI - this test locks in that guarantee.
 */
@DisplayName("appendNonCommutative concurrent race regression")
@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "spring.profiles.active=test")
class AppendNonCommutativeConcurrentRaceTest extends com.crablet.test.AbstractPostgresEventStoreTest {

    @Test
    @DisplayName("Two genuinely simultaneous appendNonCommutative calls against the same stale position: exactly one wins")
    void concurrentRaceAgainstSameStalePositionResolvesToExactlyOneWinner() throws Exception {
        final int iterations = 20;

        for (int i = 0; i < iterations; i++) {
            String walletId = "race-wallet-" + UUID.randomUUID();

            eventStore.appendCommutative(List.of(
                    AppendEvent.builder("WalletOpened")
                            .tag("wallet_id", walletId)
                            .data(WalletOpened.of(walletId, "Racer", 1000))
                            .build()
            ));

            Query query = Query.forEventsAndTags(
                    List.of("WalletOpened", "DepositMade", "WithdrawalMade"),
                    List.of(new Tag("wallet_id", walletId))
            );
            ProjectionResult<WalletBalanceState> projection = eventStore.project(
                    query,
                    StreamPosition.zero(),
                    WalletBalanceState.class,
                    List.of(new WalletBalanceStateProjector())
            );
            StreamPosition staleStreamPosition = Objects.requireNonNull(projection.streamPosition());

            int iteration = i;
            CountDownLatch startGate = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                Future<String> f1 = pool.submit(() -> attempt(walletId, "dep-a-" + iteration, staleStreamPosition, query, startGate));
                Future<String> f2 = pool.submit(() -> attempt(walletId, "dep-b-" + iteration, staleStreamPosition, query, startGate));
                startGate.countDown();

                String r1 = f1.get(10, TimeUnit.SECONDS);
                String r2 = f2.get(10, TimeUnit.SECONDS);

                long successCount = List.of(r1, r2).stream().filter("success"::equals).count();
                assertThat(successCount)
                        .as("iteration %d: exactly one of [%s, %s] should succeed", i, r1, r2)
                        .isEqualTo(1);
            } finally {
                pool.shutdownNow();
            }
        }
    }

    private String attempt(String walletId, String depositId, StreamPosition staleStreamPosition, Query query, CountDownLatch startGate) {
        try {
            startGate.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "interrupted";
        }
        try {
            eventStore.appendNonCommutative(
                    List.of(AppendEvent.builder("DepositMade")
                            .tag("wallet_id", walletId)
                            .tag("deposit_id", depositId)
                            .data(DepositMade.of(depositId, walletId, 500, 1500, "Race deposit"))
                            .build()),
                    query,
                    staleStreamPosition
            );
            return "success";
        } catch (ConcurrencyException e) {
            return "conflict";
        } catch (Exception e) {
            return "error:" + e.getClass().getSimpleName();
        }
    }
}
