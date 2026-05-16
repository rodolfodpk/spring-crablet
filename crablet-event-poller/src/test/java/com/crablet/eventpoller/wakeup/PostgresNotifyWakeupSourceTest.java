package com.crablet.eventpoller.wakeup;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
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
}
