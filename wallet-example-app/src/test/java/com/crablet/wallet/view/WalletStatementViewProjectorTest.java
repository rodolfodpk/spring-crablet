package com.crablet.wallet.view;

import com.crablet.eventprocessor.processor.EventProcessor;
import com.crablet.eventstore.dcb.AppendCondition;
import com.crablet.eventstore.store.AppendEvent;
import com.crablet.examples.wallet.events.DepositMade;
import com.crablet.examples.wallet.events.MoneyTransferred;
import com.crablet.examples.wallet.events.WalletStatementClosed;
import com.crablet.examples.wallet.events.WalletStatementOpened;
import com.crablet.examples.wallet.events.WithdrawalMade;
import com.crablet.views.adapter.ViewProcessorConfig;
import com.crablet.wallet.AbstractWalletTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static com.crablet.eventstore.store.EventType.type;
import static com.crablet.examples.wallet.WalletTags.DAY;
import static com.crablet.examples.wallet.WalletTags.DEPOSIT_ID;
import static com.crablet.examples.wallet.WalletTags.FROM_DAY;
import static com.crablet.examples.wallet.WalletTags.FROM_HOUR;
import static com.crablet.examples.wallet.WalletTags.FROM_MONTH;
import static com.crablet.examples.wallet.WalletTags.FROM_WALLET_ID;
import static com.crablet.examples.wallet.WalletTags.FROM_YEAR;
import static com.crablet.examples.wallet.WalletTags.HOUR;
import static com.crablet.examples.wallet.WalletTags.MONTH;
import static com.crablet.examples.wallet.WalletTags.STATEMENT_ID;
import static com.crablet.examples.wallet.WalletTags.TO_DAY;
import static com.crablet.examples.wallet.WalletTags.TO_HOUR;
import static com.crablet.examples.wallet.WalletTags.TO_MONTH;
import static com.crablet.examples.wallet.WalletTags.TO_WALLET_ID;
import static com.crablet.examples.wallet.WalletTags.TO_YEAR;
import static com.crablet.examples.wallet.WalletTags.TRANSFER_ID;
import static com.crablet.examples.wallet.WalletTags.WALLET_ID;
import static com.crablet.examples.wallet.WalletTags.WITHDRAWAL_ID;
import static com.crablet.examples.wallet.WalletTags.YEAR;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for WalletStatementViewProjector.
 * <p>
 * Tests statement period tracking, transaction aggregation, period extraction,
 * idempotency, and edge cases.
 */
@DisplayName("Wallet Statement View Projector Tests")
class WalletStatementViewProjectorTest extends AbstractWalletTest {

    @Autowired
    @Qualifier("viewsEventProcessor")
    private EventProcessor<ViewProcessorConfig, String> viewsEventProcessor;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String WALLET_ID_1 = "wallet-stmt-test-1";
    private static final String WALLET_ID_2 = "wallet-stmt-test-2";

    /**
     * Process statement view synchronously.
     */
    private int processStatementView() {
        return viewsEventProcessor.process("wallet-statement-view");
    }

    /**
     * Create WalletStatementOpened event and append to event store.
     */
    private void appendStatementOpened(String walletId, String statementId, int year, Integer month, 
                                       Integer day, Integer hour, int openingBalance) throws Exception {
        WalletStatementOpened event = WalletStatementOpened.of(
            walletId, statementId, year, month, day, hour, openingBalance
        );
        byte[] data = objectMapper.writeValueAsBytes(event);
        
        AppendEvent.Builder builder = AppendEvent.builder(type(WalletStatementOpened.class))
            .tag(STATEMENT_ID, statementId)
            .tag(WALLET_ID, walletId)
            .tag(YEAR, String.valueOf(year));
        
        if (month != null) {
            builder.tag(MONTH, String.valueOf(month));
        }
        if (day != null) {
            builder.tag(DAY, String.valueOf(day));
        }
        if (hour != null) {
            builder.tag(HOUR, String.valueOf(hour));
        }
        
        eventStore.appendIf(List.of(builder.data(data).build()), AppendCondition.empty());
    }

