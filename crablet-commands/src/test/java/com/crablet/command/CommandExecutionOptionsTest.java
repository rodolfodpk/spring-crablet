package com.crablet.command;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class CommandExecutionOptionsTest {

    @Test
    void defaultsHaveNoCorrelationOrCommandId() {
        CommandExecutionOptions options = CommandExecutionOptions.defaults();

        assertThat(options.correlationId()).isNull();
        assertThat(options.commandId()).isNull();
    }

    @Test
    void builderStoresCorrelationAndCommandId() {
        UUID correlationId = UUID.randomUUID();
        UUID commandId = UUID.randomUUID();

        CommandExecutionOptions options = CommandExecutionOptions.builder()
                .correlationId(correlationId)
                .commandId(commandId)
                .build();

        assertThat(options.correlationId()).isEqualTo(correlationId);
        assertThat(options.commandId()).isEqualTo(commandId);
    }

    @Test
    @SuppressWarnings("NullAway") // intentionally passing null to verify builder validation
    void builderRejectsNullValues() {
        assertThatNullPointerException()
                .isThrownBy(() -> CommandExecutionOptions.builder().correlationId(null))
                .withMessage("correlationId must not be null");
        assertThatNullPointerException()
                .isThrownBy(() -> CommandExecutionOptions.builder().commandId(null))
                .withMessage("commandId must not be null");
    }
}
