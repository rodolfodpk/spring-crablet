package com.crablet.eventpoller;

import com.crablet.eventstore.StoredEvent;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Benchmark: per-processor poller fetch time with sparse required-tag-key filter.
 *
 * Establishes a timing baseline for EventSelectionWhereClauseBuilder after the query
 * switch from unnest(tags) LIKE to event_tags correlated EXISTS subqueries.
 * Run with -Dgroups=benchmark to include in a benchmark-only suite.
 *
 * Scenario: 10k events, 10% matching the required-tag-key filter (sparse subscription).
 * Output: average and P99 fetch time per batch of matching events.
 */
@Tag("benchmark")
@DisplayName("event_tags poller fetch benchmark")
@Testcontainers
class EventTagsPollerFetchBenchmarkTest {

    private static final int SEED_EVENTS    = 10_000;
    private static final int MATCH_RATIO    = 10;   // 1 in 10 events has the target tag
    private static final int FETCH_BATCH    = 100;
    private static final int FETCH_ROUNDS   = 50;
    private static final String TARGET_KEY  = "subscribed_tenant";

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("postgres")
            .withUsername("postgres")
            .withPassword("postgres");

    private DataSource dataSource;

    @BeforeAll
    static void migrateSchema() {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @BeforeEach
    void setUp() throws Exception {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(postgres.getJdbcUrl());
        ds.setUser(postgres.getUsername());
        ds.setPassword(postgres.getPassword());
        dataSource = ds;

        try (Connection conn = dataSource.getConnection()) {
            conn.createStatement().execute("TRUNCATE TABLE crablet_event_tags");
            conn.createStatement().execute("TRUNCATE TABLE crablet_events RESTART IDENTITY CASCADE");
        }

        seedEvents();
    }

    @Test
    @DisplayName("Average and P99 fetch time: sparse required-tag-key filter over 10k events")
    void sparseRequiredTagKeyFetch() {
        EventSelection selection = sparseSelection();
        String sqlFilter = EventSelectionWhereClauseBuilder.buildWhereClause(selection);

        TestFetcher fetcher = new TestFetcher(dataSource, sqlFilter);

        List<Long> samples = new ArrayList<>(FETCH_ROUNDS);
        int totalFetched = 0;

        for (int round = 0; round < FETCH_ROUNDS; round++) {
            long start = System.nanoTime();
            List<StoredEvent> events = fetcher.fetchEvents("benchmark-processor", 0, FETCH_BATCH);
            samples.add(System.nanoTime() - start);
            totalFetched += events.size();
        }

        Collections.sort(samples);
        long avgMs  = samples.stream().mapToLong(l -> l).sum() / samples.size() / 1_000_000;
        long p99Ms  = samples.get((int) (FETCH_ROUNDS * 0.99)) / 1_000_000;

        System.out.printf(
            "[benchmark] poller fetch sparse (%d events, 1-in-%d match, batch=%d, rounds=%d): " +
            "avg=%dms P99=%dms total-fetched=%d%n",
            SEED_EVENTS, MATCH_RATIO, FETCH_BATCH, FETCH_ROUNDS, avgMs, p99Ms, totalFetched);

        assertThat(p99Ms).as("P99 fetch should complete within 5s").isLessThan(5_000);
        assertThat(totalFetched).as("each round should return matching events").isGreaterThan(0);
    }

    private void seedEvents() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            String insertEvent = """
                INSERT INTO crablet_events (type, tags, data, transaction_id, occurred_at)
                VALUES (?, ?, ?::json, pg_current_xact_id(), CURRENT_TIMESTAMP)
                RETURNING position
                """;
            String insertTag = """
                INSERT INTO crablet_event_tags (position, key, value)
                VALUES (?, ?, ?)
                """;

            for (int i = 0; i < SEED_EVENTS; i++) {
                boolean isMatch = (i % MATCH_RATIO == 0);
                String type = isMatch ? "TenantEvent" : "OtherEvent";
                String[] tags = isMatch
                        ? new String[]{ TARGET_KEY + "=tenant-1", "entity_id=e-" + i }
                        : new String[]{ "entity_id=e-" + i };

                try (PreparedStatement stmt = conn.prepareStatement(insertEvent)) {
                    stmt.setString(1, type);
                    stmt.setArray(2, conn.createArrayOf("text", tags));
                    stmt.setString(3, "{}");
                    var rs = stmt.executeQuery();
                    if (rs.next()) {
                        long position = rs.getLong(1);
                        try (PreparedStatement tagStmt = conn.prepareStatement(insertTag)) {
                            for (String tag : tags) {
                                int eq = tag.indexOf('=');
                                tagStmt.setLong(1, position);
                                tagStmt.setString(2, tag.substring(0, eq));
                                tagStmt.setString(3, tag.substring(eq + 1));
                                tagStmt.addBatch();
                            }
                            tagStmt.executeBatch();
                        }
                    }
                }

                if (i % 500 == 499) conn.commit();
            }
            conn.commit();
        }
    }

    private static EventSelection sparseSelection() {
        return new EventSelection() {
            @Override public Set<String> getEventTypes()  { return Set.of(); }
            @Override public Set<String> getRequiredTags() { return Set.of(TARGET_KEY); }
            @Override public Set<String> getAnyOfTags()   { return Set.of(); }
            @Override public Map<String, String> getExactTags() { return Map.of(); }
        };
    }

    private static class TestFetcher extends AbstractJdbcEventFetcher<String> {
        private final @Nullable String filter;

        TestFetcher(DataSource ds, @Nullable String filter) {
            super(ds);
            this.filter = filter;
        }

        @Override
        protected @Nullable String buildSqlFilter(String processorId) {
            return filter;
        }
    }
}
