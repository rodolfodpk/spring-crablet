package com.crablet.wallet.view.projectors;

import com.crablet.eventstore.ClockProvider;
import com.crablet.eventstore.StoredEvent;
import com.crablet.eventstore.Tag;
import com.crablet.eventstore.WriteDataSource;
import com.crablet.examples.wallet.events.DepositMade;
import com.crablet.examples.wallet.events.MoneyTransferred;
import com.crablet.examples.wallet.events.WalletEvent;
import com.crablet.examples.wallet.events.WalletOpened;
import com.crablet.examples.wallet.events.WalletStatementClosed;
import com.crablet.examples.wallet.events.WalletStatementOpened;
import com.crablet.examples.wallet.events.WithdrawalMade;
import com.crablet.views.AbstractTypedViewProjector;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static com.crablet.examples.wallet.WalletTags.DAY;
import static com.crablet.examples.wallet.WalletTags.FROM_DAY;
import static com.crablet.examples.wallet.WalletTags.FROM_HOUR;
import static com.crablet.examples.wallet.WalletTags.FROM_MONTH;
import static com.crablet.examples.wallet.WalletTags.FROM_YEAR;
import static com.crablet.examples.wallet.WalletTags.HOUR;
import static com.crablet.examples.wallet.WalletTags.MONTH;
import static com.crablet.examples.wallet.WalletTags.TO_DAY;
import static com.crablet.examples.wallet.WalletTags.TO_HOUR;
import static com.crablet.examples.wallet.WalletTags.TO_MONTH;
import static com.crablet.examples.wallet.WalletTags.TO_YEAR;
import static com.crablet.examples.wallet.WalletTags.YEAR;

/**
 * View projector for wallet statement periods.
 * Projects statement periods with opening/closing balances and period totals.
 * Supports reconciliation by tracking all transaction activity per period.
 */
@Component
public class WalletStatementViewProjector extends AbstractTypedViewProjector<WalletEvent> {

    private static final Logger log = LoggerFactory.getLogger(WalletStatementViewProjector.class);

    // SQL for statement events
    private static final String INSERT_STATEMENT_OPENED = """
        INSERT INTO wallet_statement_view 
            (statement_id, wallet_id, year, month, day, hour, opening_balance, opened_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (statement_id) DO UPDATE SET
            opening_balance = EXCLUDED.opening_balance,
            opened_at = EXCLUDED.opened_at
        """;

    private static final String UPDATE_STATEMENT_CLOSED = """
        UPDATE wallet_statement_view
        SET closing_balance = ?,
            closed_at = ?
        WHERE statement_id = ?
        """;

    // SQL for idempotent event tracking
    private static final String INSERT_PROCESSED_EVENT = """
        INSERT INTO statement_transactions (statement_id, event_position)
        VALUES (?, ?)
        ON CONFLICT (statement_id, event_position) DO NOTHING
        """;

    // SQL for updating totals (only if event was newly inserted)
    private static final String UPDATE_DEPOSIT_TOTALS = """
        UPDATE wallet_statement_view
        SET total_deposits = total_deposits + ?,
            transaction_count = transaction_count + 1
        WHERE statement_id = ?
        """;

    private static final String UPDATE_WITHDRAWAL_TOTALS = """
        UPDATE wallet_statement_view
        SET total_withdrawals = total_withdrawals + ?,
            transaction_count = transaction_count + 1
        WHERE statement_id = ?
        """;

    private static final String UPDATE_TRANSFER_OUT_TOTALS = """
        UPDATE wallet_statement_view
        SET total_transfers_out = total_transfers_out + ?,
            transaction_count = transaction_count + 1
        WHERE statement_id = ?
        """;

    private static final String UPDATE_TRANSFER_IN_TOTALS = """
        UPDATE wallet_statement_view
        SET total_transfers_in = total_transfers_in + ?,
            transaction_count = transaction_count + 1
        WHERE statement_id = ?
        """;

    public WalletStatementViewProjector(
            ObjectMapper objectMapper,
            ClockProvider clockProvider,
            PlatformTransactionManager transactionManager,
            WriteDataSource writeDataSource) {
        super(objectMapper, clockProvider, transactionManager, writeDataSource);
    }

    @Override
    public String getViewName() {
        return "wallet-statement-view";
    }

