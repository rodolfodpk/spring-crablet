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
 * View projector for wallet transaction history view.
 * Projects transaction history for audit and reporting.
 */
@Component
public class WalletTransactionViewProjector extends AbstractTypedViewProjector<WalletEvent> {

    // SQL statements as constants for better maintainability
    private static final String INSERT_TRANSACTION = """
        INSERT INTO wallet_transaction_view 
            (transaction_id, wallet_id, event_type, amount, description, occurred_at, event_position)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (transaction_id, event_position) DO NOTHING
        """;

    public WalletTransactionViewProjector(
            ObjectMapper objectMapper, 
            ClockProvider clockProvider,
            PlatformTransactionManager transactionManager) {
        super(objectMapper, clockProvider, transactionManager);
    }

    @Override
    public String getViewName() {
        return "wallet-transaction-view";
    }

    @Override
    protected Class<WalletEvent> getEventType() {
        return WalletEvent.class;
    }

    @Override
    protected boolean handleEvent(WalletEvent walletEvent, StoredEvent storedEvent, JdbcTemplate jdbcTemplate) {
        return switch (walletEvent) {
            case DepositMade deposit -> handleDepositMade(deposit, storedEvent, jdbcTemplate);
            case WithdrawalMade withdrawal -> handleWithdrawalMade(withdrawal, storedEvent, jdbcTemplate);
            case MoneyTransferred transfer -> handleMoneyTransferred(transfer, storedEvent, jdbcTemplate);
            case WalletOpened ignored -> false; // Not a transaction event
            case WalletStatementOpened ignored -> false; // Not a transaction event
            case WalletStatementClosed ignored -> false; // Not a transaction event
        };
    }

    private boolean handleDepositMade(DepositMade deposit, StoredEvent storedEvent, JdbcTemplate jdbc) {
        // Insert transaction (idempotent via transaction_id + event_position)
        jdbc.update(INSERT_TRANSACTION,
            deposit.depositId(),
            deposit.walletId(),
            "DepositMade",
            BigDecimal.valueOf(deposit.amount()),
            deposit.description(),
            Timestamp.from(deposit.depositedAt()),
            storedEvent.position()
        );
        return true;
    }

    private boolean handleWithdrawalMade(WithdrawalMade withdrawal, StoredEvent storedEvent, JdbcTemplate jdbc) {
        // Insert transaction (idempotent via transaction_id + event_position)
        jdbc.update(INSERT_TRANSACTION,
            withdrawal.withdrawalId(),
            withdrawal.walletId(),
            "WithdrawalMade",
            BigDecimal.valueOf(withdrawal.amount()),
            withdrawal.description(),
            Timestamp.from(withdrawal.withdrawnAt()),
            storedEvent.position()
        );
        return true;
    }

    private boolean handleMoneyTransferred(MoneyTransferred transfer, StoredEvent storedEvent, JdbcTemplate jdbc) {
        // Insert two transactions: one for from_wallet, one for to_wallet
        // Use transfer_id with suffix to make them unique
        jdbc.update(INSERT_TRANSACTION,
            transfer.transferId() + "-from",
            transfer.fromWalletId(),
            "MoneyTransferred",
            BigDecimal.valueOf(-transfer.amount()), // Negative for outgoing
            transfer.description(),
            Timestamp.from(transfer.transferredAt()),
            storedEvent.position()
        );
        
        jdbc.update(INSERT_TRANSACTION,
            transfer.transferId() + "-to",
            transfer.toWalletId(),
            "MoneyTransferred",
            BigDecimal.valueOf(transfer.amount()), // Positive for incoming
            transfer.description(),
            Timestamp.from(transfer.transferredAt()),
            storedEvent.position()
        );
        return true;
    }
}

