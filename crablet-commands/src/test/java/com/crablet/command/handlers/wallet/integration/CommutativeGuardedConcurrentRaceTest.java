package com.crablet.command.handlers.wallet.integration;

import com.crablet.command.CommandExecutor;
import com.crablet.eventstore.ClockProvider;
import com.crablet.eventstore.ConcurrencyException;
import com.crablet.eventstore.DCBErrorCode;
import com.crablet.examples.wallet.commands.CloseWalletCommand;
import com.crablet.examples.wallet.commands.CloseWalletCommandHandler;
import com.crablet.examples.wallet.commands.DepositCommand;
import com.crablet.examples.wallet.commands.DepositCommandHandler;
import com.crablet.examples.wallet.commands.OpenWalletCommand;
import com.crablet.examples.wallet.commands.OpenWalletCommandHandler;
import com.crablet.examples.wallet.period.WalletPeriodHelper;
import com.crablet.test.AbstractPostgresEventStoreTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for a genuinely-simultaneous race between a {@code CommutativeGuarded} deposit
 * and a {@code NonCommutative} wallet close.
 * <p>
 * {@code DepositCommandHandler}'s lifecycle guard and {@code CloseWalletCommandHandler}'s decision
 * model both use the identical {@code WalletQueryPatterns.walletLifecycleModel(walletId)} query -
 * so both derive the same advisory-lock key inside {@code append_events_if()} and correctly
 * serialize against each other, even though one goes through {@code CommandDecision.CommutativeGuarded}
 * and the other through {@code CommandDecision.NonCommutative}.
 * <p>
 * {@code CloseWalletCommandHandler} isn't a registered bean in {@code TestApplication} (only
 * {@code DepositCommandHandler} is), so both handlers are constructed directly and passed to the
 * two-arg {@code commandExecutor.execute(command, handler)} overload.
 */
@SpringBootTest(classes = com.crablet.command.integration.TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "spring.profiles.active=test")
class CommutativeGuardedConcurrentRaceTest extends AbstractPostgresEventStoreTest {

    @Autowired
    private CommandExecutor commandExecutor;

    @Autowired
    private WalletPeriodHelper periodHelper;

    @Autowired
    private ClockProvider clockProvider;

    @Test
    @DisplayName("Genuinely simultaneous Deposit vs CloseWallet: CloseWallet always succeeds, Deposit is sometimes correctly rejected as a guard violation")
    void depositRacingCloseResolvesConsistently() throws Exception {
        int iterations = 30;
        int guardViolations = 0;

        for (int i = 0; i < iterations; i++) {
            String walletId = "race-guard-wallet-" + UUID.randomUUID();
            commandExecutor.execute(OpenWalletCommand.of(walletId, "Racer", 1000), new OpenWalletCommandHandler());

            CountDownLatch startGate = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                Future<String> depositFuture = pool.submit(() -> attemptDeposit(walletId, startGate));
                Future<String> closeFuture = pool.submit(() -> attemptClose(walletId, startGate));
                startGate.countDown();

                String depositResult = depositFuture.get(10, TimeUnit.SECONDS);
                String closeResult = closeFuture.get(10, TimeUnit.SECONDS);

                assertThat(closeResult)
                        .as("iteration %d: CloseWallet has no competing event in its decision model from a deposit, must always succeed", i)
                        .isEqualTo("success");
                assertThat(depositResult)
                        .as("iteration %d: Deposit must either succeed or be rejected as a guard violation", i)
                        .isIn("success", "guard_violation");
                if ("guard_violation".equals(depositResult)) {
                    guardViolations++;
                }
            } finally {
                pool.shutdownNow();
            }
        }

        // Without the advisory-lock fix, the guard's plain SELECT-based conflict check almost
        // never observes a genuinely-concurrent WalletClosed commit (snapshot isolation
        // blindness), so GUARD_VIOLATION would fire in only a tiny fraction of iterations.
        // Asserting a meaningfully non-trivial rate proves the race is actually being serialized.
        assertThat(guardViolations)
                .as("expected a meaningful fraction of the %d iterations to hit the guard once the race is serialized", iterations)
                .isGreaterThan(iterations / 10);
    }

    private String attemptDeposit(String walletId, CountDownLatch startGate) {
        awaitGate(startGate);
        try {
            commandExecutor.execute(
                    DepositCommand.of("dep-" + UUID.randomUUID(), walletId, 100, "race deposit"),
                    new DepositCommandHandler(periodHelper));
            return "success";
        } catch (ConcurrencyException e) {
            return e.violation != null && e.violation.errorCode() == DCBErrorCode.GUARD_VIOLATION
                    ? "guard_violation" : "other_conflict:" + e.getMessage();
        } catch (Exception e) {
            return "error:" + e.getClass().getSimpleName();
        }
    }

    private String attemptClose(String walletId, CountDownLatch startGate) {
        awaitGate(startGate);
        try {
            commandExecutor.execute(CloseWalletCommand.of(walletId), new CloseWalletCommandHandler(clockProvider));
            return "success";
        } catch (Exception e) {
            return "error:" + e.getClass().getSimpleName();
        }
    }

    private void awaitGate(CountDownLatch gate) {
        try {
            gate.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
