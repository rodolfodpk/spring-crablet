package com.crablet.command.api.internal;

import com.crablet.command.CommandExecutor;
import com.crablet.command.ExecutionResult;
import com.crablet.command.api.CommandApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Generic REST adapter for command execution.
 */
class CommandApiController {

    private final CommandExecutor commandExecutor;
    private final ExposedCommandTypeRegistry exposedCommands;
    private final ObjectMapper objectMapper;

    CommandApiController(
            CommandExecutor commandExecutor,
            ExposedCommandTypeRegistry exposedCommands,
            ObjectMapper objectMapper) {
        this.commandExecutor = commandExecutor;
        this.exposedCommands = exposedCommands;
        this.objectMapper = objectMapper;
    }

    ResponseEntity<CommandApiResponse> execute(JsonNode body) {
        if (!(body instanceof ObjectNode objectNode)) {
            throw new CommandApiBadRequestException("Command payload must be a JSON object");
        }

        JsonNode commandTypeNode = objectNode.get("commandType");
        if (commandTypeNode == null || !commandTypeNode.isTextual() || commandTypeNode.asText().isBlank()) {
            throw new CommandApiBadRequestException("Command payload must contain a non-empty commandType");
        }

        String commandType = commandTypeNode.asText();
        Class<?> commandClass = exposedCommands.resolve(commandType);
        if (commandClass == null) {
            if (exposedCommands.isKnown(commandType)) {
                throw new CommandNotExposedException(commandType);
            }
            throw new CommandApiBadRequestException("Unknown commandType: " + commandType);
        }

        Object command;
        try {
            command = objectMapper.treeToValue(objectNode, commandClass);
        } catch (JacksonException | IllegalArgumentException e) {
            throw new CommandApiBadRequestException("Invalid payload for commandType: " + commandType);
        }

        ExecutionResult result = commandExecutor.execute(command);
        if (result.wasCreated()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(CommandApiResponse.created());
        }
        return ResponseEntity.ok(CommandApiResponse.idempotent(result.reason()));
    }
}
