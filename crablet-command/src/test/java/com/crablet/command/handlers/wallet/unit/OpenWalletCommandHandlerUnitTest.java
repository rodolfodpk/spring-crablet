package com.crablet.command.handlers.wallet.unit;

import com.crablet.command.handlers.unit.AbstractHandlerUnitTest;
import com.crablet.command.handlers.wallet.OpenWalletCommandHandler;
import com.crablet.examples.wallet.event.WalletOpened;
import com.crablet.examples.wallet.features.openwallet.OpenWalletCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OpenWalletCommandHandler}.
 * <p>
 * These tests focus on business logic validation and happy paths.
 * DCB concurrency (idempotency) is tested in integration tests.
 */
@DisplayName("OpenWalletCommandHandler Unit Tests")
class OpenWalletCommandHandlerUnitTest extends AbstractHandlerUnitTest {
    
    private OpenWalletCommandHandler handler;
    
    @BeforeEach
    @Override
    protected void setUp() {
        super.setUp();
        handler = new OpenWalletCommandHandler();
    }
    
    @Test
    @DisplayName("Given no events, when opening wallet, then wallet opened event created")
    void givenNoEvents_whenOpeningWallet_thenWalletOpenedEventCreated() {
        // Given: No events (empty event store)
        
        // When
        OpenWalletCommand command = OpenWalletCommand.of("wallet1", "Alice", 1000);
        List<Object> events = when(handler, command);
        
        // Then
        then(events, WalletOpened.class, wallet -> {
            assertThat(wallet.walletId()).isEqualTo("wallet1");
            assertThat(wallet.owner()).isEqualTo("Alice");
            assertThat(wallet.initialBalance()).isEqualTo(1000);
        });
    }
    
    @Test
    @DisplayName("Given no events, when opening wallet with zero balance, then wallet opened event created")
    void givenNoEvents_whenOpeningWalletWithZeroBalance_thenWalletOpenedEventCreated() {
        // Given: No events (empty event store)
        
        // When
        OpenWalletCommand command = OpenWalletCommand.of("wallet1", "Bob", 0);
        List<Object> events = when(handler, command);
        
        // Then
        then(events, WalletOpened.class, wallet -> {
            assertThat(wallet.walletId()).isEqualTo("wallet1");
            assertThat(wallet.owner()).isEqualTo("Bob");
            assertThat(wallet.initialBalance()).isEqualTo(0);
        });
    }
    
    @Test
    @DisplayName("Given no events, when opening wallet, then event has correct tags")
    void givenNoEvents_whenOpeningWallet_thenEventHasCorrectTags() {
        // Given: No events (empty event store)
        
        // When - get events with tags
        OpenWalletCommand command = OpenWalletCommand.of("wallet1", "Alice", 1000);
        List<EventWithTags<Object>> events = whenWithTags(handler, command);
        
        // Then - verify event data AND tags
        then(events, WalletOpened.class, (wallet, tags) -> {
            // Event data
            assertThat(wallet.walletId()).isEqualTo("wallet1");
            assertThat(wallet.owner()).isEqualTo("Alice");
            assertThat(wallet.initialBalance()).isEqualTo(1000);
            
            // Tags
            assertThat(tags).containsEntry("wallet_id", "wallet1");
        });
    }
}

