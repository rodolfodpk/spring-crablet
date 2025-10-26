package wallets.integration.features.handlers.openwallet;

import com.crablet.eventstore.CommandResult;
import com.crablet.eventstore.ConcurrencyException;
import com.crablet.eventstore.EventStore;
import com.crablet.eventstore.EventTestHelper;
import com.crablet.eventstore.Query;
import com.crablet.eventstore.QueryItem;
import com.crablet.eventstore.StoredEvent;
import com.crablet.eventstore.Tag;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallets.domain.event.WalletOpened;
import com.wallets.features.openwallet.OpenWalletCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import wallets.integration.AbstractWalletIntegrationTest;
import com.com.com.com.com.wallets.testutils.WalletTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for OpenWalletCommandHandler.
 * <p>
 * DCB Principle: Tests verify that handler projects only wallet existence (boolean).
 */
@DisplayName("OpenWalletCommandHandler Integration Tests")
class OpenWalletCommandHandlerIT extends AbstractWalletIntegrationTest {

    private com.wallets.features.openwallet.OpenWalletCommandHandler handler;
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EventStore eventStore;

    @Autowired
    private EventTestHelper testHelper;

    @BeforeEach
    void setUp() {
        handler = new com.wallets.features.openwallet.OpenWalletCommandHandler();
    }

    @Test
    @DisplayName("Should successfully handle open wallet command")
    void testHandleOpenWallet_Success() {
        // Arrange
        OpenWalletCommand cmd = OpenWalletCommand.of("wallet1", "Alice", 1000);

        // Act
        CommandResult result = handler.handle(eventStore, cmd);

        // Assert
        assertThat(result.events()).hasSize(1);
        assertThat(result.events().get(0))
                .satisfies(event -> {
                    assertThat(event.type()).isEqualTo("WalletOpened");
                    assertThat(event.tags()).hasSize(1);
                    assertThat(event.tags().get(0))
                            .satisfies(tag -> {
                                assertThat(tag.key()).isEqualTo("wallet_id");
                                assertThat(tag.value()).isEqualTo("wallet1");
                            });
                });

        WalletOpened walletOpened = WalletTestUtils.deserializeEventData(result.events().get(0).eventData(), WalletOpened.class);
        assertThat(walletOpened)
                .satisfies(wallet -> {
                    assertThat(wallet.walletId()).isEqualTo("wallet1");
                    assertThat(wallet.owner()).isEqualTo("Alice");
                    assertThat(wallet.initialBalance()).isEqualTo(1000);
                });
    }

    @Test
    @DisplayName("Should throw exception when wallet already exists")
    void testHandleOpenWallet_WalletAlreadyExists() {
        // Arrange - create wallet first
        OpenWalletCommand firstCmd = OpenWalletCommand.of("wallet1", "Alice", 1000);
        CommandResult firstResult = handler.handle(eventStore, firstCmd);
        eventStore.appendIf(firstResult.events(), firstResult.appendCondition());

        // Act & Assert - try to create same wallet again
        OpenWalletCommand secondCmd = OpenWalletCommand.of("wallet1", "Bob", 2000);
        CommandResult secondResult = handler.handle(eventStore, secondCmd);

        // The handler doesn't throw - the executor does via appendIf
        assertThatThrownBy(() -> eventStore.appendIf(secondResult.events(), secondResult.appendCondition()))
                .isInstanceOf(ConcurrencyException.class);
    }

    @Test
    @DisplayName("Should project only wallet existence - minimal state")
    void testProjectWalletExistence_MinimalState() {
        // Arrange - create wallet
        OpenWalletCommand cmd = OpenWalletCommand.of("wallet1", "Alice", 1000);
        CommandResult result = handler.handle(eventStore, cmd);
        eventStore.appendIf(result.events(), result.appendCondition());

        // Act - try to create same wallet (should detect existence)
        OpenWalletCommand duplicateCmd = OpenWalletCommand.of("wallet1", "Bob", 2000);

        // Assert - should throw exception proving existence was detected
        CommandResult duplicateResult = handler.handle(eventStore, duplicateCmd);
        assertThatThrownBy(() -> eventStore.appendIf(duplicateResult.events(), duplicateResult.appendCondition()))
                .isInstanceOf(ConcurrencyException.class);

        // Verify only one event exists (no duplicate created)
        List<StoredEvent> allEvents = testHelper.query(Query.of(
                QueryItem.of(List.of("WalletOpened"), List.of(new Tag("wallet_id", "wallet1")))
        ), null);
        assertThat(allEvents).hasSize(1);
    }
}