    @Override
    protected Class<WalletEvent> getEventType() {
        return WalletEvent.class;
    }

    @Override
    protected boolean handleEvent(WalletEvent walletEvent, StoredEvent storedEvent, JdbcTemplate jdbcTemplate) {
        return switch (walletEvent) {
            case WalletStatementOpened opened -> handleStatementOpened(opened, jdbcTemplate);
            case WalletStatementClosed closed -> handleStatementClosed(closed, jdbcTemplate);
            case DepositMade deposit -> handleDepositMade(deposit, storedEvent, jdbcTemplate);
            case WithdrawalMade withdrawal -> handleWithdrawalMade(withdrawal, storedEvent, jdbcTemplate);
            case MoneyTransferred transfer -> handleMoneyTransferred(transfer, storedEvent, jdbcTemplate);
            case WalletOpened _ -> false; // Not a statement event
        };
    }

    private boolean handleStatementOpened(WalletStatementOpened opened, JdbcTemplate jdbc) {
        String statementId = opened.statementId();
        jdbc.update(INSERT_STATEMENT_OPENED,
            statementId,
            opened.walletId(),
            opened.year(),
            opened.month(),
            opened.day(),
            opened.hour(),
            BigDecimal.valueOf(opened.openingBalance()),
            Timestamp.from(opened.openedAt())
        );
        return true;
    }

    private boolean handleStatementClosed(WalletStatementClosed closed, JdbcTemplate jdbc) {
        String statementId = closed.statementId();
        jdbc.update(UPDATE_STATEMENT_CLOSED,
            BigDecimal.valueOf(closed.closingBalance()),
            Timestamp.from(closed.closedAt()),
            statementId
        );
        return true;
    }

    private boolean handleDepositMade(DepositMade deposit, StoredEvent storedEvent, JdbcTemplate jdbc) {
        String statementId = extractStatementId(storedEvent, deposit.walletId());
        if (statementId == null) {
            log.warn("Cannot determine statement_id for DepositMade event at position {}", storedEvent.position());
            return false;
        }

        if (!recordProcessedEvent(jdbc, statementId, storedEvent.position())) {
            return false;
        }

        int updated = jdbc.update(UPDATE_DEPOSIT_TOTALS,
            BigDecimal.valueOf(deposit.amount()),
            statementId
        );

        if (updated > 0) {
            log.debug("Updated deposit totals for statement {}: +{}", statementId, deposit.amount());
        }
        return true;
    }

    private boolean handleWithdrawalMade(WithdrawalMade withdrawal, StoredEvent storedEvent, JdbcTemplate jdbc) {
        String statementId = extractStatementId(storedEvent, withdrawal.walletId());
        if (statementId == null) {
            log.warn("Cannot determine statement_id for WithdrawalMade event at position {}", storedEvent.position());
            return false;
        }

        if (!recordProcessedEvent(jdbc, statementId, storedEvent.position())) {
            return false;
        }

        int updated = jdbc.update(UPDATE_WITHDRAWAL_TOTALS,
            BigDecimal.valueOf(withdrawal.amount()),
            statementId
        );

        if (updated > 0) {
            log.debug("Updated withdrawal totals for statement {}: +{}", statementId, withdrawal.amount());
        }
        return true;
    }

    private boolean handleMoneyTransferred(MoneyTransferred transfer, StoredEvent storedEvent, JdbcTemplate jdbc) {
        // Process for FROM wallet
        String fromStatementId = extractStatementIdForTransfer(storedEvent, transfer.fromWalletId(), true);
        if (fromStatementId != null && recordProcessedEvent(jdbc, fromStatementId, storedEvent.position())) {
            int updated = jdbc.update(UPDATE_TRANSFER_OUT_TOTALS,
                BigDecimal.valueOf(transfer.amount()),
                fromStatementId
            );
            if (updated > 0) {
                log.debug("Updated transfer-out totals for statement {}: +{}", fromStatementId, transfer.amount());
            }
        }

        // Process for TO wallet
        String toStatementId = extractStatementIdForTransfer(storedEvent, transfer.toWalletId(), false);
        if (toStatementId != null && recordProcessedEvent(jdbc, toStatementId, storedEvent.position())) {
            int updated = jdbc.update(UPDATE_TRANSFER_IN_TOTALS,
                BigDecimal.valueOf(transfer.amount()),
                toStatementId
            );
            if (updated > 0) {
                log.debug("Updated transfer-in totals for statement {}: +{}", toStatementId, transfer.amount());
            }
        }

        return true;
    }

