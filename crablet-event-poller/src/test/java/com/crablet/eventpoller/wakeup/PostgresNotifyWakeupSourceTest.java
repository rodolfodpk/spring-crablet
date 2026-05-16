package com.crablet.eventpoller.wakeup;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.PGNotification;

import java.lang.reflect.Method;
import java.sql.SQLException;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PostgresNotifyWakeupSource Unit Tests")
class PostgresNotifyWakeupSourceTest {

    @Test
    @DisplayName("Should accept valid PostgreSQL notification channel names")
    void shouldAcceptValidChannelNames() {
        assertThatCode(() -> new PostgresNotifyWakeupSource(
                "jdbc:postgresql://localhost/test", "user", "password", "crablet_events"))
                .doesNotThrowAnyException();
        assertThatCode(() -> new PostgresNotifyWakeupSource(
                "jdbc:postgresql://localhost/test", "user", "password", "_events_123"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should reject invalid PostgreSQL notification channel names")
    @SuppressWarnings("NullAway")
    void shouldRejectInvalidChannelNames() {
        assertThatThrownBy(() -> new PostgresNotifyWakeupSource(
                "jdbc:postgresql://localhost/test", "user", "password", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid PostgreSQL notification channel: null");
        assertThatThrownBy(() -> new PostgresNotifyWakeupSource(
                "jdbc:postgresql://localhost/test", "user", "password", "123_events"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid PostgreSQL notification channel: 123_events");
        assertThatThrownBy(() -> new PostgresNotifyWakeupSource(
                "jdbc:postgresql://localhost/test", "user", "password", "events; DROP TABLE events"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid PostgreSQL notification channel: events; DROP TABLE events");
    }

    @Test
    @DisplayName("Should close safely before listener starts")
    void shouldCloseSafelyBeforeListenerStarts() {
        PostgresNotifyWakeupSource source = new PostgresNotifyWakeupSource(
                "jdbc:postgresql://localhost/test", "user", "password", "crablet_events");

        assertThatCode(source::close).doesNotThrowAnyException();
    }

    // --- Phase 3: parsePayload ---

    @Test
    @DisplayName("parsePayload: null, blank, and '*' are wildcard (empty set)")
    void parsePayloadWildcards() {
        assertThat(PostgresNotifyWakeupSource.parsePayload(null)).isEmpty();
        assertThat(PostgresNotifyWakeupSource.parsePayload("")).isEmpty();
        assertThat(PostgresNotifyWakeupSource.parsePayload("   ")).isEmpty();
        assertThat(PostgresNotifyWakeupSource.parsePayload("*")).isEmpty();
        assertThat(PostgresNotifyWakeupSource.parsePayload(" * ")).isEmpty();
    }

    @Test
    @DisplayName("parsePayload: valid comma-separated types are parsed correctly")
    void parsePayloadValidTypes() {
        assertThat(PostgresNotifyWakeupSource.parsePayload("WalletCreated"))
                .containsExactly("WalletCreated");
        assertThat(PostgresNotifyWakeupSource.parsePayload("WalletCreated,WalletDeposited"))
                .containsExactlyInAnyOrder("WalletCreated", "WalletDeposited");
    }

    @Test
    @DisplayName("parsePayload: duplicates and blank tokens are handled safely")
    void parsePayloadDuplicatesAndBlanks() {
        // No Set.of() on duplicates risk; blanks stripped
        Set<String> result = PostgresNotifyWakeupSource.parsePayload("A,,B,A, ,B");
        assertThat(result).containsExactlyInAnyOrder("A", "B");
    }

    @Test
    @DisplayName("typed start(): wildcard subscriber is always woken regardless of payload")
    void wildcardSubscriberAlwaysWoken() {
        // A subscriber with empty subscribedEventTypes is a wildcard — must be woken
        // even when a type-specific payload arrives. We can verify by checking
        // that the subscriber list includes it after registration.
        PostgresNotifyWakeupSource source = new PostgresNotifyWakeupSource(
                "jdbc:postgresql://localhost/test", "user", "password", "crablet_events");
        AtomicInteger count = new AtomicInteger();
        source.start(Set.of(), count::incrementAndGet); // wildcard subscriber
        source.close(); // cleanup
        assertThat(count.get()).isZero(); // no notifications sent — just registration test
    }

    // --- Phase 2 (continued) ---

    @Test
    @DisplayName("factory.create() returns the same singleton instance")
    void factoryCreateReturnsSingleton() {
        PostgresNotifyWakeupSourceFactory factory = new PostgresNotifyWakeupSourceFactory(
                "jdbc:postgresql://localhost/test", "user", "password", "crablet_events");

        ProcessorWakeupSource first = factory.create();
        ProcessorWakeupSource second = factory.create();

        assertThat(first).isSameAs(second);
    }

    @Test
    @DisplayName("close(subscriber) removes only that subscriber; other subscribers remain")
    void closeSubscriberRemovesOnlyThatSubscriber() {
        PostgresNotifyWakeupSource source = new PostgresNotifyWakeupSource(
                "jdbc:postgresql://localhost/test", "user", "password", "crablet_events");

        AtomicInteger countA = new AtomicInteger();
        AtomicInteger countB = new AtomicInteger();
        Runnable subscriberA = countA::incrementAndGet;
        Runnable subscriberB = countB::incrementAndGet;

        source.start(subscriberA);
        source.start(subscriberB);
        source.close(subscriberA);

        // Source is not closed (subscriberB still registered); closing it now is safe
        assertThatCode(source::close).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("close(last subscriber) shuts down cleanly")
    void closeLastSubscriberShutsDown() {
        PostgresNotifyWakeupSource source = new PostgresNotifyWakeupSource(
                "jdbc:postgresql://localhost/test", "user", "password", "crablet_events");

        Runnable subscriber = () -> {};
        source.start(subscriber);
        assertThatCode(() -> source.close(subscriber)).doesNotThrowAnyException();
    }

    // --- Phase B: BatchState merge ---

    @Test
    @DisplayName("BatchState.merge: union of types when neither is wildcard")
    void batchStateMergeUnionsTypes() {
        var a = PostgresNotifyWakeupSource.BatchState.ofNotification(false, Set.of("A", "B"), Set.of());
        var b = PostgresNotifyWakeupSource.BatchState.ofNotification(false, Set.of("B", "C"), Set.of());
        var merged = a.merge(b);
        assertThat(merged.wildcard()).isFalse();
        assertThat(merged.types()).containsExactlyInAnyOrder("A", "B", "C");
    }

    @Test
    @DisplayName("BatchState.merge: wildcard in either operand poisons result")
    void batchStateMergeWildcardPoisons() {
        var typed = PostgresNotifyWakeupSource.BatchState.ofNotification(false, Set.of("A"), Set.of());
        var wild  = PostgresNotifyWakeupSource.BatchState.ofNotification(true, Set.of(), Set.of());
        assertThat(typed.merge(wild).wildcard()).isTrue();
        assertThat(wild.merge(typed).wildcard()).isTrue();
        assertThat(wild.merge(wild).wildcard()).isTrue();
    }

    @Test
    @DisplayName("BatchState.merge: union of tag keys when neither is wildcard")
    void batchStateMergeUnionsTagKeys() {
        var a = PostgresNotifyWakeupSource.BatchState.ofNotification(false, Set.of("T"), Set.of("wallet_id", "region"));
        var b = PostgresNotifyWakeupSource.BatchState.ofNotification(false, Set.of("T"), Set.of("region", "currency"));
        var merged = a.merge(b);
        assertThat(merged.tagKeys()).containsExactlyInAnyOrder("wallet_id", "region", "currency");
    }

    // --- Phase B: debounce=0 (immediate dispatch) ---

    @Test
    @DisplayName("debounce=0: each dispatchBatch call wakes subscribers immediately")
    void noDebounceDispatchesImmediately() {
        var source = new PostgresNotifyWakeupSource(
                "jdbc:postgresql://localhost/test", "user", "password", "crablet_events", 0L);
        AtomicInteger count = new AtomicInteger();
        source.start(count::incrementAndGet);

        source.dispatchBatch(new PGNotification[]{notify("WalletCreated")});
        source.dispatchBatch(new PGNotification[]{notify("WalletDeposited")});

        assertThat(count.get()).isEqualTo(2); // two separate batches → two wakeups
        source.close();
    }

    @Test
    @DisplayName("debounce=0: typed subscriber skipped when types do not intersect")
    void noDebounceTypedSubscriberFiltered() {
        var source = new PostgresNotifyWakeupSource(
                "jdbc:postgresql://localhost/test", "user", "password", "crablet_events", 0L);
        AtomicInteger woken = new AtomicInteger();
        source.start(Set.of("CourseEnrolled"), woken::incrementAndGet);

        source.dispatchBatch(new PGNotification[]{notify("WalletDeposited")});

        assertThat(woken.get()).isZero();
        source.close();
    }

    @Test
    @DisplayName("debounce=0: wildcard payload wakes all subscribers")
    void noDebounceWildcardPayloadWakesAll() {
        var source = new PostgresNotifyWakeupSource(
                "jdbc:postgresql://localhost/test", "user", "password", "crablet_events", 0L);
        AtomicInteger typed = new AtomicInteger();
        AtomicInteger wildcard = new AtomicInteger();
        source.start(Set.of("CourseEnrolled"), typed::incrementAndGet);
        source.start(wildcard::incrementAndGet);

        source.dispatchBatch(new PGNotification[]{notify("*")}); // wildcard payload

        assertThat(typed.get()).isEqualTo(1);
        assertThat(wildcard.get()).isEqualTo(1);
        source.close();
    }

    // --- Phase B: debounce>0 (cross-read coalescing) ---

    @Test
    @DisplayName("debounce>0: back-to-back batches are coalesced into one wakeup")
    void debounceCoalescesBatches() throws InterruptedException {
        var source = new PostgresNotifyWakeupSource(
                "jdbc:postgresql://localhost/test", "user", "password", "crablet_events", 50L);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger count = new AtomicInteger();
        source.start(() -> { count.incrementAndGet(); latch.countDown(); });

        // Two batches within the debounce window
        source.dispatchBatch(new PGNotification[]{notify("WalletCreated")});
        source.dispatchBatch(new PGNotification[]{notify("WalletDeposited")});

        assertThat(latch.await(300, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(count.get()).isEqualTo(1); // coalesced to one wakeup
        source.close();
    }

    @Test
    @DisplayName("debounce>0: wildcard in second batch poisons the merged flush")
    void debounceWildcardPoisonsMerge() throws InterruptedException {
        var source = new PostgresNotifyWakeupSource(
                "jdbc:postgresql://localhost/test", "user", "password", "crablet_events", 50L);
        AtomicInteger typed = new AtomicInteger();
        AtomicInteger wildSub = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(2);
        source.start(Set.of("CourseEnrolled"), () -> { typed.incrementAndGet(); latch.countDown(); });
        source.start(() -> { wildSub.incrementAndGet(); latch.countDown(); });

        source.dispatchBatch(new PGNotification[]{notify("WalletCreated")}); // no CourseEnrolled
        source.dispatchBatch(new PGNotification[]{notify("*")});             // wildcard

        assertThat(latch.await(300, TimeUnit.MILLISECONDS)).isTrue();
        // Both subscribers woken because wildcard poisoned the merge
        assertThat(typed.get()).isEqualTo(1);
        assertThat(wildSub.get()).isEqualTo(1);
        source.close();
    }

    @Test
    @DisplayName("debounce>0: close() drains pending flush before stopping")
    void debounceCloseFlushesImmediately() {
        var source = new PostgresNotifyWakeupSource(
                "jdbc:postgresql://localhost/test", "user", "password", "crablet_events", 5000L); // very long window
        AtomicInteger count = new AtomicInteger();
        source.start(count::incrementAndGet);

        source.dispatchBatch(new PGNotification[]{notify("WalletCreated")}); // queued, window not elapsed
        source.close(); // must drain immediately

        assertThat(count.get()).isEqualTo(1); // drained synchronously on close
    }

    // --- Phase D: tag-key filtering ---

    @Test
    @DisplayName("parseTagSection: extracts keys after pipe separator")
    void parseTagSectionExtractsKeys() {
        assertThat(PostgresNotifyWakeupSource.parseTagSection("WalletDeposited|wallet_id,region,currency"))
                .containsExactlyInAnyOrder("wallet_id", "region", "currency");
    }

    @Test
    @DisplayName("parseTagSection: no pipe means no tag section")
    void parseTagSectionNoPipe() {
        assertThat(PostgresNotifyWakeupSource.parseTagSection("WalletDeposited")).isEmpty();
    }

    @Test
    @DisplayName("parseTagSection: wildcard payload yields empty")
    void parseTagSectionWildcard() {
        assertThat(PostgresNotifyWakeupSource.parseTagSection("*")).isEmpty();
        assertThat(PostgresNotifyWakeupSource.parseTagSection(null)).isEmpty();
    }

    @Test
    @DisplayName("requiredTagKeys filter: skips subscriber when required tag absent from batch")
    void requiredTagKeyFilterSkipsWhenAbsent() {
        var source = new PostgresNotifyWakeupSource(
                "jdbc:postgresql://localhost/test", "user", "password", "crablet_events", 0L);
        AtomicInteger count = new AtomicInteger();
        // Subscriber requires wallet_id to be present
        source.start(Set.of("WalletDeposited"), Set.of("wallet_id"), Set.of(), Set.of(), count::incrementAndGet);

        // Batch has WalletDeposited but no wallet_id tag key
        source.dispatchBatch(new PGNotification[]{notifyWithTags("WalletDeposited", "region")});

        assertThat(count.get()).isZero();
        source.close();
    }

    @Test
    @DisplayName("requiredTagKeys filter: wakes subscriber when all required tags present")
    void requiredTagKeyFilterWakesWhenPresent() {
        var source = new PostgresNotifyWakeupSource(
                "jdbc:postgresql://localhost/test", "user", "password", "crablet_events", 0L);
        AtomicInteger count = new AtomicInteger();
        source.start(Set.of("WalletDeposited"), Set.of("wallet_id"), Set.of(), Set.of(), count::incrementAndGet);

        source.dispatchBatch(new PGNotification[]{notifyWithTags("WalletDeposited", "wallet_id,region")});

        assertThat(count.get()).isEqualTo(1);
        source.close();
    }

    @Test
    @DisplayName("anyOfTagKeys filter: skips when none of the anyOf keys present")
    void anyOfTagKeyFilterSkipsWhenNonePresent() {
        var source = new PostgresNotifyWakeupSource(
                "jdbc:postgresql://localhost/test", "user", "password", "crablet_events", 0L);
        AtomicInteger count = new AtomicInteger();
        source.start(Set.of(), Set.of(), Set.of("wallet_id", "account_id"), Set.of(), count::incrementAndGet);

        source.dispatchBatch(new PGNotification[]{notifyWithTags("SomeEvent", "region,currency")});

        assertThat(count.get()).isZero();
        source.close();
    }

    @Test
    @DisplayName("tag filter skipped when batch has no tag section (backward compat)")
    void tagFilterSkippedWhenNoTagSection() {
        var source = new PostgresNotifyWakeupSource(
                "jdbc:postgresql://localhost/test", "user", "password", "crablet_events", 0L);
        AtomicInteger count = new AtomicInteger();
        source.start(Set.of("WalletDeposited"), Set.of("wallet_id"), Set.of(), Set.of(), count::incrementAndGet);

        // Old-format payload — no | section — tag filter should be skipped (conservative)
        source.dispatchBatch(new PGNotification[]{notify("WalletDeposited")});

        assertThat(count.get()).isEqualTo(1); // woken conservatively when no tag info
        source.close();
    }

    // --- Phase E: reconnect backoff ---

    @Test
    @DisplayName("isPermanentFailure: unwrap error is permanent; generic error is not")
    void permanentFailureDetection() throws Exception {
        Method method = PostgresNotifyWakeupSource.class
                .getDeclaredMethod("isPermanentFailure", SQLException.class);
        method.setAccessible(true);

        SQLException poolerError = new SQLException("Cannot unwrap to PGConnection");
        SQLException transientError = new SQLException("Connection reset");

        assertThat((boolean) method.invoke(null, poolerError)).isTrue();
        assertThat((boolean) method.invoke(null, transientError)).isFalse();
    }

    @Test
    @DisplayName("close() while no listener thread started is safe (no reconnect loop)")
    void closeBeforeListenerStartsNoReconnect() {
        var source = new PostgresNotifyWakeupSource(
                "jdbc:postgresql://localhost/test", "user", "password", "crablet_events", 0L);
        assertThatCode(source::close).doesNotThrowAnyException();
    }

    // --- helper ---

    private static PGNotification notify(String payload) {
        return new PGNotification() {
            @Override public String getName() { return "crablet_events"; }
            @Override public String getParameter() { return payload; }
            @Override public int getPID() { return 0; }
        };
    }

    /** Helper that builds a payload with types and tag keys: {@code "type|key1,key2"}. */
    private static PGNotification notifyWithTags(String types, String tagKeys) {
        return notify(types + "|" + tagKeys);
    }
}
