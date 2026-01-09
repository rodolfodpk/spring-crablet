package com.crablet.wallet.view.projectors;

import com.crablet.eventstore.clock.ClockProvider;
import com.crablet.eventstore.store.StoredEvent;
import com.crablet.examples.wallet.events.DepositMade;
import com.crablet.examples.wallet.events.MoneyTransferred;
import com.crablet.examples.wallet.events.WalletEvent;
import com.crablet.examples.wallet.events.WalletOpened;
import com.crablet.examples.wallet.events.WalletStatementClosed;
import com.crablet.examples.wallet.events.WalletStatementOpened;
import com.crablet.examples.wallet.events.WithdrawalMade;
import com.crablet.views.AbstractTypedViewProjector;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * View projector for wallet balance view.
 * Projects wallet balance and owner information for fast API queries.
 */
@Component
public class WalletBalanceViewProjector extends AbstractTypedViewProjector<WalletEvent> {

    // SQL statements as constants for better maintainability
    private static final String UPSERT_WALLET_BALANCE = """
        INSERT INTO wallet_balance_view (wallet_id, owner, balance, last_updated_at)
        VALUES (?, ?, ?, ?)
        ON CONFLICT (wallet_id) 
        DO UPDATE SET 
            owner = EXCLUDED.owner,
            balance = EXCLUDED.balance,
            last_updated_at = EXCLUDED.last_updated_at
        """;

    private static final String UPDATE_BALANCE = """
        UPDATE wallet_balance_view
        SET balance = ?,
            last_updated_at = ?
        WHERE wallet_id = ?
        """;

    public WalletBalanceViewProjector(
            ObjectMapper objectMapper, 
            ClockProvider clockProvider,
            PlatformTransactionManager transactionManager) {
        super(objectMapper, clockProvider, transactionManager);
    }

    @Override
    public String getViewName() {
        return "wallet-balance-view";
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
            case WalletStatementOpened ignored -> false; // Not relevant for balance view
            case WalletStatementClosed ignored -> false; // Not relevant for balance view
        };
    }

    private boolean handleWalletOpened(WalletOpened opened, JdbcTemplate jdbc) {
        // Insert or update wallet balance (idempotent)
        jdbc.update(UPSERT_WALLET_BALANCE,
            opened.walletId(),
            opened.owner(),
            BigDecimal.valueOf(opened.initialBalance()),
            Timestamp.from(clockProvider.now())
        );
        return true;
    }

    private boolean handleDepositMade(DepositMade deposit, JdbcTemplate jdbc) {
        // Update balance by adding deposit amount (idempotent via new_balance)
        jdbc.update(UPDATE_BALANCE,
            BigDecimal.valueOf(deposit.newBalance()),
            Timestamp.from(clockProvider.now()),
            deposit.walletId()
        );
        return true;
    }

    private boolean handleWithdrawalMade(WithdrawalMade withdrawal, JdbcTemplate jdbc) {
        // Update balance using new_balance from event (idempotent)
        jdbc.update(UPDATE_BALANCE,
            BigDecimal.valueOf(withdrawal.newBalance()),
            Timestamp.from(clockProvider.now()),
            withdrawal.walletId()
        );
        return true;
    }

    private boolean handleMoneyTransferred(MoneyTransferred transfer, JdbcTemplate jdbc) {
        // Update both wallets' balances (idempotent via new balances from event)
        jdbc.update(UPDATE_BALANCE,
            BigDecimal.valueOf(transfer.fromBalance()),
            Timestamp.from(clockProvider.now()),
            transfer.fromWalletId()
        );
        
        jdbc.update(UPDATE_BALANCE,
            BigDecimal.valueOf(transfer.toBalance()),
            Timestamp.from(clockProvider.now()),
            transfer.toWalletId()
        );
        return true;
    }
}