    private boolean recordProcessedEvent(JdbcTemplate jdbc, String statementId, long eventPosition) {
        return jdbc.update(INSERT_PROCESSED_EVENT, statementId, eventPosition) > 0;
    }

    /**
     * Extract statement_id from event tags for regular events (deposits, withdrawals).
     */
    private String extractStatementId(StoredEvent event, String walletId) {
        List<Tag> tags = event.tags();
        Optional<Integer> year = extractTagValue(tags, YEAR, Integer::parseInt);
        if (year.isEmpty()) {
            return null; // No period tags
        }
        Integer month = extractTagValue(tags, MONTH, Integer::parseInt).orElse(null);
        Integer day = extractTagValue(tags, DAY, Integer::parseInt).orElse(null);
        Integer hour = extractTagValue(tags, HOUR, Integer::parseInt).orElse(null);
        return buildStatementId(walletId, year.get(), month, day, hour);
    }

    /**
     * Extract statement_id from event tags for transfer events.
     * Uses from_* tags for FROM wallet, to_* tags for TO wallet.
     */
    private String extractStatementIdForTransfer(StoredEvent event, String walletId, boolean isFromWallet) {
        List<Tag> tags = event.tags();
        String yearTag = isFromWallet ? FROM_YEAR : TO_YEAR;
        String monthTag = isFromWallet ? FROM_MONTH : TO_MONTH;
        String dayTag = isFromWallet ? FROM_DAY : TO_DAY;
        String hourTag = isFromWallet ? FROM_HOUR : TO_HOUR;

        // Try transfer-specific tags first, fallback to regular tags
        Optional<Integer> year = extractTagValue(tags, yearTag, Integer::parseInt);
        String resolvedMonthTag = monthTag;
        String resolvedDayTag = dayTag;
        String resolvedHourTag = hourTag;
        if (year.isEmpty()) {
            year = extractTagValue(tags, YEAR, Integer::parseInt);
            resolvedMonthTag = MONTH;
            resolvedDayTag = DAY;
            resolvedHourTag = HOUR;
        }

        if (year.isEmpty()) {
            return null; // No period tags
        }

        Integer month = extractTagValue(tags, resolvedMonthTag, Integer::parseInt).orElse(null);
        Integer day = extractTagValue(tags, resolvedDayTag, Integer::parseInt).orElse(null);
        Integer hour = extractTagValue(tags, resolvedHourTag, Integer::parseInt).orElse(null);
        return buildStatementId(walletId, year.get(), month, day, hour);
    }

    /**
     * Extract tag value from tags list.
     */
    private <T> Optional<T> extractTagValue(List<Tag> tags, String tagKey, Function<String, T> parser) {
        return tags.stream()
            .filter(t -> tagKey.equals(t.key()))
            .findFirst()
            .flatMap(tag -> {
                try {
                    return Optional.ofNullable(parser.apply(tag.value()));
                } catch (Exception e) {
                    log.warn("Failed to parse tag value: {} = {}", tagKey, tag.value(), e);
                    return Optional.empty();
                }
            });
    }

    /**
     * Build statement_id from period components.
     * Format matches WalletStatementId.toStreamId():
     * - Hourly: wallet:{walletId}:{year}-{month:02d}-{day:02d}-{hour:02d}
     * - Daily: wallet:{walletId}:{year}-{month:02d}-{day:02d}
     * - Monthly: wallet:{walletId}:{year}-{month:02d}
     * - Yearly: wallet:{walletId}:{year}
     */
    private String buildStatementId(String walletId, int year, Integer month, Integer day, Integer hour) {
        if (hour != null && day != null && month != null) {
            // Hourly
            return String.format("wallet:%s:%d-%02d-%02d-%02d", walletId, year, month, day, hour);
        } else if (day != null && month != null) {
            // Daily
            return String.format("wallet:%s:%d-%02d-%02d", walletId, year, month, day);
        } else if (month != null) {
            // Monthly
            return String.format("wallet:%s:%d-%02d", walletId, year, month);
        } else {
            // Yearly
            return String.format("wallet:%s:%d", walletId, year);
        }
    }
}
