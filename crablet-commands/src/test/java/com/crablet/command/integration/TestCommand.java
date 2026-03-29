package com.crablet.command.integration;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Test command for integration tests.
 * Simple record with commandType field for Jackson serialization.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "commandType"
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = TestCommand.class, name = "test_command")
})
public record TestCommand(
    @JsonProperty("commandType") String commandType,
    @JsonProperty("entityId") String entityId
) {
}

