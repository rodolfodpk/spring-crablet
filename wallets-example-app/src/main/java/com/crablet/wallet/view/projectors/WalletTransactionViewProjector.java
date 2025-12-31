package com.crablet.wallet.view.projectors;

import com.crablet.eventstore.store.StoredEvent;
import com.crablet.views.ViewProjector;
import com.crablet.examples.wallet.event.DepositMade;
import com.crablet.examples.wallet.event.MoneyTransferred;
import com.crablet.examples.wallet.event.WalletEvent;
import com.crablet.examples.wallet.event.WithdrawalMade;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.crablet.eventstore.clock.ClockProvider;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;

/**
 * View projector for wallet transaction history view.
 * Projects transaction history for audit and reporting.
 */
@Component
public class WalletTransactionViewProjector implements ViewProjector {

    private static final Logger log = LoggerFactory.getLogger(WalletTransactionViewProjector.class);
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ClockProvider clockProvider;

    public WalletTransactionViewProjector(DataSource dataSource, ObjectMapper objectMapper, ClockProvider clockProvider) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.objectMapper = objectMapper;
        this.clockProvider = clockProvider;
    }

    @Override
    public String getViewName() {
        return "wallet-transaction-view";
    }

    @Override
    public int handle(String viewName, List<StoredEvent> events, DataSource writeDataSource) {
        int handled = 0;
        JdbcTemplate writeJdbc = new JdbcTemplate(writeDataSource);

        for (StoredEvent event : events) {
            try {
                WalletEvent walletEvent = deserialize(event, WalletEvent.class);
                
                switch (walletEvent) {
                    case DepositMade deposit -> {
                        // Insert transaction (idempotent via transaction_id + event_position)
                        writeJdbc.update("""
                            INSERT INTO wallet_transaction_view 
                                (transaction_id, wallet_id, event_type, amount, description, occurred_at, event_position)
                            VALUES (?, ?, ?, ?, ?, ?, ?)
                            ON CONFLICT (transaction_id, event_position) DO NOTHING
                            """,
                            deposit.depositId(),
                            deposit.walletId(),
                            "DepositMade",
                            BigDecimal.valueOf(deposit.amount()),
                            deposit.description(),
                            Timestamp.from(deposit.depositedAt()),
                            event.position()
                        );
                        handled++;
                    }
                    
                    case WithdrawalMade withdrawal -> {
                        // Insert transaction (idempotent via transaction_id + event_position)
                        writeJdbc.update("""
                            INSERT INTO wallet_transaction_view 
                                (transaction_id, wallet_id, event_type, amount, description, occurred_at, event_position)
                            VALUES (?, ?, ?, ?, ?, ?, ?)
                            ON CONFLICT (transaction_id, event_position) DO NOTHING
                            """,
                            withdrawal.withdrawalId(),
                            withdrawal.walletId(),
                            "WithdrawalMade",
                            BigDecimal.valueOf(withdrawal.amount()),
                            withdrawal.description(),
                            Timestamp.from(withdrawal.withdrawnAt()),
                            event.position()
                        );
                        handled++;
                    }
                    
                    case MoneyTransferred transfer -> {
                        // Insert two transactions: one for from_wallet, one for to_wallet
                        // Use transfer_id with suffix to make them unique
                        writeJdbc.update("""
                            INSERT INTO wallet_transaction_view 
                                (transaction_id, wallet_id, event_type, amount, description, occurred_at, event_position)
                            VALUES (?, ?, ?, ?, ?, ?, ?)
                            ON CONFLICT (transaction_id, event_position) DO NOTHING
                            """,
                            transfer.transferId() + "-from",
                            transfer.fromWalletId(),
                            "MoneyTransferred",
                            BigDecimal.valueOf(-transfer.amount()), // Negative for outgoing
                            transfer.description(),
                            Timestamp.from(transfer.transferredAt()),
                            event.position()
                        );
                        
                        writeJdbc.update("""
                            INSERT INTO wallet_transaction_view 
                                (transaction_id, wallet_id, event_type, amount, description, occurred_at, event_position)
                            VALUES (?, ?, ?, ?, ?, ?, ?)
                            ON CONFLICT (transaction_id, event_position) DO NOTHING
                            """,
                            transfer.transferId() + "-to",
                            transfer.toWalletId(),
                            "MoneyTransferred",
                            BigDecimal.valueOf(transfer.amount()), // Positive for incoming
                            transfer.description(),
                            Timestamp.from(transfer.transferredAt()),
                            event.position()
                        );
                        handled++;
                    }
                    
                    default -> {
                        // Ignore other event types
                    }
                }
            } catch (Exception e) {
                log.error("Failed to project event {} for view {}: {}", 
                    event.type(), viewName, e.getMessage(), e);
                throw new RuntimeException("Failed to project event: " + event.type(), e);
            }
        }

        return handled;
    }

    private <T> T deserialize(StoredEvent event, Class<T> type) {
        try {
            return objectMapper.readValue(event.data(), type);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize event: " + event.type() + " to " + type.getSimpleName(), e);
        }
    }
}