    /**
     * Create WalletStatementClosed event and append to event store.
     */
    private void appendStatementClosed(String walletId, String statementId, int year, Integer month,
                                      Integer day, Integer hour, int openingBalance, int closingBalance) throws Exception {
        WalletStatementClosed event = WalletStatementClosed.of(
            walletId, statementId, year, month, day, hour, openingBalance, closingBalance
        );
        byte[] data = objectMapper.writeValueAsBytes(event);
        
        AppendEvent.Builder builder = AppendEvent.builder(type(WalletStatementClosed.class))
            .tag(STATEMENT_ID, statementId)
            .tag(WALLET_ID, walletId)
            .tag(YEAR, String.valueOf(year));
        
        if (month != null) {
            builder.tag(MONTH, String.valueOf(month));
        }
        if (day != null) {
            builder.tag(DAY, String.valueOf(day));
        }
        if (hour != null) {
            builder.tag(HOUR, String.valueOf(hour));
        }
        
        eventStore.appendIf(List.of(builder.data(data).build()), AppendCondition.empty());
    }

    /**
     * Create DepositMade event with period tags and append to event store.
     */
    private void appendDeposit(String walletId, String depositId, int amount, int newBalance, 
                               int year, int month, Integer day, Integer hour) throws Exception {
        DepositMade deposit = DepositMade.of(depositId, walletId, amount, newBalance, "Test deposit");
        byte[] data = objectMapper.writeValueAsBytes(deposit);
        
        AppendEvent.Builder builder = AppendEvent.builder(type(DepositMade.class))
            .tag(WALLET_ID, walletId)
            .tag(DEPOSIT_ID, depositId)
            .tag(YEAR, String.valueOf(year))
            .tag(MONTH, String.valueOf(month));
        
        if (day != null) {
            builder.tag(DAY, String.valueOf(day));
        }
        if (hour != null) {
            builder.tag(HOUR, String.valueOf(hour));
        }
        
        eventStore.appendIf(List.of(builder.data(data).build()), AppendCondition.empty());
    }

    /**
     * Create WithdrawalMade event with period tags and append to event store.
     */
    private void appendWithdrawal(String walletId, String withdrawalId, int amount, int newBalance,
                                  int year, int month, Integer day, Integer hour) throws Exception {
        WithdrawalMade withdrawal = WithdrawalMade.of(withdrawalId, walletId, amount, newBalance, "Test withdrawal");
        byte[] data = objectMapper.writeValueAsBytes(withdrawal);
        
        AppendEvent.Builder builder = AppendEvent.builder(type(WithdrawalMade.class))
            .tag(WALLET_ID, walletId)
            .tag(WITHDRAWAL_ID, withdrawalId)
            .tag(YEAR, String.valueOf(year))
            .tag(MONTH, String.valueOf(month));
        
        if (day != null) {
            builder.tag(DAY, String.valueOf(day));
        }
        if (hour != null) {
            builder.tag(HOUR, String.valueOf(hour));
        }
        
        eventStore.appendIf(List.of(builder.data(data).build()), AppendCondition.empty());
    }

    /**
     * Create MoneyTransferred event with period tags and append to event store.
     */
    private void appendTransfer(String fromWalletId, String toWalletId, String transferId, int amount,
                               int fromBalance, int toBalance, int fromYear, int fromMonth, Integer fromDay, Integer fromHour,
                               int toYear, int toMonth, Integer toDay, Integer toHour) throws Exception {
        MoneyTransferred transfer = MoneyTransferred.of(
            transferId, fromWalletId, toWalletId, amount, fromBalance, toBalance, "Test transfer"
        );
        byte[] data = objectMapper.writeValueAsBytes(transfer);
        
        AppendEvent.Builder builder = AppendEvent.builder(type(MoneyTransferred.class))
            .tag(TRANSFER_ID, transferId)
            .tag(FROM_WALLET_ID, fromWalletId)
            .tag(TO_WALLET_ID, toWalletId)
            .tag(FROM_YEAR, String.valueOf(fromYear))
            .tag(FROM_MONTH, String.valueOf(fromMonth))
            .tag(TO_YEAR, String.valueOf(toYear))
            .tag(TO_MONTH, String.valueOf(toMonth));
        
        if (fromDay != null) {
            builder.tag(FROM_DAY, String.valueOf(fromDay));
        }
        if (fromHour != null) {
            builder.tag(FROM_HOUR, String.valueOf(fromHour));
        }
        if (toDay != null) {
            builder.tag(TO_DAY, String.valueOf(toDay));
        }
        if (toHour != null) {
            builder.tag(TO_HOUR, String.valueOf(toHour));
        }
        
        eventStore.appendIf(List.of(builder.data(data).build()), AppendCondition.empty());
    }

