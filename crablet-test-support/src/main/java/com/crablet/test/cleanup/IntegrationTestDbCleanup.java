package com.crablet.test.cleanup;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Shared SQL cleanup for integration tests against the Crablet framework schema. Callers typically wrap
 * invocations in {@code try/catch} for {@link org.springframework.jdbc.BadSqlGrammarException} when
 * Flyway has not created tables yet.
 */
public final class IntegrationTestDbCleanup {

    private IntegrationTestDbCleanup() {}

    /** Eventstore / metrics style: {@code events} without RESTART IDENTITY, explicit sequence reset. */
    public static void truncateEventStoreTablesAndRestartPositionSequence(JdbcTemplate jdbc) {
        jdbc.execute("TRUNCATE TABLE events CASCADE");
        jdbc.execute("TRUNCATE TABLE commands CASCADE");
        jdbc.execute("TRUNCATE TABLE outbox_topic_progress CASCADE");
        jdbc.execute("ALTER SEQUENCE events_position_seq RESTART WITH 1");
    }

    /** Outbox, event-poller, and related tests: {@code events} with RESTART IDENTITY. */
    public static void truncateEventsCommandsAndOutboxProgress(JdbcTemplate jdbc) {
        jdbc.execute("TRUNCATE TABLE events RESTART IDENTITY CASCADE");
        jdbc.execute("TRUNCATE TABLE commands CASCADE");
        jdbc.execute("TRUNCATE TABLE outbox_topic_progress CASCADE");
    }

    /** For tests that reset the event stream and outbox progress without touching {@code commands}. */
    public static void truncateEventsAndOutboxProgressOnly(JdbcTemplate jdbc) {
        jdbc.execute("TRUNCATE TABLE events RESTART IDENTITY CASCADE");
        jdbc.execute("TRUNCATE TABLE outbox_topic_progress CASCADE");
    }

    public static void truncateViewsIntegrationTables(JdbcTemplate jdbc) {
        truncateEventsCommandsAndOutboxProgress(jdbc);
        truncateViewProgress(jdbc);
    }

    public static void truncateAutomationsIntegrationTables(JdbcTemplate jdbc) {
        jdbc.execute("TRUNCATE TABLE events RESTART IDENTITY CASCADE");
        jdbc.execute("TRUNCATE TABLE commands CASCADE");
        jdbc.execute("TRUNCATE TABLE automation_progress CASCADE");
    }

    public static void truncateOutboxTopicProgress(JdbcTemplate jdbc) {
        jdbc.execute("TRUNCATE TABLE outbox_topic_progress CASCADE");
    }

    public static void truncateViewProgress(JdbcTemplate jdbc) {
        jdbc.execute("TRUNCATE TABLE view_progress CASCADE");
    }

    public static void truncateAutomationProgress(JdbcTemplate jdbc) {
        jdbc.execute("TRUNCATE TABLE automation_progress CASCADE");
    }

    public static void truncateSharedFetchScanProgress(JdbcTemplate jdbc) {
        jdbc.execute("TRUNCATE TABLE crablet_module_scan_progress CASCADE");
        jdbc.execute("TRUNCATE TABLE crablet_processor_scan_progress CASCADE");
    }
}
