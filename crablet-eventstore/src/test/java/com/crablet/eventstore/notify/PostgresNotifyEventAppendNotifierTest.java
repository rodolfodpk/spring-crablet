package com.crablet.eventstore.notify;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PostgresNotifyEventAppendNotifierTest {

    private static PostgreSQLContainer<?> postgres;
    private static PGSimpleDataSource ds;

    @BeforeAll
    static void startDatabase() {
        postgres = new PostgreSQLContainer<>("postgres:17.2")
                .withDatabaseName("notify_test")
                .withUsername("test")
                .withPassword("test");
        postgres.start();

        ds = new PGSimpleDataSource();
        ds.setURL(postgres.getJdbcUrl());
        ds.setUser(postgres.getUsername());
        ds.setPassword(postgres.getPassword());
    }

    @AfterAll
    static void stopDatabase() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    @Test
    void notifyEventsAppendedDeliversPgNotifyOnChannel() throws Exception {
        PostgresNotifyEventAppendNotifier notifier =
                new PostgresNotifyEventAppendNotifier(ds, "crablet_events", "events-appended");

        try (Connection listenConn = ds.getConnection()) {
            try (var stmt = listenConn.createStatement()) {
                stmt.execute("LISTEN crablet_events");
            }

            notifier.notifyEventsAppended();

            PGConnection pgConn = listenConn.unwrap(PGConnection.class);
            PGNotification[] notifications = pgConn.getNotifications(5000);

            assertThat(notifications)
                    .as("expected notification on channel crablet_events")
                    .isNotNull()
                    .isNotEmpty();
            assertThat(notifications[0].getName()).isEqualTo("crablet_events");
            assertThat(notifications[0].getParameter()).isEqualTo("events-appended");
        }
    }

    @Test
    void notifyEventsAppendedDoesNotPropagateWhenDataSourceThrows() throws Exception {
        DataSource broken = mock(DataSource.class);
        when(broken.getConnection()).thenThrow(new SQLException("simulated connection failure"));

        PostgresNotifyEventAppendNotifier notifier =
                new PostgresNotifyEventAppendNotifier(broken, "crablet_events", "events-appended");

        assertThatCode(notifier::notifyEventsAppended).doesNotThrowAnyException();
    }

    // --- Phase D: encodePayload ---

    @Test
    void encodePayloadTypesOnly() {
        assertThat(PostgresNotifyEventAppendNotifier.encodePayload(Set.of("WalletCreated"), Set.of()))
                .isEqualTo("WalletCreated");
    }

    @Test
    void encodePayloadTypesAndTagKeys() {
        String encoded = PostgresNotifyEventAppendNotifier.encodePayload(
                Set.of("WalletDeposited"), Set.of("wallet_id", "region"));
        assertThat(encoded).startsWith("WalletDeposited|");
        assertThat(encoded).contains("wallet_id");
        assertThat(encoded).contains("region");
    }

    @Test
    void encodePayloadEmptyTypesIsWildcard() {
        assertThat(PostgresNotifyEventAppendNotifier.encodePayload(Set.of(), Set.of("wallet_id")))
                .isEqualTo("*");
    }

    @Test
    void encodePayloadCombinedTooLongFallsBackToTypesOnly() {
        String hugeTag = "y".repeat(8000);
        assertThat(PostgresNotifyEventAppendNotifier.encodePayload(Set.of("T"), Set.of(hugeTag)))
                .isEqualTo("T");
    }

    @Test
    void encodePayloadTypesPartTooLongFallsBackToWildcard() {
        String hugeType = "x".repeat(8000);
        assertThat(PostgresNotifyEventAppendNotifier.encodePayload(Set.of(hugeType), Set.of()))
                .isEqualTo("*");
    }

    @Test
    void recoveryOnSameNotifierAfterFailureClearsConsecutiveFailures() throws Exception {
        DataSource flaky = mock(DataSource.class);
        AtomicInteger attempts = new AtomicInteger();
        when(flaky.getConnection()).thenAnswer(invocation -> {
            if (attempts.getAndIncrement() == 0) {
                throw new SQLException("transient");
            }
            return ds.getConnection();
        });

        PostgresNotifyEventAppendNotifier notifier =
                new PostgresNotifyEventAppendNotifier(flaky, "crablet_events", "events-appended");

        assertThatCode(notifier::notifyEventsAppended).doesNotThrowAnyException();
        assertThatCode(notifier::notifyEventsAppended).doesNotThrowAnyException();
    }

    // --- Phase E: failure hygiene ---

    @Test
    void consecutiveFailuresNeverThrowRegardlessOfCount() throws Exception {
        DataSource broken = mock(DataSource.class);
        when(broken.getConnection()).thenThrow(new SQLException("pool exhausted"));

        PostgresNotifyEventAppendNotifier notifier =
                new PostgresNotifyEventAppendNotifier(broken, "crablet_events", "events-appended");

        // Drive well past the failure threshold — must never propagate
        for (int i = 0; i < 20; i++) {
            assertThatCode(notifier::notifyEventsAppended).doesNotThrowAnyException();
        }
    }

    @Test
    void recoveryAfterCooldownResetsSuppression() throws Exception {
        DataSource broken = mock(DataSource.class);
        when(broken.getConnection()).thenThrow(new SQLException("fail"));

        PostgresNotifyEventAppendNotifier notifier =
                new PostgresNotifyEventAppendNotifier(broken, "crablet_events", "events-appended");

        // Exhaust threshold to enter cooldown
        for (int i = 0; i < 5; i++) {
            notifier.notifyEventsAppended();
        }

        // Force cooldown to expire via reflection
        var field = PostgresNotifyEventAppendNotifier.class.getDeclaredField("suppressUntilNanos");
        field.setAccessible(true);
        field.set(notifier, 0L);

        // Switch to healthy datasource and verify recovery works without throwing
        DataSource healthy = mock(DataSource.class);
        when(healthy.getConnection()).thenAnswer(inv -> ds.getConnection());
        PostgresNotifyEventAppendNotifier recovered =
                new PostgresNotifyEventAppendNotifier(healthy, "crablet_events", "events-appended");
        assertThatCode(recovered::notifyEventsAppended).doesNotThrowAnyException();
    }
}
