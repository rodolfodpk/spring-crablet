package com.crablet.command;

import com.crablet.eventstore.EventStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Command public API contract")
class PublicApiContractTest {

    @Test
    @DisplayName("CommandExecutor execute overloads should return ExecutionResult")
    void commandExecutorExecuteOverloads_ShouldReturnExecutionResult() throws NoSuchMethodException {
        assertThat(executeMethod(Object.class).getReturnType()).isEqualTo(ExecutionResult.class);
        assertThat(executeMethod(Object.class, CommandHandler.class).getReturnType()).isEqualTo(ExecutionResult.class);
        assertThat(executeMethod(Object.class, CommandExecutionOptions.class).getReturnType()).isEqualTo(ExecutionResult.class);
    }

    @Test
    @DisplayName("CommutativeCommandHandler decide should return CommutativeDecision")
    void commutativeCommandHandlerDecide_ShouldReturnCommutativeDecision() throws NoSuchMethodException {
        Method decide = CommutativeCommandHandler.class.getMethod("decide", EventStore.class, Object.class);

        assertThat(decide.getReturnType()).isEqualTo(CommandDecision.CommutativeDecision.class);
    }

    private static Method executeMethod(Class<?>... parameterTypes) throws NoSuchMethodException {
        return CommandExecutor.class.getMethod("execute", parameterTypes);
    }
}
