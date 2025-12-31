package com.crablet.wallet.view.projectors;

import com.crablet.eventstore.store.StoredEvent;
import com.crablet.views.ViewProjector;
import com.crablet.examples.wallet.event.DepositMade;
import com.crablet.examples.wallet.event.MoneyTransferred;
import com.crablet.examples.wallet.event.WalletEvent;
import com.crablet.examples.wallet.event.WalletOpened;
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
 * View projector for wallet summary view.
 * Projects aggregated statistics for dashboards and reporting.
 */
@Component
public class WalletSummaryViewProjector implements ViewProjector {

    private static final Logger log = LoggerFactory.getLogger(WalletSummaryViewProjector.class);
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ClockProvider clockProvider;

    public WalletSummaryViewProjector(DataSource dataSource, ObjectMapper objectMapper, ClockProvider clockProvider) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.objectMapper = objectMapper;
        this.clockProvider = clockProvider;
    }

    @Override
    public String getViewName() {
        return "wallet-summary-view";
    }

    @Override
    public int handle(String viewName, List<StoredEvent> events, DataSource writeDataSource) {
        int handled = 0;
        JdbcTemplate writeJdbc = new JdbcTemplate(writeDataSource);

        for (StoredEvent event : events) {
            try {
                WalletEvent walletEvent = deserialize(event, WalletEvent.class);
                
                switch (walletEvent) {
                    case WalletOpened opened -> {
                        // Initialize summary (idempotent via wallet_id PK)
                        writeJdbc.update("""
                            INSERT INTO wallet_summary_view 
                                (wallet_id, total_deposits, total_withdrawals, total_transfers_in, 
                                 total_transfers_out, current_balance, last_transaction_at)
                            VALUES (?, 0, 0, 0, 0, ?, ?)
                            ON CONFLICT (wallet_id) DO NOTHING
                            """,
                            opened.walletId(),
                            BigDecimal.valueOf(opened.initialBalance()),
                            Timestamp.from(opened.openedAt())
                        );
                        handled++;
                    }
                    
                    case DepositMade deposit -> {
                        // Update summary with deposit (idempotent - use new_balance from event)
                        // Note: For idempotency, we use the new_balance from event, not accumulate
                        // If event is reprocessed, it will set the same balance
                        writeJdbc.update("""
                            INSERT INTO wallet_summary_view (wallet_id, total_deposits, current_balance, last_transaction_at)
                            VALUES (?, ?, ?, ?)
                            ON CONFLICT (wallet_id) 
                            DO UPDATE SET 
                                total_deposits = GREATEST(wallet_summary_view.total_deposits, EXCLUDED.total_deposits),
                                current_balance = EXCLUDED.current_balance,
                                last_transaction_at = GREATEST(wallet_summary_view.last_transaction_at, EXCLUDED.last_transaction_at)
                            """,
                            deposit.walletId(),
                            BigDecimal.valueOf(deposit.amount()),
                            BigDecimal.valueOf(deposit.newBalance()),
                            Timestamp.from(deposit.depositedAt())
                        );
                        handled++;
                    }
                    
                    case WithdrawalMade withdrawal -> {
                        // Update summary with withdrawal (idempotent - use new_balance from event)
                        writeJdbc.update("""
                            INSERT INTO wallet_summary_view (wallet_id, total_withdrawals, current_balance, last_transaction_at)
                            VALUES (?, ?, ?, ?)
                            ON CONFLICT (wallet_id) 
                            DO UPDATE SET 
                                total_withdrawals = GREATEST(wallet_summary_view.total_withdrawals, EXCLUDED.total_withdrawals),
                                current_balance = EXCLUDED.current_balance,
                                last_transaction_at = GREATEST(wallet_summary_view.last_transaction_at, EXCLUDED.last_transaction_at)
                            """,
                            withdrawal.walletId(),
                            BigDecimal.valueOf(withdrawal.amount()),
                            BigDecimal.valueOf(withdrawal.newBalance()),
                            Timestamp.from(withdrawal.withdrawnAt())
                        );
                        handled++;
                    }
                    
                    case MoneyTransferred transfer -> {
                        // Update summary for both wallets (idempotent - use new balances from event)
                        // From wallet: outgoing transfer
                        writeJdbc.update("""
                            INSERT INTO wallet_summary_view (wallet_id, total_transfers_out, current_balance, last_transaction_at)
                            VALUES (?, ?, ?, ?)
                            ON CONFLICT (wallet_id) 
                            DO UPDATE SET 
                                total_transfers_out = GREATEST(wallet_summary_view.total_transfers_out, EXCLUDED.total_transfers_out),
                                current_balance = EXCLUDED.current_balance,
                                last_transaction_at = GREATEST(wallet_summary_view.last_transaction_at, EXCLUDED.last_transaction_at)
                            """,
                            transfer.fromWalletId(),
                            BigDecimal.valueOf(transfer.amount()),
                            BigDecimal.valueOf(transfer.fromBalance()),
                            Timestamp.from(transfer.transferredAt())
                        );
                        
                        // To wallet: incoming transfer
                        writeJdbc.update("""
                            INSERT INTO wallet_summary_view (wallet_id, total_transfers_in, current_balance, last_transaction_at)
                            VALUES (?, ?, ?, ?)
                            ON CONFLICT (wallet_id) 
                            DO UPDATE SET 
                                total_transfers_in = GREATEST(wallet_summary_view.total_transfers_in, EXCLUDED.total_transfers_in),
                                current_balance = EXCLUDED.current_balance,
                                last_transaction_at = GREATEST(wallet_summary_view.last_transaction_at, EXCLUDED.last_transaction_at)
                            """,
                            transfer.toWalletId(),
                            BigDecimal.valueOf(transfer.amount()),
                            BigDecimal.valueOf(transfer.toBalance()),
                            Timestamp.from(transfer.transferredAt())
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

