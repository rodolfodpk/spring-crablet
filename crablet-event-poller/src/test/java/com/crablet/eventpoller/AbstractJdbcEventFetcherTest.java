package com.crablet.eventpoller;

import com.crablet.eventstore.StoredEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.jspecify.annotations.Nullable;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AbstractJdbcEventFetcher Unit Tests")
@Testcontainers
class AbstractJdbcEventFetcherTest {

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("postgres")
            .withUsername("postgres")
            .withPassword("postgres");

    private DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(postgres.getJdbcUrl());
        ds.setUser(postgres.getUsername());
        ds.setPassword(postgres.getPassword());
        dataSource = ds;

        try (Connection connection = dataSource.getConnection()) {
            connection.createStatement().execute("""
                CREATE TABLE IF NOT EXISTS events (
                    type VARCHAR(64) NOT NULL,
                    tags TEXT[] NOT NULL DEFAULT '{}',
                    data JSON NOT NULL,
                    transaction_id xid8 NOT NULL,
                    position BIGSERIAL NOT NULL PRIMARY KEY,
                    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    correlation_id UUID NULL,
                    causation_id BIGINT NULL
                )
                """);
            connection.createStatement().execute("TRUNCATE TABLE events RESTART IDENTITY CASCADE");
        }
    }

    @Test
    @DisplayName("Null SQL filter skips fetch")
    void nullSqlFilterSkipsFetch() {
        TestFetcher fetcher = new TestFetcher(dataSource, null);

        assertThat(fetcher.fetchEvents("processor", 0, 10)).isEmpty();
    }

    @Test
    @DisplayName("Fetches matching events after position in order")
    void fetchesMatchingEventsAfterPositionInOrder() throws Exception {
        UUID correlationId = UUID.randomUUID();
        insertEvent("WalletOpened", new String[]{"wallet_id=w1"}, "{\"name\":\"first\"}", correlationId, 10L);
        insertEvent("DepositMade", new String[]{"wallet_id=w1", "deposit_id=d1"}, "{\"name\":\"second\"}", null, null);
        insertEvent("WalletOpened", new String[]{"wallet_id=w2"}, "{\"name\":\"other\"}", null, null);

        TestFetcher fetcher = new TestFetcher(dataSource, "type = 'DepositMade' OR 'wallet_id=w2' = ANY(tags)");

        List<StoredEvent> events = fetcher.fetchEvents("processor", 1, 10);

        assertThat(events).hasSize(2);
        assertThat(events).extracting(StoredEvent::type).containsExactly("DepositMade", "WalletOpened");
        assertThat(events.get(0).tags()).extracting("key").containsExactly("wallet_id", "deposit_id");
        assertThat(new String(events.get(0).data(), StandardCharsets.UTF_8)).contains("second");
        assertThat(events.get(0).position()).isEqualTo(2);
    }

    @Test
    @DisplayName("Honors batch size limit")
    void honorsBatchSizeLimit() throws Exception {
        insertEvent("One", new String[]{"kind=a"}, "{}", null, null);
        insertEvent("Two", new String[]{"kind=a"}, "{}", null, null);

        TestFetcher fetcher = new TestFetcher(dataSource, "TRUE");

        assertThat(fetcher.fetchEvents("processor", 0, 1))
                .hasSize(1)
                .first()
                .extracting(StoredEvent::type)
                .isEqualTo("One");
    }

    private void insertEvent(
            String type,
            String[] tags,
            String data,
            @Nullable UUID correlationId,
            @Nullable Long causationId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement("""
                 INSERT INTO events (type, tags, data, transaction_id, occurred_at, correlation_id, causation_id)
                 VALUES (?, ?, ?::json, pg_current_xact_id(), CURRENT_TIMESTAMP, ?, ?)
                 """)) {
            stmt.setString(1, type);
            stmt.setArray(2, connection.createArrayOf("text", tags));
            stmt.setString(3, data);
            stmt.setObject(4, correlationId);
            stmt.setObject(5, causationId);
            stmt.executeUpdate();
        }
    }

    private static class TestFetcher extends AbstractJdbcEventFetcher<String> {
        private final @Nullable String filter;

        private TestFetcher(DataSource readDataSource, @Nullable String filter) {
            super(readDataSource);
            this.filter = filter;
        }

        @Override
        protected @Nullable String buildSqlFilter(String processorId) {
            return filter;
        }
    }
}
