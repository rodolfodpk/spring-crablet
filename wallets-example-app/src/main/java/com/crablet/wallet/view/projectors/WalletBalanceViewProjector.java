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
import java.time.Instant;
import java.util.List;

/**
 * View projector for wallet balance view.
 * Projects wallet balance and owner information for fast API queries.
 */
@Component
public class WalletBalanceViewProjector implements ViewProjector {

    private static final Logger log = LoggerFactory.getLogger(WalletBalanceViewProjector.class);
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ClockProvider clockProvider;

    public WalletBalanceViewProjector(DataSource dataSource, ObjectMapper objectMapper, ClockProvider clockProvider) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.objectMapper = objectMapper;
        this.clockProvider = clockProvider;
    }

    @Override
    public String getViewName() {
        return "wallet-balance-view";
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
                        // Insert or update wallet balance (idempotent)
                        writeJdbc.update("""
                            INSERT INTO wallet_balance_view (wallet_id, owner, balance, last_updated_at)
                            VALUES (?, ?, ?, ?)
                            ON CONFLICT (wallet_id) 
                            DO UPDATE SET 
                                owner = EXCLUDED.owner,
                                balance = EXCLUDED.balance,
                                last_updated_at = EXCLUDED.last_updated_at
                            """,
                            opened.walletId(),
                            opened.owner(),
                            BigDecimal.valueOf(opened.initialBalance()),
                            Timestamp.from(clockProvider.now())
                        );
                        handled++;
                    }
                    
                    case DepositMade deposit -> {
                        // Update balance by adding deposit amount (idempotent via new_balance)
                        writeJdbc.update("""
                            UPDATE wallet_balance_view
                            SET balance = ?,
                                last_updated_at = ?
                            WHERE wallet_id = ?
                            """,
                            BigDecimal.valueOf(deposit.newBalance()),
                            Timestamp.from(clockProvider.now()),
                            deposit.walletId()
                        );
                        handled++;
                    }
                    
                    case WithdrawalMade withdrawal -> {
                        // Update balance using new_balance from event (idempotent)
                        writeJdbc.update("""
                            UPDATE wallet_balance_view
                            SET balance = ?,
                                last_updated_at = ?
                            WHERE wallet_id = ?
                            """,
                            BigDecimal.valueOf(withdrawal.newBalance()),
                            Timestamp.from(clockProvider.now()),
                            withdrawal.walletId()
                        );
                        handled++;
                    }
                    
                    case MoneyTransferred transfer -> {
                        // Update both wallets' balances (idempotent via new balances from event)
                        writeJdbc.update("""
                            UPDATE wallet_balance_view
                            SET balance = ?,
                                last_updated_at = ?
                            WHERE wallet_id = ?
                            """,
                            BigDecimal.valueOf(transfer.fromBalance()),
                            Timestamp.from(clockProvider.now()),
                            transfer.fromWalletId()
                        );
                        
                        writeJdbc.update("""
                            UPDATE wallet_balance_view
                            SET balance = ?,
                                last_updated_at = ?
                            WHERE wallet_id = ?
                            """,
                            BigDecimal.valueOf(transfer.toBalance()),
                            Timestamp.from(clockProvider.now()),
                            transfer.toWalletId()
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

