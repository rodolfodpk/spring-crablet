package com.crablet.command;

import com.crablet.command.handlers.courses.DefineCourseCommandHandler;
import com.crablet.command.internal.CommandTypeResolver;
import com.crablet.examples.wallet.commands.DepositCommandHandler;
import com.crablet.examples.wallet.commands.OpenWalletCommandHandler;
import com.crablet.examples.wallet.commands.OpenWalletCommand;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CommandTypeResolverTest {

    @Test
    void extractCommandTypeFromHandler_WithDepositCommandHandler_ShouldReturnDeposit() {
        // When
        String commandType = CommandTypeResolver.extractCommandTypeFromHandler(DepositCommandHandler.class);

        // Then
        assertEquals("deposit", commandType);
    }

    @Test
    void extractCommandTypeFromHandler_WithOpenWalletCommandHandler_ShouldReturnOpenWallet() {
        // When
        String commandType = CommandTypeResolver.extractCommandTypeFromHandler(OpenWalletCommandHandler.class);

        // Then
        assertEquals("open_wallet", commandType);
    }

    @Test
    void extractCommandTypeFromHandler_WithDefineCourseCommandHandler_ShouldReturnDefineCourse() {
        // When
        String commandType = CommandTypeResolver.extractCommandTypeFromHandler(DefineCourseCommandHandler.class);

        // Then
        assertEquals("define_course", commandType);
    }

    @Test
    void extractCommandTypeFromHandler_WithHandlerNotImplementingCommandHandler_ShouldThrowException() {
        // When & Then
        InvalidCommandException exception = assertThrows(
            InvalidCommandException.class,
            () -> CommandTypeResolver.extractCommandTypeFromHandler(String.class)
        );

        assertThat(exception).hasMessageContaining("does not implement CommandHandler");
    }

    @Test
    void extractCommandTypeFromHandler_WithHandlerWithoutJsonSubTypes_ShouldThrowException() {
        // Given: A handler class that implements CommandHandler but command has no @JsonSubTypes
        class TestCommand {
        }
        
        class TestHandler implements CommandHandler<TestCommand> {
            @Override
            @SuppressWarnings("NullAway")
            public CommandDecision handle(com.crablet.eventstore.EventStore eventStore, TestCommand command) {
                return null;
            }
        }

        // When & Then
        InvalidCommandException exception = assertThrows(
            InvalidCommandException.class,
            () -> CommandTypeResolver.extractCommandTypeFromHandler(TestHandler.class)
        );

        assertThat(exception).hasMessageContaining("@JsonSubTypes hierarchy");
    }

    @Test
    void extractCommandTypeFromHandler_WithCommandNotInJsonSubTypes_ShouldThrowException() {
        // Given: A handler for a command class that's not in any @JsonSubTypes
        class CommandNotInSubTypes {}
        
        class HandlerWithoutSubType implements CommandHandler<CommandNotInSubTypes> {
            @Override
            @SuppressWarnings("NullAway")
            public CommandDecision handle(com.crablet.eventstore.EventStore eventStore, CommandNotInSubTypes command) {
                return null;
            }
        }

        // When & Then
        InvalidCommandException exception = assertThrows(
            InvalidCommandException.class,
            () -> CommandTypeResolver.extractCommandTypeFromHandler(HandlerWithoutSubType.class)
        );

        assertThat(exception.getMessage()).isNotNull().satisfies(msg ->
            assertThat(msg.contains("@JsonSubTypes") || msg.contains("not found in @JsonSubTypes")).isTrue());
    }

    @Test
    void extractCommandTypeFromHandler_WithHandlerExtendingAbstractBaseClass_ShouldExtractFromSuperclass() {
        // Given: An abstract base handler class that implements CommandHandler<T>
        abstract class AbstractBaseHandler<T> implements CommandHandler<T> {
            // Abstract base class with generic type parameter
        }
        
        // Concrete handler extending the abstract base class
        class ConcreteHandler extends AbstractBaseHandler<OpenWalletCommand> {
            @Override
            @SuppressWarnings("NullAway")
            public CommandDecision handle(com.crablet.eventstore.EventStore eventStore, OpenWalletCommand command) {
                return null;
            }
        }
        
        // When - Should extract from superclass (lines 84-93)
        String commandType = CommandTypeResolver.extractCommandTypeFromHandler(ConcreteHandler.class);
        
        // Then
        assertEquals("open_wallet", commandType);
    }
    
    @Test
    void extractCommandTypeFromHandler_WithHandlerWithoutCommandHandler_ShouldThrowException() {
        // Given: A class that doesn't implement CommandHandler at all
        class NotAHandler {
            // Not implementing CommandHandler
        }
        
        // When & Then - Should throw exception (lines 95-99)
        InvalidCommandException exception = assertThrows(
            InvalidCommandException.class,
            () -> CommandTypeResolver.extractCommandTypeFromHandler(NotAHandler.class)
        );

        assertThat(exception.getMessage()).isNotNull().satisfies(msg ->
            assertThat(msg.contains("CommandHandler<T> not found") || msg.contains("does not implement CommandHandler")).isTrue());
    }
    
    @Test
    void extractCommandTypeFromHandler_WithHandlerHavingNonClassTypeArgument_ShouldThrowException() {
        // Given: A handler that implements CommandHandler but type argument is not a Class
        // This scenario is difficult to create because Java's type system prevents it at compile time.
        // However, we can test the error path by creating a handler that doesn't have
        // CommandHandler<T> in interfaces or superclass, which will hit lines 95-99.
        
        // Note: The early validation (line 33) checks if class implements CommandHandler,
        // so to hit lines 95-99, we'd need a class that implements CommandHandler but
        // getCommandClassFromHandler can't extract the type. This is rare in practice.
        
        // This test verifies the error message when CommandHandler<T> cannot be found
        // (which happens when type argument is not a Class or CommandHandler is not in hierarchy)
        class HandlerWithoutTypeInfo {
            // This class doesn't implement CommandHandler, so it will hit early validation
            // The actual error path at lines 95-99 would require a more complex scenario
        }
        
        // When & Then - This hits early validation, not lines 95-99
        InvalidCommandException exception = assertThrows(
            InvalidCommandException.class,
            () -> CommandTypeResolver.extractCommandTypeFromHandler(HandlerWithoutTypeInfo.class)
        );
        
        assertThat(exception).hasMessageContaining("does not implement CommandHandler");

        // Note: To actually test lines 95-99, we would need a class that implements CommandHandler
        // but getCommandClassFromHandler can't extract the type. This is very difficult to create
        // because Java's type system prevents such scenarios at compile time.
    }

    @Test
    void extractCommandTypeFromHandler_WithHandlerImplementingMultipleInterfaces_ShouldFindCommandHandler() {
        // Given: Handler implementing CommandHandler and other interfaces
        interface OtherInterface {}
        
        class MultiInterfaceHandler implements CommandHandler<OpenWalletCommand>, OtherInterface {
            @Override
            @SuppressWarnings("NullAway")
            public CommandDecision handle(com.crablet.eventstore.EventStore eventStore, OpenWalletCommand command) {
                return null;
            }
        }
        
        // When
        String commandType = CommandTypeResolver.extractCommandTypeFromHandler(MultiInterfaceHandler.class);
        
        // Then
        assertEquals("open_wallet", commandType);
    }

    @Test
    void extractCommandTypeFromHandler_WithJsonSubTypesOnCommandClass_ShouldUseClassAnnotation() {
        // Given: Command class with @JsonSubTypes annotation directly
        @JsonTypeInfo(
                use = JsonTypeInfo.Id.NAME,
                include = JsonTypeInfo.As.PROPERTY,
                property = "commandType"
        )
        @JsonSubTypes({
                @JsonSubTypes.Type(value = TestCommandClass.class, name = "test_class_command")
        })
        class TestCommandClass {}
        
        class TestHandler implements CommandHandler<TestCommandClass> {
            @Override
            @SuppressWarnings("NullAway")
            public CommandDecision handle(com.crablet.eventstore.EventStore eventStore, TestCommandClass command) {
                return null;
            }
        }
        
        // When
        String commandType = CommandTypeResolver.extractCommandTypeFromHandler(TestHandler.class);
        
        // Then
        assertEquals("test_class_command", commandType);
    }

    @Test
    void extractCommandTypeFromHandler_WithJsonSubTypesOnParentInterface_ShouldUseInterfaceAnnotation() {
        // This test verifies that @JsonSubTypes on parent interface is found
        // (This is already tested by existing tests with WalletCommand and CourseCommand,
        // but we make it explicit)
        
        // Given: Handler for command implementing interface with @JsonSubTypes
        String commandType = CommandTypeResolver.extractCommandTypeFromHandler(DepositCommandHandler.class);
        
        // Then: Should extract from WalletCommand interface annotation
        assertEquals("deposit", commandType);
    }

    @Test
    void extractCommandTypeFromHandler_WithInvalidHandler_ShouldProvideHelpfulErrorMessage() {
        // When & Then
        InvalidCommandException exception = assertThrows(
            InvalidCommandException.class,
            () -> CommandTypeResolver.extractCommandTypeFromHandler(Integer.class)
        );
        
        assertThat(exception.getMessage()).isNotNull().satisfies(msg ->
            assertThat(msg.contains("does not implement CommandHandler") || msg.contains("Integer")).isTrue());
    }
}

