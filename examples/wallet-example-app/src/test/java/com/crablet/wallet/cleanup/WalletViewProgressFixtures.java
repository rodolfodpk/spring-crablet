package com.crablet.wallet.cleanup;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Seeds {@code view_progress} for the wallet example's four view processors.
 */
public final class WalletViewProgressFixtures {

    private WalletViewProgressFixtures() {}

    public static void reseedDefaultWalletViews(JdbcTemplate jdbc) {
        jdbc.update(
                """
                INSERT INTO view_progress (view_name, status, last_position, last_updated_at, created_at)
                VALUES
                    ('wallet-balance-view',     'ACTIVE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                    ('wallet-transaction-view', 'ACTIVE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                    ('wallet-summary-view',     'ACTIVE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                    ('wallet-statement-view',   'ACTIVE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (view_name) DO UPDATE SET
                    status = 'ACTIVE',
                    last_position = 0,
                    error_count = 0,
                    last_error = NULL,
                    last_error_at = NULL,
                    last_updated_at = CURRENT_TIMESTAMP
                """);
    }
}
