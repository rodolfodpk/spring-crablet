package unit.features.withdraw;

import com.crablet.core.CommandResult;
import com.crablet.core.Event;
import com.crablet.core.EventStore;
import com.crablet.core.InputEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallets.domain.event.WalletOpened;
import com.wallets.domain.event.WithdrawalMade;
import com.wallets.domain.exception.InsufficientFundsException;
import com.wallets.domain.exception.WalletNotFoundException;
import com.wallets.domain.projections.WalletBalanceProjector;
import com.wallets.features.withdraw.WithdrawCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import testutils.AbstractCrabletTest;
import testutils.WalletTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test WithdrawCommandHandler with minimal state projection.
 * 
 * DCB Principle: Tests verify that handler projects only balance + existence.
 */
class WithdrawCommandHandlerTest extends AbstractCrabletTest {
    
    private com.wallets.features.withdraw.WithdrawCommandHandler handler;
    @Autowired
    private ObjectMapper objectMapper;
    private WalletBalanceProjector balanceProjector;
    
    @Autowired
    private EventStore eventStore;
    
    @BeforeEach
    void setUp() {
        balanceProjector = new WalletBalanceProjector(objectMapper);
        handler = new com.wallets.features.withdraw.WithdrawCommandHandler(objectMapper, balanceProjector);
    }
    
    @Test
    @DisplayName("Should successfully handle withdrawal command")
    void testHandleWithdraw_Success() {
        // Arrange - create wallet first
        WalletOpened walletOpened = WalletOpened.of("wallet1", "Alice", 1000);
        Event walletEvent = WalletTestUtils.createEvent(walletOpened);
        InputEvent walletInputEvent = InputEvent.of(walletEvent.type(), walletEvent.tags(), walletEvent.data());
        eventStore.append(List.of(walletInputEvent));
        
        WithdrawCommand cmd = WithdrawCommand.of("withdrawal1", "wallet1", 300, "Shopping");
        
        // Act
        CommandResult result = handler.handle(eventStore, cmd);
        
        // Assert
        assertThat(result.events()).hasSize(1);
        assertThat(result.events().get(0))
            .satisfies(event -> {
                assertThat(event.type()).isEqualTo("WithdrawalMade");
                assertThat(event.tags()).hasSize(2);
                assertThat(event.tags().get(0))
                    .satisfies(tag -> {
                        assertThat(tag.key()).isEqualTo("wallet_id");
                        assertThat(tag.value()).isEqualTo("wallet1");
                    });
                assertThat(event.tags().get(1))
                    .satisfies(tag -> {
                        assertThat(tag.key()).isEqualTo("withdrawal_id");
                        assertThat(tag.value()).isEqualTo("withdrawal1");
                    });
            });
        
        WithdrawalMade withdrawal = WalletTestUtils.deserializeEventData(result.events().get(0).data(), WithdrawalMade.class);
        assertThat(withdrawal)
            .satisfies(w -> {
                assertThat(w.withdrawalId()).isEqualTo("withdrawal1");
                assertThat(w.walletId()).isEqualTo("wallet1");
                assertThat(w.amount()).isEqualTo(300);
                assertThat(w.newBalance()).isEqualTo(700); // 1000 - 300
                assertThat(w.description()).isEqualTo("Shopping");
            });
    }
    
    @Test
    @DisplayName("Should throw exception when wallet does not exist")
    void testHandleWithdraw_WalletNotFound() {
        // Arrange
        WithdrawCommand cmd = WithdrawCommand.of("withdrawal1", "nonexistent", 100, "Test withdrawal");
        
        // Act & Assert
        assertThatThrownBy(() -> handler.handle(eventStore, cmd))
            .isInstanceOf(WalletNotFoundException.class)
            .hasMessageContaining("nonexistent");
    }
    
    @Test
    @DisplayName("Should throw exception for insufficient funds")
    void testHandleWithdraw_InsufficientFunds() {
        // Arrange - create wallet with low balance
        WalletOpened walletOpened = WalletOpened.of("wallet1", "Alice", 100);
        Event walletEvent = WalletTestUtils.createEvent(walletOpened);
        InputEvent walletInputEvent = InputEvent.of(walletEvent.type(), walletEvent.tags(), walletEvent.data());
        eventStore.append(List.of(walletInputEvent));
        
        WithdrawCommand cmd = WithdrawCommand.of("withdrawal1", "wallet1", 200, "Overdraft");
        
        // Act & Assert
        assertThatThrownBy(() -> handler.handle(eventStore, cmd))
            .isInstanceOf(InsufficientFundsException.class)
            .hasMessageContaining("wallet1")
            .hasMessageContaining("100")
            .hasMessageContaining("200");
    }
    
