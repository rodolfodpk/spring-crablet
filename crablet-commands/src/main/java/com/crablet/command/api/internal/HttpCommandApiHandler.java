package com.crablet.command.api.internal;

import com.crablet.command.CommandExecutor;
import com.crablet.command.InvalidCommandException;
import com.crablet.eventstore.ConcurrencyException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.NullNode;

import java.io.IOException;

final class HttpCommandApiHandler {

    private final CommandApiController controller;
    private final ObjectMapper objectMapper;

    HttpCommandApiHandler(
            CommandExecutor commandExecutor,
            ExposedCommandTypeRegistry exposedCommands,
            ObjectMapper objectMapper) {
        this.controller = new CommandApiController(commandExecutor, exposedCommands, objectMapper);
        this.objectMapper = objectMapper;
    }

    ServerResponse executeCommand(ServerRequest request) throws Exception {
        try {
            JsonNode body = readBody(request.servletRequest());
            return toServerResponse(controller.execute(body));
        } catch (CommandApiBadRequestException e) {
            return problem(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (CommandNotExposedException e) {
            return problem(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (InvalidCommandException | IllegalArgumentException e) {
            return problem(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (ConcurrencyException e) {
            return problem(HttpStatus.CONFLICT, e.getMessage());
        } catch (Exception e) {
            return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected command API error");
        }
    }

    private JsonNode readBody(HttpServletRequest request) throws IOException {
        try {
            JsonNode body = objectMapper.readTree(request.getInputStream());
            return body == null ? NullNode.getInstance() : body;
        } catch (JacksonException e) {
            throw new CommandApiBadRequestException("Malformed JSON request body");
        }
    }

    private ServerResponse toServerResponse(ResponseEntity<?> response) throws Exception {
        ServerResponse.BodyBuilder builder = ServerResponse.status(response.getStatusCode())
                .contentType(MediaType.APPLICATION_JSON);
        response.getHeaders().forEach((name, values) -> values.forEach(value -> builder.header(name, value)));
        Object body = response.getBody();
        return body == null ? builder.build() : builder.body(body);
    }

    private ServerResponse problem(HttpStatus status, String detail) throws Exception {
        return ServerResponse.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
