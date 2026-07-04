package com.crablet.command.handlers.wallet;

import com.crablet.command.CommandDecision;
import com.crablet.eventstore.AppendEvent;
import com.crablet.eventstore.ClockProvider;
import com.crablet.examples.wallet.commands.CloseWalletCommand;
import com.crablet.examples.wallet.commands.CloseWalletCommandHandler;
import com.crablet.examples.wallet.events.WalletClosed;
import com.crablet.examples.wallet.events.WalletOpened;
import com.crablet.examples.wallet.exceptions.WalletNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static com.crablet.eventstore.EventType.type;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CloseWalletCommandHandler Integration Tests")
@SpringBootTest(classes = com.crablet.command.integration.TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "spring.profiles.active=test")
class CloseWalletCommandHandlerTest extends com.crablet.test.AbstractPostgresEventStoreTest {

    private CloseWalletCommandHandler handler;

    @Autowired
    private ClockProvider clockProvider;

    @Autowired
    private WalletTestUtils walletTestUtils;

    @BeforeEach
    void setUp() {
        handler = new CloseWalletCommandHandler(clockProvider);
    }

    @Test
    @DisplayName("Should successfully close an existing wallet")
    void testHandleClose_Success() {
        eventStore.appendCommutative(List.of(
            AppendEvent.builder(type(WalletOpened.class))
                .data(WalletOpened.of("wallet1", "Alice", 1000))
                .tag("wallet_id", "wallet1")
                .build()
        ));

        CommandDecision result = handler.handle(eventStore, CloseWalletCommand.of("wallet1"));

        assertThat(result.events()).hasSize(1);
        AppendEvent closeEvent = result.events().get(0);
        assertThat(closeEvent.type()).isEqualTo("WalletClosed");
        assertThat(closeEvent.tags()).anyMatch(tag ->
            "wallet_id".equals(tag.key()) && "wallet1".equals(tag.value()));

        WalletClosed closed = walletTestUtils.deserializeEventData(closeEvent.eventData(), WalletClosed.class);
        assertThat(closed.walletId()).isEqualTo("wallet1");
        assertThat(closed.closedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should throw WalletNotFoundException when wallet does not exist")
    void testHandleClose_WalletNotFound() {
        assertThatThrownBy(() -> handler.handle(eventStore, CloseWalletCommand.of("nonexistent")))
            .isInstanceOf(WalletNotFoundException.class)
            .hasMessageContaining("nonexistent");
    }

    @Test
    @DisplayName("Should throw WalletNotFoundException when wallet is already closed")
    void testHandleClose_AlreadyClosed() {
        eventStore.appendCommutative(List.of(
            AppendEvent.builder(type(WalletOpened.class))
                .data(WalletOpened.of("wallet1", "Alice", 1000))
                .tag("wallet_id", "wallet1")
                .build()
        ));
        eventStore.appendCommutative(List.of(
            AppendEvent.builder(type(WalletClosed.class))
                .data(new WalletClosed("wallet1", clockProvider.now()))
                .tag("wallet_id", "wallet1")
                .build()
        ));

        assertThatThrownBy(() -> handler.handle(eventStore, CloseWalletCommand.of("wallet1")))
            .isInstanceOf(WalletNotFoundException.class)
            .hasMessageContaining("wallet1");
    }

    @Test
    @DisplayName("Closing one wallet should not affect another")
    void testHandleClose_IsolatedByWalletId() {
        eventStore.appendCommutative(List.of(
            AppendEvent.builder(type(WalletOpened.class))
                .data(WalletOpened.of("wallet1", "Alice", 1000))
                .tag("wallet_id", "wallet1")
                .build(),
            AppendEvent.builder(type(WalletOpened.class))
                .data(WalletOpened.of("wallet2", "Bob", 500))
                .tag("wallet_id", "wallet2")
                .build()
        ));

        // Close wallet1
        CommandDecision result = handler.handle(eventStore, CloseWalletCommand.of("wallet1"));
        assertThat(result.events()).hasSize(1);

        // wallet2 is still closeable (isExisting=true)
        CommandDecision result2 = handler.handle(eventStore, CloseWalletCommand.of("wallet2"));
        assertThat(result2.events()).hasSize(1);
        WalletClosed closed2 = walletTestUtils.deserializeEventData(result2.events().get(0).eventData(), WalletClosed.class);
        assertThat(closed2.walletId()).isEqualTo("wallet2");
    }
}