    @Test
    @DisplayName("Should throw exception for zero amount at command creation")
    void testHandleWithdraw_ZeroAmount() {
        // Act & Assert - YAVI validation prevents invalid command creation
        assertThatThrownBy(() -> WithdrawCommand.of("withdrawal1", "wallet1", 0, "Zero withdrawal"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("amount");
    }
    
    @Test
    @DisplayName("Should throw exception for negative amount at command creation")
    void testHandleWithdraw_NegativeAmount() {
        // Act & Assert - YAVI validation prevents invalid command creation
        assertThatThrownBy(() -> WithdrawCommand.of("withdrawal1", "wallet1", -100, "Negative withdrawal"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("amount");
    }
    
    @Test
    @DisplayName("Should handle withdrawal with exact balance")
    void testHandleWithdraw_ExactBalance() {
        // Arrange - create wallet with exact balance
        WalletOpened walletOpened = WalletOpened.of("wallet1", "Alice", 500);
        Event walletEvent = WalletTestUtils.createEvent(walletOpened);
        InputEvent walletInputEvent = InputEvent.of(walletEvent.type(), walletEvent.tags(), walletEvent.data());
        eventStore.append(List.of(walletInputEvent));
        
        WithdrawCommand cmd = WithdrawCommand.of("withdrawal1", "wallet1", 500, "Full withdrawal");
        
        // Act
        CommandResult result = handler.handle(eventStore, cmd);
        
        // Assert
        assertThat(result.events()).hasSize(1);
        WithdrawalMade withdrawal = WalletTestUtils.deserializeEventData(result.events().get(0).data(), WithdrawalMade.class);
        assertThat(withdrawal.newBalance()).isEqualTo(0); // 500 - 500
    }
    
    @Test
    @DisplayName("Should project minimal state - balance + existence")
    void testProjectMinimalState() {
        // Arrange - create wallet with multiple events
        WalletOpened walletOpened = WalletOpened.of("wallet1", "Alice", 1000);
        Event walletEvent = WalletTestUtils.createEvent(walletOpened);
        InputEvent walletInputEvent = InputEvent.of(walletEvent.type(), walletEvent.tags(), walletEvent.data());
        eventStore.append(List.of(walletInputEvent));
        
        // Act - withdrawal should only project balance + existence, not full WalletState
        WithdrawCommand cmd = WithdrawCommand.of("withdrawal1", "wallet1", 200, "Test withdrawal");
        CommandResult result = handler.handle(eventStore, cmd);
        
        // Assert - verify correct new balance calculation
        WithdrawalMade withdrawal = WalletTestUtils.deserializeEventData(result.events().get(0).data(), WithdrawalMade.class);
        assertThat(withdrawal.newBalance()).isEqualTo(800); // 1000 - 200
    }
    
    @ParameterizedTest
    @CsvSource({
        "wallet1, Alice, 1000, Shopping",
        "wallet2, Bob, 500, Rent",
        "wallet3, Charlie, 2000, Vacation"
    })
    @DisplayName("Should handle various withdrawal scenarios")
    void testWithdrawalScenarios(String walletId, String owner, int initialBalance, String description) {
        // Arrange - create wallet
        WalletOpened walletOpened = WalletOpened.of(walletId, owner, initialBalance);
        Event walletEvent = WalletTestUtils.createEvent(walletOpened);
        InputEvent walletInputEvent = InputEvent.of(walletEvent.type(), walletEvent.tags(), walletEvent.data());
        eventStore.append(List.of(walletInputEvent));
        
        WithdrawCommand cmd = WithdrawCommand.of("withdrawal1", walletId, 100, description);
        
        // Act
        CommandResult result = handler.handle(eventStore, cmd);
        
        // Assert
        assertThat(result.events()).hasSize(1);
        WithdrawalMade withdrawal = WalletTestUtils.deserializeEventData(result.events().get(0).data(), WithdrawalMade.class);
        assertThat(withdrawal.newBalance()).isEqualTo(initialBalance - 100);
        assertThat(withdrawal.description()).isEqualTo(description);
    }
}
