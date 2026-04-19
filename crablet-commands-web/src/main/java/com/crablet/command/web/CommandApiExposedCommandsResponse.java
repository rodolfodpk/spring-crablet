package com.crablet.command.web;

import java.util.List;

/**
 * Response body for {@code GET /api/commands} — lists commands currently reachable over HTTP.
 */
public record CommandApiExposedCommandsResponse(List<Entry> exposedCommands) {

    public record Entry(String commandType, String className) {}
}
