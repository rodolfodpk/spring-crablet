package com.crablet.command.web.internal;

import com.crablet.command.CommandExecutor;
import com.crablet.command.ExecutionResult;
import com.crablet.command.web.CommandApiExposedCommandsResponse;
import com.crablet.command.web.CommandApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Generic REST adapter that routes JSON command payloads to the {@link CommandExecutor}.
 * <p>
 * The endpoint path is configurable via {@code crablet.commands.api.base-path}
 * (default: {@code /api/commands}).
 * <p>
 * Every request must include a {@code commandType} field identifying the target command.
 * Only commands listed in a {@link com.crablet.command.web.CommandApiExposedCommands} bean
 * are reachable; all others return {@code 404 Not Found}.
 */
@RestController
class CommandApiRestController {

    private final CommandExecutor commandExecutor;
    private final ExposedCommandTypeRegistry exposedCommands;
    private final ObjectMapper objectMapper;

    CommandApiRestController(
            CommandExecutor commandExecutor,
            ExposedCommandTypeRegistry exposedCommands,
            ObjectMapper objectMapper) {
        this.commandExecutor = commandExecutor;
        this.exposedCommands = exposedCommands;
        this.objectMapper = objectMapper;
    }

    @GetMapping("${crablet.commands.api.base-path:/api/commands}")
    ResponseEntity<CommandApiExposedCommandsResponse> listExposedCommands() {
        List<CommandApiExposedCommandsResponse.Entry> entries = exposedCommands.exposedCommandsByType()
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new CommandApiExposedCommandsResponse.Entry(e.getKey(), e.getValue().getName()))
                .toList();
        return ResponseEntity.ok(new CommandApiExposedCommandsResponse(entries));
    }

    @PostMapping("${crablet.commands.api.base-path:/api/commands}")
    ResponseEntity<CommandApiResponse> executeCommand(@RequestBody JsonNode body, HttpServletRequest request) {
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

        ExecutionResult result = commandExecutor.execute(command, correlationId(request));
        if (result.wasCreated()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(CommandApiResponse.created());
        }
        return ResponseEntity.ok(CommandApiResponse.idempotent(result.reason()));
    }

    private static @Nullable UUID correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CommandApiCorrelationFilter.CORRELATION_ID_ATTRIBUTE);
        return value instanceof UUID uuid ? uuid : null;
    }
}
