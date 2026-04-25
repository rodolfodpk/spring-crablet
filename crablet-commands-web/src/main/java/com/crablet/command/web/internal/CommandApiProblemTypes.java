package com.crablet.command.web.internal;

import java.net.URI;

/**
 * Stable problem type identifiers for the generic command API.
 */
final class CommandApiProblemTypes {

    static final URI BAD_REQUEST = URI.create("urn:crablet:problem:command-api:bad-request");
    static final URI MALFORMED_JSON = URI.create("urn:crablet:problem:command-api:malformed-json");
    static final URI COMMAND_NOT_EXPOSED = URI.create("urn:crablet:problem:command-api:command-not-exposed");
    static final URI INVALID_COMMAND = URI.create("urn:crablet:problem:command-api:invalid-command");
    static final URI DCB_CONCURRENCY = URI.create("urn:crablet:problem:command-api:dcb-concurrency");
    static final URI UNEXPECTED_ERROR = URI.create("urn:crablet:problem:command-api:unexpected-error");

    private CommandApiProblemTypes() {
    }
}
