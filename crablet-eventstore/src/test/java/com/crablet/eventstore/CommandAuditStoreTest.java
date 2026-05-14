package com.crablet.eventstore;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CommandAuditStore default method contract")
class CommandAuditStoreTest {

    @Test
    @DisplayName("default reserveCommand throws UnsupportedOperationException")
    void defaultReserveCommandThrowsUnsupportedOperation() {
        CommandAuditStore stub = new CommandAuditStore() {
            @Override
            public void storeCommand(String commandJson, String commandType, String transactionId) {}
        };

        assertThatThrownBy(() -> stub.reserveCommand("json", "type", "key", Instant.now()))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
