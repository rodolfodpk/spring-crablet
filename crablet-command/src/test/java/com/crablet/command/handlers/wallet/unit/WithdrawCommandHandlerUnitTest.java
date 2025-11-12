package com.crablet.command.handlers.wallet.unit;

import com.crablet.command.handlers.unit.AbstractHandlerUnitTest;
import com.crablet.command.handlers.wallet.WithdrawCommandHandler;
import com.crablet.examples.wallet.event.DepositMade;
import com.crablet.examples.wallet.event.WalletOpened;
import com.crablet.examples.wallet.event.WithdrawalMade;
import com.crablet.examples.wallet.exception.InsufficientFundsException;
import com.crablet.examples.wallet.exception.WalletNotFoundException;
import com.crablet.examples.wallet.features.withdraw.WithdrawCommand;
import com.crablet.examples.wallet.period.WalletPeriodHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.crablet.examples.wallet.WalletEventTypes.*;
import static com.crablet.examples.wallet.WalletTags.WALLET_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link WithdrawCommandHandler}.
 * <p>
 * These tests focus on business logic validation and happy paths.
 * DCB concurrency is tested in integration tests.
 */
@DisplayName("WithdrawCommandHandler Unit Tests")
class WithdrawCommandHandlerUnitTest extends AbstractHandlerUnitTest {
    
    private WithdrawCommandHandler handler;
    private WalletPeriodHelper periodHelper;
    
    @BeforeEach
    @Override
    protected void setUp() {
        super.setUp();
        periodHelper = WalletPeriodHelperTestFactory.createTestHelper(eventStore);
        handler = new WithdrawCommandHandler(periodHelper);
    }
    
    @Test
    @DisplayName("Given wallet with sufficient balance, when withdrawing, then balance decreases")
    void givenWalletWithSufficientBalance_whenWithdrawing_thenBalanceDecreases() {
        // Given
        given().event(WALLET_OPENED, builder -> builder
            .data(WalletOpened.of("wallet1", "Alice", 1000))
            .tag(WALLET_ID, "wallet1")
        );
        
        // When
        WithdrawCommand command = WithdrawCommand.of("withdrawal1", "wallet1", 300, "Shopping");
        List<Object> events = when(handler, command);
        
        // Then
        then(events, WithdrawalMade.class, withdrawal -> {
            assertThat(withdrawal.walletId()).isEqualTo("wallet1");
            assertThat(withdrawal.amount()).isEqualTo(300);
            assertThat(withdrawal.newBalance()).isEqualTo(700); // 1000 - 300
            assertThat(withdrawal.description()).isEqualTo("Shopping");
        });
    }
    
    @Test
    @DisplayName("Given wallet with insufficient balance, when withdrawing, then insufficient funds exception")
    void givenWalletWithInsufficientBalance_whenWithdrawing_thenInsufficientFundsException() {
        // Given
        given().event(WALLET_OPENED, builder -> builder
            .data(WalletOpened.of("wallet1", "Alice", 100))
            .tag(WALLET_ID, "wallet1")
        );
        
        // When & Then
        WithdrawCommand command = WithdrawCommand.of("withdrawal1", "wallet1", 200, "Shopping");
        assertThatThrownBy(() -> when(handler, command))
            .isInstanceOf(InsufficientFundsException.class)
            .hasMessageContaining("wallet1")
            .hasMessageContaining("100")
            .hasMessageContaining("200");
    }
    
    @Test
    @DisplayName("Given no events, when withdrawing, then wallet not found exception")
    void givenNoEvents_whenWithdrawing_thenWalletNotFoundException() {
        // Given: No events (empty event store)
        
        // When & Then
        WithdrawCommand command = WithdrawCommand.of("withdrawal1", "wallet1", 100, "Shopping");
        assertThatThrownBy(() -> when(handler, command))
            .isInstanceOf(WalletNotFoundException.class)
            .hasMessageContaining("wallet1");
    }
    
    @Test
    @DisplayName("Given wallet with previous transactions, when withdrawing, then balance calculates correctly")
    void givenWalletWithPreviousTransactions_whenWithdrawing_thenBalanceCalculatesCorrectly() {
        // Given
        given().event(WALLET_OPENED, builder -> builder
            .data(WalletOpened.of("wallet1", "Alice", 1000))
            .tag(WALLET_ID, "wallet1")
        );
        given().event(DEPOSIT_MADE, builder -> builder
            .data(DepositMade.of("deposit1", "wallet1", 500, 1500, "Bonus"))
            .tag(WALLET_ID, "wallet1")
        );
        given().event(WITHDRAWAL_MADE, builder -> builder
            .data(WithdrawalMade.of("withdrawal1", "wallet1", 200, 1300, "Previous withdrawal"))
            .tag(WALLET_ID, "wallet1")
        );
        
        // When
        WithdrawCommand command = WithdrawCommand.of("withdrawal2", "wallet1", 400, "New withdrawal");
        List<Object> events = when(handler, command);
        
        // Then
        then(events, WithdrawalMade.class, withdrawal -> {
            assertThat(withdrawal.amount()).isEqualTo(400);
            assertThat(withdrawal.newBalance()).isEqualTo(900); // 1000 + 500 - 200 - 400
        });
    }
    
    @Test
    @DisplayName("Given wallet, when withdrawing, then withdrawal has correct period tags")
    void givenWallet_whenWithdrawing_thenWithdrawalHasCorrectPeriodTags() {
        // Given
        given().event(WALLET_OPENED, builder -> builder
            .data(WalletOpened.of("wallet1", "Alice", 1000))
            .tag(WALLET_ID, "wallet1")
        );
        
        // When - get events with tags
        WithdrawCommand command = WithdrawCommand.of("withdrawal1", "wallet1", 300, "Shopping");
        List<EventWithTags<Object>> events = whenWithTags(handler, command);
        
        // Then - verify business logic AND period tags
        then(events, WithdrawalMade.class, (withdrawal, tags) -> {
            // Business logic
            assertThat(withdrawal.walletId()).isEqualTo("wallet1");
            assertThat(withdrawal.amount()).isEqualTo(300);
            assertThat(withdrawal.newBalance()).isEqualTo(700);
            
            // Period tags
            assertThat(tags).containsEntry("wallet_id", "wallet1");
            assertThat(tags).containsEntry("withdrawal_id", "withdrawal1");
            assertThat(tags).containsEntry("year", "2025");
            assertThat(tags).containsEntry("month", "1");
        });
    }
}

