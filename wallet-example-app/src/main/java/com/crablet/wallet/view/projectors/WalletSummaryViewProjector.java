package com.crablet.wallet.view.projectors;

import com.crablet.eventstore.clock.ClockProvider;
import com.crablet.eventstore.store.StoredEvent;
import com.crablet.views.AbstractTypedViewProjector;
import com.crablet.examples.wallet.events.DepositMade;
import com.crablet.examples.wallet.events.MoneyTransferred;
import com.crablet.examples.wallet.events.WalletEvent;
import com.crablet.examples.wallet.events.WalletOpened;
import com.crablet.examples.wallet.events.WalletStatementClosed;
import com.crablet.examples.wallet.events.WalletStatementOpened;
import com.crablet.examples.wallet.events.WithdrawalMade;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * View projector for wallet summary view.
 * Projects aggregated statistics for dashboards and reporting.
 */
@Component
public class WalletSummaryViewProjector extends AbstractTypedViewProjector<WalletEvent> {

    // SQL statements as constants for better maintainability
    private static final String INSERT_WALLET_OPENED = """
        INSERT INTO wallet_summary_view 
            (wallet_id, total_deposits, total_withdrawals, total_transfers_in, 
             total_transfers_out, current_balance, last_transaction_at)
        VALUES (?, 0, 0, 0, 0, ?, ?)
        ON CONFLICT (wallet_id) DO NOTHING
        """;

    private static final String UPSERT_DEPOSIT = """
        INSERT INTO wallet_summary_view (wallet_id, total_deposits, current_balance, last_transaction_at)
        VALUES (?, ?, ?, ?)
        ON CONFLICT (wallet_id) 
        DO UPDATE SET 
            total_deposits = GREATEST(wallet_summary_view.total_deposits, EXCLUDED.total_deposits),
            current_balance = EXCLUDED.current_balance,
            last_transaction_at = GREATEST(wallet_summary_view.last_transaction_at, EXCLUDED.last_transaction_at)
        """;

    private static final String UPSERT_WITHDRAWAL = """
        INSERT INTO wallet_summary_view (wallet_id, total_withdrawals, current_balance, last_transaction_at)
        VALUES (?, ?, ?, ?)
        ON CONFLICT (wallet_id) 
        DO UPDATE SET 
            total_withdrawals = GREATEST(wallet_summary_view.total_withdrawals, EXCLUDED.total_withdrawals),
            current_balance = EXCLUDED.current_balance,
            last_transaction_at = GREATEST(wallet_summary_view.last_transaction_at, EXCLUDED.last_transaction_at)
        """;

    private static final String UPSERT_TRANSFER_OUT = """
        INSERT INTO wallet_summary_view (wallet_id, total_transfers_out, current_balance, last_transaction_at)
        VALUES (?, ?, ?, ?)
        ON CONFLICT (wallet_id) 
        DO UPDATE SET 
            total_transfers_out = GREATEST(wallet_summary_view.total_transfers_out, EXCLUDED.total_transfers_out),
            current_balance = EXCLUDED.current_balance,
            last_transaction_at = GREATEST(wallet_summary_view.last_transaction_at, EXCLUDED.last_transaction_at)
        """;

    private static final String UPSERT_TRANSFER_IN = """
        INSERT INTO wallet_summary_view (wallet_id, total_transfers_in, current_balance, last_transaction_at)
        VALUES (?, ?, ?, ?)
        ON CONFLICT (wallet_id) 
        DO UPDATE SET 
            total_transfers_in = GREATEST(wallet_summary_view.total_transfers_in, EXCLUDED.total_transfers_in),
            current_balance = EXCLUDED.current_balance,
            last_transaction_at = GREATEST(wallet_summary_view.last_transaction_at, EXCLUDED.last_transaction_at)
        """;

    public WalletSummaryViewProjector(
            ObjectMapper objectMapper, 
            ClockProvider clockProvider,
            PlatformTransactionManager transactionManager) {
        super(objectMapper, clockProvider, transactionManager);
    }

    @Override
    public String getViewName() {
        return "wallet-summary-view";
    }

    @Override
    protected Class<WalletEvent> getEventType() {
        return WalletEvent.class;
    }

    @Override
    protected boolean handleEvent(WalletEvent walletEvent, StoredEvent storedEvent, JdbcTemplate jdbcTemplate) {
        return switch (walletEvent) {
            case WalletOpened opened -> handleWalletOpened(opened, jdbcTemplate);
            case DepositMade deposit -> handleDepositMade(deposit, jdbcTemplate);
            case WithdrawalMade withdrawal -> handleWithdrawalMade(withdrawal, jdbcTemplate);
            case MoneyTransferred transfer -> handleMoneyTransferred(transfer, jdbcTemplate);
            case WalletStatementOpened ignored -> false; // Not relevant for summary view
            case WalletStatementClosed ignored -> false; // Not relevant for summary view
        };
    }

    private boolean handleWalletOpened(WalletOpened opened, JdbcTemplate jdbc) {
        // Initialize summary (idempotent via wallet_id PK)
        jdbc.update(INSERT_WALLET_OPENED,
            opened.walletId(),
            BigDecimal.valueOf(opened.initialBalance()),
            Timestamp.from(opened.openedAt())
        );
        return true;
    }

    private boolean handleDepositMade(DepositMade deposit, JdbcTemplate jdbc) {
        // Update summary with deposit (idempotent - use new_balance from event)
        // Note: For idempotency, we use the new_balance from event, not accumulate
        // If event is reprocessed, it will set the same balance
        jdbc.update(UPSERT_DEPOSIT,
            deposit.walletId(),
            BigDecimal.valueOf(deposit.amount()),
            BigDecimal.valueOf(deposit.newBalance()),
            Timestamp.from(deposit.depositedAt())
        );
        return true;
    }

    private boolean handleWithdrawalMade(WithdrawalMade withdrawal, JdbcTemplate jdbc) {
        // Update summary with withdrawal (idempotent - use new_balance from event)
        jdbc.update(UPSERT_WITHDRAWAL,
            withdrawal.walletId(),
            BigDecimal.valueOf(withdrawal.amount()),
            BigDecimal.valueOf(withdrawal.newBalance()),
            Timestamp.from(withdrawal.withdrawnAt())
        );
        return true;
    }

    private boolean handleMoneyTransferred(MoneyTransferred transfer, JdbcTemplate jdbc) {
        // Update summary for both wallets (idempotent - use new balances from event)
        // From wallet: outgoing transfer
        jdbc.update(UPSERT_TRANSFER_OUT,
            transfer.fromWalletId(),
            BigDecimal.valueOf(transfer.amount()),
            BigDecimal.valueOf(transfer.fromBalance()),
            Timestamp.from(transfer.transferredAt())
        );
        
        // To wallet: incoming transfer
        jdbc.update(UPSERT_TRANSFER_IN,
            transfer.toWalletId(),
            BigDecimal.valueOf(transfer.amount()),
            BigDecimal.valueOf(transfer.toBalance()),
            Timestamp.from(transfer.transferredAt())
        );
        return true;
    }
}

