package com.crablet.command.handlers.wallet.unit;

import com.crablet.eventstore.internal.ClockProviderImpl;
import com.crablet.test.commands.AbstractInMemoryHandlerTest;
import com.crablet.examples.wallet.commands.CloseWalletCommand;
import com.crablet.examples.wallet.commands.CloseWalletCommandHandler;
import com.crablet.examples.wallet.events.WalletClosed;
import com.crablet.examples.wallet.events.WalletOpened;
import com.crablet.examples.wallet.exceptions.WalletNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static com.crablet.eventstore.EventType.type;
import static com.crablet.examples.wallet.WalletTags.WALLET_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CloseWalletCommandHandler Unit Tests")
class CloseWalletCommandHandlerUnitTest extends AbstractInMemoryHandlerTest {

    private static final Instant FIXED_NOW = LocalDateTime.of(2026, 1, 15, 10, 0, 0)
            .atZone(ZoneId.systemDefault()).toInstant();

    private CloseWalletCommandHandler handler;

    @BeforeEach
    @Override
    protected void setUp() {
        super.setUp();
        ClockProviderImpl clock = new ClockProviderImpl();
        clock.setClock(Clock.fixed(FIXED_NOW, ZoneId.systemDefault()));
        handler = new CloseWalletCommandHandler(clock);
    }

    @Test
    @DisplayName("Given wallet exists, when closing, then WalletClosed event is returned")
    void givenWalletExists_whenClosing_thenWalletClosedReturned() {
        given().event(type(WalletOpened.class), builder -> builder
            .data(WalletOpened.of("wallet1", "Alice", 1000))
            .tag(WALLET_ID, "wallet1")
        );

        List<Object> events = when(handler, CloseWalletCommand.of("wallet1"));

        then(events, WalletClosed.class, closed -> {
            assertThat(closed.walletId()).isEqualTo("wallet1");
            assertThat(closed.closedAt()).isEqualTo(FIXED_NOW);
        });
    }

    @Test
    @DisplayName("Given no events, when closing, then WalletNotFoundException is thrown")
    void givenNoEvents_whenClosing_thenWalletNotFoundException() {
        assertThatThrownBy(() -> when(handler, CloseWalletCommand.of("wallet1")))
            .isInstanceOf(WalletNotFoundException.class)
            .hasMessageContaining("wallet1");
    }

    @Test
    @DisplayName("Given wallet already closed, when closing again, then WalletNotFoundException is thrown")
    void givenWalletAlreadyClosed_whenClosingAgain_thenWalletNotFoundException() {
        given().event(type(WalletOpened.class), builder -> builder
            .data(WalletOpened.of("wallet1", "Alice", 1000))
            .tag(WALLET_ID, "wallet1")
        );
        given().event(type(WalletClosed.class), builder -> builder
            .data(new WalletClosed("wallet1", FIXED_NOW))
            .tag(WALLET_ID, "wallet1")
        );

        assertThatThrownBy(() -> when(handler, CloseWalletCommand.of("wallet1")))
            .isInstanceOf(WalletNotFoundException.class)
            .hasMessageContaining("wallet1");
    }

    @Test
    @DisplayName("Given wallet exists, when closing, then event has correct wallet_id tag")
    void givenWalletExists_whenClosing_thenEventHasCorrectTag() {
        given().event(type(WalletOpened.class), builder -> builder
            .data(WalletOpened.of("wallet1", "Alice", 500))
            .tag(WALLET_ID, "wallet1")
        );

        List<EventWithTags<Object>> events = whenWithTags(handler, CloseWalletCommand.of("wallet1"));

        then(events, WalletClosed.class, (closed, tags) -> {
            assertThat(closed.walletId()).isEqualTo("wallet1");
            assertThat(tags).containsEntry("wallet_id", "wallet1");
        });
    }
}
