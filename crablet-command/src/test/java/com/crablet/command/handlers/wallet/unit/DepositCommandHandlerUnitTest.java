package com.crablet.command.handlers.wallet.unit;

import com.crablet.command.handlers.unit.AbstractHandlerUnitTest;
import com.crablet.command.handlers.wallet.DepositCommandHandler;
import com.crablet.examples.wallet.event.DepositMade;
import com.crablet.examples.wallet.event.WalletOpened;
import com.crablet.examples.wallet.event.WalletStatementOpened;
import com.crablet.examples.wallet.exception.WalletNotFoundException;
import com.crablet.examples.wallet.features.deposit.DepositCommand;
import com.crablet.examples.wallet.period.WalletPeriodHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.crablet.eventstore.store.EventType.type;
import static com.crablet.examples.wallet.WalletTags.WALLET_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DepositCommandHandler}.
 * <p>
 * These tests focus on business logic validation and happy paths.
 * DCB concurrency is tested in integration tests.
 * <p>
 * <strong>Test Strategy:</strong>
 * <ul>
 *   <li>Regular tests: Verify balance accumulation and event generation</li>
 *   <li>Period tests: Verify period tags are added correctly</li>
 *   <li>Error cases: Verify wallet not found exception</li>
 * </ul>
 */
@DisplayName("DepositCommandHandler Unit Tests")
class DepositCommandHandlerUnitTest extends AbstractHandlerUnitTest {
    
    private DepositCommandHandler handler;
    private WalletPeriodHelper periodHelper;
    
    @BeforeEach
    @Override
    protected void setUp() {
        super.setUp();
        periodHelper = WalletPeriodHelperTestFactory.createTestHelper(eventStore);
        handler = new DepositCommandHandler(periodHelper);
    }
    
    @Test
    @DisplayName("Given wallet with balance, when depositing, then balance increases")
    void givenWalletWithBalance_whenDepositing_thenBalanceIncreases() {
        // Given
        given().event(type(WalletOpened.class), builder -> builder
            .data(WalletOpened.of("wallet1", "Alice", 1000))
            .tag(WALLET_ID, "wallet1")
        );
        
        // When
        DepositCommand command = DepositCommand.of("deposit1", "wallet1", 500, "Bonus payment");
        List<Object> events = when(handler, command);
        
        // Then
        then(events, DepositMade.class, deposit -> {
            assertThat(deposit.walletId()).isEqualTo("wallet1");
            assertThat(deposit.amount()).isEqualTo(500);
            assertThat(deposit.newBalance()).isEqualTo(1500); // 1000 + 500
            assertThat(deposit.description()).isEqualTo("Bonus payment");
        });
    }
    
    @Test
    @DisplayName("Given no events, when depositing, then wallet not found exception")
    void givenNoEvents_whenDepositing_thenWalletNotFoundException() {
        // Given: No events (empty event store)
        
        // When & Then
        DepositCommand command = DepositCommand.of("deposit1", "wallet1", 500, "Bonus");
        assertThatThrownBy(() -> when(handler, command))
            .isInstanceOf(WalletNotFoundException.class)
            .hasMessageContaining("wallet1");
    }
    
    @Test
    @DisplayName("Given wallet with previous deposits, when depositing, then balance accumulates correctly")
    void givenWalletWithPreviousDeposits_whenDepositing_thenBalanceAccumulatesCorrectly() {
        // Given
        given().event(type(WalletOpened.class), builder -> builder
            .data(WalletOpened.of("wallet1", "Alice", 1000))
            .tag(WALLET_ID, "wallet1")
        );
        given().event(type(DepositMade.class), builder -> builder
            .data(DepositMade.of("deposit1", "wallet1", 200, 1200, "First deposit"))
            .tag(WALLET_ID, "wallet1")
        );
        given().event(type(DepositMade.class), builder -> builder
            .data(DepositMade.of("deposit2", "wallet1", 300, 1500, "Second deposit"))
            .tag(WALLET_ID, "wallet1")
        );
        
        // When
        DepositCommand command = DepositCommand.of("deposit3", "wallet1", 400, "Third deposit");
        List<Object> events = when(handler, command);
        
        // Then
        then(events, DepositMade.class, deposit -> {
            assertThat(deposit.amount()).isEqualTo(400);
            assertThat(deposit.newBalance()).isEqualTo(1900); // 1000 + 200 + 300 + 400
        });
    }
    
    @Test
    @DisplayName("Given wallet, when depositing, then deposit has correct period tags")
    void givenWallet_whenDepositing_thenDepositHasCorrectPeriodTags() {
        // Given - WalletStatementOpened will be created automatically by periodHelper
        given().event(type(WalletOpened.class), builder -> builder
            .data(WalletOpened.of("wallet1", "Alice", 1000))
            .tag(WALLET_ID, "wallet1")
        );
        
        // When - get events with tags
        DepositCommand command = DepositCommand.of("deposit1", "wallet1", 500, "Bonus");
        List<EventWithTags<Object>> events = whenWithTags(handler, command);
        
        // Then - verify business logic AND period tags
        then(events, DepositMade.class, (deposit, tags) -> {
            // Business logic
            assertThat(deposit.walletId()).isEqualTo("wallet1");
            assertThat(deposit.amount()).isEqualTo(500);
            assertThat(deposit.newBalance()).isEqualTo(1500);
            
            // Period tags (WalletPeriodHelperTestFactory uses fixed period 2025-01)
            assertThat(tags).containsEntry("wallet_id", "wallet1");
            assertThat(tags).containsEntry("deposit_id", "deposit1");
            assertThat(tags).containsEntry("year", "2025");
            assertThat(tags).containsEntry("month", "1");
        });
    }
    
    @Test
    @DisplayName("Given wallet with existing period, when depositing, then balance includes opening balance")
    void givenWalletWithExistingPeriod_whenDepositing_thenBalanceIncludesOpeningBalance() {
        // Given - pre-seed WalletStatementOpened for period
        given().event(type(WalletOpened.class), builder -> builder
            .data(WalletOpened.of("wallet1", "Alice", 1000))
            .tag(WALLET_ID, "wallet1")
        );
        given().eventWithMonthlyPeriod(
            type(WalletStatementOpened.class),
            com.crablet.examples.wallet.event.WalletStatementOpened.of(
                "wallet1", "wallet:wallet1:2025-01", 2025, 1, null, null, 1000),
            "wallet1",
            2025, 1
        );
        given().event(type(DepositMade.class), builder -> builder
            .data(DepositMade.of("deposit1", "wallet1", 200, 1200, "Previous deposit"))
            .tag(WALLET_ID, "wallet1")
            .tag("year", "2025")
            .tag("month", "1")
        );
        
        // When
        DepositCommand command = DepositCommand.of("deposit2", "wallet1", 300, "New deposit");
        List<Object> events = when(handler, command);
        
        // Then - balance should include opening balance (1000) + previous deposit (200) + new deposit (300)
        then(events, DepositMade.class, deposit -> {
            assertThat(deposit.newBalance()).isEqualTo(1500); // 1000 + 200 + 300
        });
    }
}

