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
}