    /**
     * Assert statement exists with expected values.
     */
    private void assertStatementExists(String statementId, int expectedOpeningBalance, Integer expectedClosingBalance) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT * FROM wallet_statement_view WHERE statement_id = ?", statementId
        );
        assertThat(rows).hasSize(1);
        
        Map<String, Object> row = rows.get(0);
        assertThat(row.get("statement_id")).isEqualTo(statementId);
        assertThat(((BigDecimal) row.get("opening_balance")).intValue()).isEqualTo(expectedOpeningBalance);
        
        if (expectedClosingBalance != null) {
            assertThat(row.get("closing_balance")).isNotNull();
            assertThat(((BigDecimal) row.get("closing_balance")).intValue()).isEqualTo(expectedClosingBalance);
            assertThat(row.get("closed_at")).isNotNull();
        } else {
            assertThat(row.get("closing_balance")).isNull();
            assertThat(row.get("closed_at")).isNull();
        }
    }

    // ========== Category 1: Statement Events Processing ==========

    @Test
    @DisplayName("Should create statement when WalletStatementOpened event received")
    void shouldCreateStatement_WhenStatementOpenedEventReceived() throws Exception {
        // Given
        String statementId = "wallet:" + WALLET_ID_1 + ":2024-01";
        
        // When
        appendStatementOpened(WALLET_ID_1, statementId, 2024, 1, null, null, 100);
        int processed = processStatementView();
        
        // Then
        assertThat(processed).isGreaterThan(0);
        assertStatementExists(statementId, 100, null);
        
        Map<String, Object> row = jdbcTemplate.queryForList(
            "SELECT * FROM wallet_statement_view WHERE statement_id = ?", statementId
        ).get(0);
        assertThat(row.get("wallet_id")).isEqualTo(WALLET_ID_1);
        assertThat(row.get("year")).isEqualTo(2024);
        assertThat(row.get("month")).isEqualTo(1);
        assertThat(row.get("day")).isNull();
        assertThat(row.get("hour")).isNull();
        assertThat(row.get("opened_at")).isNotNull();
    }

    @Test
    @DisplayName("Should update statement when WalletStatementClosed event received")
    void shouldUpdateStatement_WhenStatementClosedEventReceived() throws Exception {
        // Given
        String statementId = "wallet:" + WALLET_ID_1 + ":2024-01";
        appendStatementOpened(WALLET_ID_1, statementId, 2024, 1, null, null, 100);
        processStatementView();
        
        // When
        appendStatementClosed(WALLET_ID_1, statementId, 2024, 1, null, null, 100, 150);
        int processed = processStatementView();
        
        // Then
        assertThat(processed).isGreaterThan(0);
        assertStatementExists(statementId, 100, 150);
    }

    @Test
    @DisplayName("Should handle multiple statements for same wallet")
    void shouldHandleMultipleStatements_ForSameWallet() throws Exception {
        // Given
        String statementId1 = "wallet:" + WALLET_ID_1 + ":2024-01";
        String statementId2 = "wallet:" + WALLET_ID_1 + ":2024-02";
        
        // When
        appendStatementOpened(WALLET_ID_1, statementId1, 2024, 1, null, null, 100);
        appendStatementOpened(WALLET_ID_1, statementId2, 2024, 2, null, null, 150);
        int processed = processStatementView();
        
        // Then
        assertThat(processed).isGreaterThan(0);
        assertStatementExists(statementId1, 100, null);
        assertStatementExists(statementId2, 150, null);
        
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM wallet_statement_view WHERE wallet_id = ?", Long.class, WALLET_ID_1
        );
        assertThat(count).isEqualTo(2);
    }

    // ========== Category 2: Transaction Aggregation ==========

    @Test
    @DisplayName("Should aggregate deposits in period")
    void shouldAggregateDeposits_InPeriod() throws Exception {
        // Given
        String statementId = "wallet:" + WALLET_ID_1 + ":2024-01";
        appendStatementOpened(WALLET_ID_1, statementId, 2024, 1, null, null, 100);
        processStatementView();
        
        // When
        appendDeposit(WALLET_ID_1, "deposit-1", 50, 150, 2024, 1, null, null);
        appendDeposit(WALLET_ID_1, "deposit-2", 30, 180, 2024, 1, null, null);
        int processed = processStatementView();
        
        // Then
        assertThat(processed).isGreaterThan(0);
        Map<String, Object> row = jdbcTemplate.queryForList(
            "SELECT * FROM wallet_statement_view WHERE statement_id = ?", statementId
        ).get(0);
        
        assertThat(((BigDecimal) row.get("total_deposits")).intValue()).isEqualTo(80);
        assertThat(((Integer) row.get("transaction_count"))).isEqualTo(2);
    }

    @Test
    @DisplayName("Should aggregate withdrawals in period")
    void shouldAggregateWithdrawals_InPeriod() throws Exception {
        // Given
        String statementId = "wallet:" + WALLET_ID_1 + ":2024-01";
        appendStatementOpened(WALLET_ID_1, statementId, 2024, 1, null, null, 100);
        processStatementView();
        
        // When
        appendWithdrawal(WALLET_ID_1, "withdrawal-1", 20, 80, 2024, 1, null, null);
        appendWithdrawal(WALLET_ID_1, "withdrawal-2", 10, 70, 2024, 1, null, null);
        int processed = processStatementView();
        
        // Then
        assertThat(processed).isGreaterThan(0);
        Map<String, Object> row = jdbcTemplate.queryForList(
            "SELECT * FROM wallet_statement_view WHERE statement_id = ?", statementId
        ).get(0);
        
        assertThat(((BigDecimal) row.get("total_withdrawals")).intValue()).isEqualTo(30);
        assertThat(((Integer) row.get("transaction_count"))).isEqualTo(2);
    }

    @Test
    @DisplayName("Should aggregate transfers in period for both wallets")
    void shouldAggregateTransfers_InPeriod_ForBothWallets() throws Exception {
        // Given
        String statementId1 = "wallet:" + WALLET_ID_1 + ":2024-01";
        String statementId2 = "wallet:" + WALLET_ID_2 + ":2024-01";
        appendStatementOpened(WALLET_ID_1, statementId1, 2024, 1, null, null, 100);
        appendStatementOpened(WALLET_ID_2, statementId2, 2024, 1, null, null, 50);
        processStatementView();
        
        // When
        appendTransfer(WALLET_ID_1, WALLET_ID_2, "transfer-1", 25, 75, 75,
                      2024, 1, null, null, 2024, 1, null, null);
        int processed = processStatementView();
        
        // Then
        assertThat(processed).isGreaterThan(0);
        
        // FROM wallet
        Map<String, Object> fromRow = jdbcTemplate.queryForList(
            "SELECT * FROM wallet_statement_view WHERE statement_id = ?", statementId1
        ).get(0);
        assertThat(((BigDecimal) fromRow.get("total_transfers_out")).intValue()).isEqualTo(25);
        assertThat(((Integer) fromRow.get("transaction_count"))).isEqualTo(1);
        
        // TO wallet
        Map<String, Object> toRow = jdbcTemplate.queryForList(
            "SELECT * FROM wallet_statement_view WHERE statement_id = ?", statementId2
        ).get(0);
        assertThat(((BigDecimal) toRow.get("total_transfers_in")).intValue()).isEqualTo(25);
        assertThat(((Integer) toRow.get("transaction_count"))).isEqualTo(1);
    }

    @Test
    @DisplayName("Should aggregate mixed transactions in period")
    void shouldAggregateMixedTransactions_InPeriod() throws Exception {
        // Given
        String statementId = "wallet:" + WALLET_ID_1 + ":2024-01";
        appendStatementOpened(WALLET_ID_1, statementId, 2024, 1, null, null, 100);
        processStatementView();
        
        // When
        appendDeposit(WALLET_ID_1, "deposit-1", 50, 150, 2024, 1, null, null);
        appendWithdrawal(WALLET_ID_1, "withdrawal-1", 20, 130, 2024, 1, null, null);
        appendDeposit(WALLET_ID_1, "deposit-2", 30, 160, 2024, 1, null, null);
        int processed = processStatementView();
        
        // Then
        assertThat(processed).isGreaterThan(0);
        Map<String, Object> row = jdbcTemplate.queryForList(
            "SELECT * FROM wallet_statement_view WHERE statement_id = ?", statementId
        ).get(0);
        
        assertThat(((BigDecimal) row.get("total_deposits")).intValue()).isEqualTo(80);
        assertThat(((BigDecimal) row.get("total_withdrawals")).intValue()).isEqualTo(20);
        assertThat(((Integer) row.get("transaction_count"))).isEqualTo(3);
    }

    // ========== Category 3: Period Extraction ==========

    @Test
    @DisplayName("Should extract period from regular event tags")
    void shouldExtractPeriod_FromRegularEventTags() throws Exception {
        // Given
        String statementId = "wallet:" + WALLET_ID_1 + ":2024-01";
        appendStatementOpened(WALLET_ID_1, statementId, 2024, 1, null, null, 100);
        processStatementView();
        
        // When - Deposit with period tags
        appendDeposit(WALLET_ID_1, "deposit-1", 50, 150, 2024, 1, null, null);
        int processed = processStatementView();
        
        // Then
        assertThat(processed).isGreaterThan(0);
        Map<String, Object> row = jdbcTemplate.queryForList(
            "SELECT * FROM wallet_statement_view WHERE statement_id = ?", statementId
        ).get(0);
        assertThat(((BigDecimal) row.get("total_deposits")).intValue()).isEqualTo(50);
    }

    @Test
    @DisplayName("Should extract period from transfer event tags")
    void shouldExtractPeriod_FromTransferEventTags() throws Exception {
        // Given
        String statementId1 = "wallet:" + WALLET_ID_1 + ":2024-01";
        String statementId2 = "wallet:" + WALLET_ID_2 + ":2024-02";
        appendStatementOpened(WALLET_ID_1, statementId1, 2024, 1, null, null, 100);
        appendStatementOpened(WALLET_ID_2, statementId2, 2024, 2, null, null, 50);
        processStatementView();
        
        // When - Transfer with different periods for each wallet
        appendTransfer(WALLET_ID_1, WALLET_ID_2, "transfer-1", 25, 75, 75,
                      2024, 1, null, null, 2024, 2, null, null);
        int processed = processStatementView();
        
        // Then
        assertThat(processed).isGreaterThan(0);
        
        // FROM wallet should use from_year, from_month
        Map<String, Object> fromRow = jdbcTemplate.queryForList(
            "SELECT * FROM wallet_statement_view WHERE statement_id = ?", statementId1
        ).get(0);
        assertThat(((BigDecimal) fromRow.get("total_transfers_out")).intValue()).isEqualTo(25);
        
        // TO wallet should use to_year, to_month
        Map<String, Object> toRow = jdbcTemplate.queryForList(
            "SELECT * FROM wallet_statement_view WHERE statement_id = ?", statementId2
        ).get(0);
        assertThat(((BigDecimal) toRow.get("total_transfers_in")).intValue()).isEqualTo(25);
    }

    @Test
    @DisplayName("Should handle missing period tags gracefully")
    void shouldHandleMissingPeriodTags_Gracefully() throws Exception {
        // Given
        DepositMade deposit = DepositMade.of("deposit-1", WALLET_ID_1, 50, 150, "Test");
        byte[] data = objectMapper.writeValueAsBytes(deposit);
        
        // When - Event without period tags
        AppendEvent event = AppendEvent.builder(type(DepositMade.class))
            .tag(WALLET_ID, WALLET_ID_1)
            .tag(DEPOSIT_ID, "deposit-1")
            // No YEAR, MONTH tags
            .data(data)
            .build();
        
        eventStore.appendIf(List.of(event), AppendCondition.empty());
        int processed = processStatementView();
        
        // Then - Event should be processed but skipped (returns true but no statement updated)
        assertThat(processed).isGreaterThanOrEqualTo(0);
        
        // No statement should be created for this event
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM wallet_statement_view WHERE wallet_id = ?", Long.class, WALLET_ID_1
        );
        assertThat(count).isEqualTo(0);
    }

    // ========== Category 4: Idempotency ==========

    @Test
    @DisplayName("Should not double count on event reprocessing")
    void shouldNotDoubleCount_OnEventReprocessing() throws Exception {
        // Given
        String statementId = "wallet:" + WALLET_ID_1 + ":2024-01";
        appendStatementOpened(WALLET_ID_1, statementId, 2024, 1, null, null, 100);
        appendDeposit(WALLET_ID_1, "deposit-1", 50, 150, 2024, 1, null, null);
        
        // When - Process first time
        int processed1 = processStatementView();
        Map<String, Object> row1 = jdbcTemplate.queryForList(
            "SELECT * FROM wallet_statement_view WHERE statement_id = ?", statementId
        ).get(0);
        BigDecimal totalDeposits1 = (BigDecimal) row1.get("total_deposits");
        
        // Process again (simulating reprocessing)
        int processed2 = processStatementView();
        Map<String, Object> row2 = jdbcTemplate.queryForList(
            "SELECT * FROM wallet_statement_view WHERE statement_id = ?", statementId
        ).get(0);
        BigDecimal totalDeposits2 = (BigDecimal) row2.get("total_deposits");
        
        // Then
        assertThat(processed1).isGreaterThan(0);
        assertThat(processed2).isGreaterThanOrEqualTo(0); // May process 0 events if already processed
        assertThat(totalDeposits2).isEqualByComparingTo(totalDeposits1); // Should not change
        assertThat(totalDeposits1.intValue()).isEqualTo(50);
        
        // Verify junction table has the record
        Long junctionCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM statement_transactions WHERE statement_id = ?", Long.class, statementId
        );
        assertThat(junctionCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Should create junction table record on first processing")
    void shouldCreateJunctionTableRecord_OnFirstProcessing() throws Exception {
        // Given
        String statementId = "wallet:" + WALLET_ID_1 + ":2024-01";
        appendStatementOpened(WALLET_ID_1, statementId, 2024, 1, null, null, 100);
        appendDeposit(WALLET_ID_1, "deposit-1", 50, 150, 2024, 1, null, null);
        
        // When
        processStatementView();
        
        // Then - Verify junction table has record
        List<Map<String, Object>> junctionRows = jdbcTemplate.queryForList(
            "SELECT * FROM statement_transactions WHERE statement_id = ?", statementId
        );
        assertThat(junctionRows).hasSize(1);
        assertThat(junctionRows.get(0).get("statement_id")).isEqualTo(statementId);
        assertThat(junctionRows.get(0).get("event_position")).isNotNull();
    }

    // ========== Category 5: Edge Cases ==========

    @Test
    @DisplayName("Should format statement ID correctly for monthly period")
    void shouldFormatStatementId_CorrectlyForMonthlyPeriod() throws Exception {
        // Given
        String expectedStatementId = "wallet:" + WALLET_ID_1 + ":2024-01";
        
        // When
        appendStatementOpened(WALLET_ID_1, expectedStatementId, 2024, 1, null, null, 100);
        processStatementView();
        
        // Then
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT statement_id FROM wallet_statement_view WHERE wallet_id = ?", WALLET_ID_1
        );
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("statement_id")).isEqualTo(expectedStatementId);
    }

    @Test
    @DisplayName("Should calculate period totals correctly")
    void shouldCalculatePeriodTotals_Correctly() throws Exception {
        // Given
        String statementId = "wallet:" + WALLET_ID_1 + ":2024-01";
        int openingBalance = 100;
        appendStatementOpened(WALLET_ID_1, statementId, 2024, 1, null, null, openingBalance);
        processStatementView();
        
        // When
        appendDeposit(WALLET_ID_1, "deposit-1", 50, 150, 2024, 1, null, null);
        appendWithdrawal(WALLET_ID_1, "withdrawal-1", 20, 130, 2024, 1, null, null);
        processStatementView();
        
        // Close statement
        int closingBalance = 130;
        appendStatementClosed(WALLET_ID_1, statementId, 2024, 1, null, null, openingBalance, closingBalance);
        processStatementView();
        
        // Then - Verify balance consistency
        Map<String, Object> row = jdbcTemplate.queryForList(
            "SELECT * FROM wallet_statement_view WHERE statement_id = ?", statementId
        ).get(0);
        
        BigDecimal totalDeposits = (BigDecimal) row.get("total_deposits");
        BigDecimal totalWithdrawals = (BigDecimal) row.get("total_withdrawals");
        BigDecimal opening = (BigDecimal) row.get("opening_balance");
        BigDecimal closing = (BigDecimal) row.get("closing_balance");
        
        // closing_balance - opening_balance = total_deposits - total_withdrawals
        BigDecimal netChange = closing.subtract(opening);
        BigDecimal calculatedNetChange = totalDeposits.subtract(totalWithdrawals);
        
        assertThat(netChange).isEqualByComparingTo(calculatedNetChange);
        assertThat(netChange.intValue()).isEqualTo(30); // 50 - 20
    }
}

