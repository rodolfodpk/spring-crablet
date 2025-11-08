package com.crablet.command.integration;

import com.crablet.command.CommandHandler;
import com.crablet.command.CommandResult;
import com.crablet.eventstore.store.EventStore;

import java.util.function.Function;

/**
 * Real CommandHandler implementation for integration tests.
 * Allows configuring handler behavior per test via ThreadLocal.
 */
public class TestCommandHandler implements CommandHandler<TestCommand> {
    
    private static final ThreadLocal<Function<TestCommand, CommandResult>> handlerLogic = new ThreadLocal<>();
    
    public static void setHandlerLogic(Function<TestCommand, CommandResult> logic) {
        handlerLogic.set(logic);
    }
    
    public static void clearHandlerLogic() {
        handlerLogic.remove();
    }
    
    @Override
    public CommandResult handle(EventStore eventStore, TestCommand command) {
        Function<TestCommand, CommandResult> logic = handlerLogic.get();
        if (logic == null) {
            throw new IllegalStateException("TestCommandHandler logic not set. Call setHandlerLogic() in test setup.");
        }
        return logic.apply(command);
    }
}

