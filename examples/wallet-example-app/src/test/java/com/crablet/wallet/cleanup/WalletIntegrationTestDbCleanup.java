package com.crablet.wallet.cleanup;

import com.crablet.test.cleanup.IntegrationTestDbCleanup;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Database cleanup helpers for wallet-example-app integration and E2E tests.
 */
public final class WalletIntegrationTestDbCleanup {

    private WalletIntegrationTestDbCleanup() {}

    public static void truncateWalletMaterializedViews(JdbcTemplate jdbc) {
        jdbc.execute("TRUNCATE TABLE wallet_balance_view CASCADE");
        jdbc.execute("TRUNCATE TABLE wallet_transaction_view CASCADE");
        jdbc.execute("TRUNCATE TABLE wallet_summary_view CASCADE");
        jdbc.execute("TRUNCATE TABLE statement_transactions CASCADE");
        jdbc.execute("TRUNCATE TABLE wallet_statement_view CASCADE");
    }

    /** Default {@link com.crablet.wallet.AbstractWalletTest} cleanup: events, commands, wallet views, then view_progress seed. */
    public static void cleanDefaultWalletIntegrationTest(JdbcTemplate jdbc) {
        jdbc.execute("TRUNCATE TABLE events RESTART IDENTITY CASCADE");
        jdbc.execute("TRUNCATE TABLE commands CASCADE");
        truncateWalletMaterializedViews(jdbc);
        WalletViewProgressFixtures.reseedDefaultWalletViews(jdbc);
    }

    /** View lifecycle E2E: events, commands, view_progress, wallet projection tables (no outbox). */
    public static void truncateForWalletViewLifecycleE2e(JdbcTemplate jdbc) {
        jdbc.execute("TRUNCATE TABLE events RESTART IDENTITY CASCADE");
        jdbc.execute("TRUNCATE TABLE commands CASCADE");
        jdbc.execute("TRUNCATE TABLE view_progress CASCADE");
        truncateWalletMaterializedViews(jdbc);
    }

    public static void truncateForWalletOutboxE2e(JdbcTemplate jdbc) {
        IntegrationTestDbCleanup.truncateEventsCommandsAndOutboxProgress(jdbc);
    }

    public static void truncateForWalletAutomationOrCorrelationE2e(JdbcTemplate jdbc) {
        IntegrationTestDbCleanup.truncateAutomationsIntegrationTables(jdbc);
    }

    public static void truncateSharedFetchScanProgressBestEffort(JdbcTemplate jdbc) {
        try {
            IntegrationTestDbCleanup.truncateSharedFetchScanProgress(jdbc);
        } catch (Exception ignored) {
            // Schema variants without V14 tables
        }
    }
}
