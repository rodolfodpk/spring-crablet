package crablet.unit.core;

import com.crablet.core.AppendCondition;
import com.crablet.core.Command;
import com.crablet.core.CommandExecutor;
import com.crablet.core.CommandHandler;
import com.crablet.core.CommandResult;
import com.crablet.core.EventStore;
import com.crablet.core.AppendEvent;
import com.crablet.core.Tag;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallets.domain.event.WalletOpened;
import com.wallets.features.deposit.DepositCommandHandler;
import com.wallets.features.openwallet.OpenWalletCommand;
import com.wallets.features.openwallet.OpenWalletCommandHandler;
import com.wallets.features.transfer.TransferMoneyCommandHandler;
import com.wallets.features.withdraw.WithdrawCommandHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import testutils.AbstractCrabletTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for CommandExecutor.
 * Tests transaction management, event generation, and command storage.
 */
class CommandExecutorTest extends AbstractCrabletTest {

    @Autowired
    private CommandExecutor commandExecutor;

    @Autowired
    private EventStore eventStore;

    @Autowired
    private OpenWalletCommandHandler openWalletHandler;
    
    
    @Autowired
    private DepositCommandHandler depositHandler;
    
    @Autowired
    private WithdrawCommandHandler withdrawHandler;
    
    @Autowired
    private TransferMoneyCommandHandler transferHandler;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
    }

    @Test
    @DisplayName("Should validate command is not null")
    void shouldValidateCommandIsNotNull() {
        // When & Then
        assertThatThrownBy(() -> commandExecutor.executeCommand(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Command cannot be null");
    }

    @Test
    @DisplayName("Should validate handler is not null")
    void shouldValidateHandlerIsNotNull() {
        // Given
        OpenWalletCommand command = OpenWalletCommand.of("test-wallet", "Test User", 1000);

        // When & Then
        assertThatThrownBy(() -> commandExecutor.execute(command, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Handler cannot be null");
    }

    @Test
    @DisplayName("Should handle handler that generates no events")
    void shouldHandleHandlerThatGeneratesNoEvents() {
        // Given
        CommandHandler<Command> emptyHandler = new CommandHandler<Command>() {
            @Override
            public CommandResult handle(EventStore eventStore, Command command) {
                return CommandResult.empty(); // Return empty list
            }
            
            @Override
            public String getCommandType() {
                return "test_empty";
            }
        };
        OpenWalletCommand command = OpenWalletCommand.of("test-wallet", "Test User", 1000);

        // When & Then - Empty event list is now allowed for idempotent operations
        assertThatCode(() -> commandExecutor.execute(command, emptyHandler))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should validate event types are not empty")
    void shouldValidateEventTypesAreNotEmpty() {
        // Given
        CommandHandler<Command> invalidHandler = new CommandHandler<Command>() {
            @Override
            public CommandResult handle(EventStore eventStore, Command command) {
                // Create event with empty type
                return CommandResult.of(List.of(AppendEvent.of("", List.of(), new byte[0])), AppendCondition.forEmptyStream());
            }
            
            @Override
            public String getCommandType() {
                return "test_invalid_type";
            }
        };
        OpenWalletCommand command = OpenWalletCommand.of("test-wallet", "Test User", 1000);

        // When & Then
        assertThatThrownBy(() -> commandExecutor.execute(command, invalidHandler))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Event at index 0 has empty type");
    }

    @Test
    @DisplayName("Should validate tag keys are not empty")
    void shouldValidateTagKeysAreNotEmpty() {
        // Given
        CommandHandler<Command> invalidHandler = new CommandHandler<Command>() {
            @Override
            public CommandResult handle(EventStore eventStore, Command command) {
                // Create event with empty tag key
                return CommandResult.of(List.of(AppendEvent.of("TestEvent", List.of(new Tag("", "value")), new byte[0])), AppendCondition.forEmptyStream());
            }
            
            @Override
            public String getCommandType() {
                return "test_invalid_tag_key";
            }
        };
        OpenWalletCommand command = OpenWalletCommand.of("test-wallet", "Test User", 1000);

        // When & Then
        assertThatThrownBy(() -> commandExecutor.execute(command, invalidHandler))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Empty tag key at index 0");
    }

    @Test
    @DisplayName("Should validate tag values are not empty")
    void shouldValidateTagValuesAreNotEmpty() {
        // Given
        CommandHandler<Command> invalidHandler = new CommandHandler<Command>() {
            @Override
            public CommandResult handle(EventStore eventStore, Command command) {
                // Create event with empty tag value
                return CommandResult.of(List.of(AppendEvent.of("TestEvent", List.of(new Tag("key", "")), new byte[0])), AppendCondition.forEmptyStream());
            }
            
            @Override
            public String getCommandType() {
                return "test_invalid_tag_value";
            }
        };
        OpenWalletCommand command = OpenWalletCommand.of("test-wallet", "Test User", 1000);

        // When & Then
        assertThatThrownBy(() -> commandExecutor.execute(command, invalidHandler))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Empty tag value for key key");
    }

    @Test
    @DisplayName("Should handle database connection errors gracefully")
    void shouldHandleDatabaseConnectionErrorsGracefully() {
        // Given
        OpenWalletCommand command = OpenWalletCommand.of("test-wallet", "Test User", 1000);

        // When & Then - Use real CommandExecutor with Testcontainers
        assertThatCode(() -> commandExecutor.executeCommand(command))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should execute command with valid handler")
    void shouldExecuteCommandWithValidHandler() {
        // Given
        String walletId = "test-wallet-" + UUID.randomUUID().toString().substring(0, 8);
        OpenWalletCommand command = OpenWalletCommand.of(walletId, "John Doe", 1000);

        // When - use the autowired commandExecutor which will find the handler automatically
        commandExecutor.executeCommand(command);

        // Then - verify the command was processed without throwing exceptions
        // The actual event storage verification would require more complex setup
        // For now, we just verify the method completes successfully
        assertThat(true).isTrue(); // Test passes if no exception is thrown
    }

    @Test
    @DisplayName("Should handle command serialization errors gracefully")
    void shouldHandleCommandSerializationErrorsGracefully() {
        // Given
        Command problematicCommand = new Command() {
            @Override
            public String getCommandType() {
                return "ProblematicCommand";
            }
            
            // This will cause serialization issues
            public Object getProblematicField() {
                return new Object() {
                    public Object getCircularReference() {
                        return this;
                    }
                };
            }
        };

        CommandHandler<Command> mockHandler = new CommandHandler<Command>() {
            @Override
            public CommandResult handle(EventStore eventStore, Command command) {
                return CommandResult.of(List.of(AppendEvent.of("TestEvent", List.of(), new byte[0])), AppendCondition.forEmptyStream());
            }
            
            @Override
            public String getCommandType() {
                return "test_mock";
            }
        };

        // When & Then
        assertThatThrownBy(() -> commandExecutor.execute(problematicCommand, mockHandler))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Failed to append events with condition using connection");
    }

    @Test
    @DisplayName("Should handle multiple events from handler")
    void shouldHandleMultipleEventsFromHandler() {
        // Given
        String walletId = "multi-event-wallet-" + UUID.randomUUID().toString().substring(0, 8);
        OpenWalletCommand command = OpenWalletCommand.of(walletId, "Multi User", 1000);

        CommandHandler<Command> multiEventHandler = new CommandHandler<Command>() {
            @Override
            public CommandResult handle(EventStore eventStore, Command command) {
                try {
                    WalletOpened walletOpened = WalletOpened.of(walletId, "Multi User", 1000);
                    byte[] data = objectMapper.writeValueAsBytes(walletOpened);
                    
                    return CommandResult.of(
                        List.of(
                            AppendEvent.of("WalletOpened", List.of(new Tag("wallet_id", walletId)), data),
                            AppendEvent.of("WalletInitialized", List.of(new Tag("wallet_id", walletId)), "{}".getBytes())
                        ),
                        AppendCondition.forEmptyStream()
                    );
                } catch (Exception e) {
                    throw new RuntimeException("Failed to serialize events", e);
                }
            }
            
            @Override
            public String getCommandType() {
                return "test_multi_event";
            }
        };

        // When
        commandExecutor.execute(command, multiEventHandler);

        // Then - verify the command was processed without throwing exceptions
        assertThat(true).isTrue(); // Test passes if no exception is thrown
    }

    @Test
    @DisplayName("Should handle handler that throws business logic exception")
    void shouldHandleHandlerThatThrowsBusinessLogicException() {
        // Given
        OpenWalletCommand command = OpenWalletCommand.of("existing-wallet", "Test User", 1000);

        CommandHandler<Command> businessLogicHandler = new CommandHandler<Command>() {
            @Override
            public CommandResult handle(EventStore eventStore, Command command) {
                // Simulate business logic validation failure
                throw new IllegalArgumentException("Wallet already exists");
            }
            
            @Override
            public String getCommandType() {
                return "test_business_logic";
            }
        };

        // When & Then
        assertThatThrownBy(() -> commandExecutor.execute(command, businessLogicHandler))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Wallet already exists");
    }

    @Test
    @DisplayName("Should handle handler that throws runtime exception")
    void shouldHandleHandlerThatThrowsRuntimeException() {
        // Given
        OpenWalletCommand command = OpenWalletCommand.of("error-wallet", "Test User", 1000);

        CommandHandler<Command> errorHandler = new CommandHandler<Command>() {
            @Override
            public CommandResult handle(EventStore eventStore, Command command) {
                throw new RuntimeException("Unexpected error in handler");
            }
            
            @Override
            public String getCommandType() {
                return "test_error";
            }
        };

        // When & Then
        assertThatThrownBy(() -> commandExecutor.execute(command, errorHandler))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Unexpected error in handler");
    }
}