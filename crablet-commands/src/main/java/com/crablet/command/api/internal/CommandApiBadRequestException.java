package com.crablet.command.api.internal;

/**
 * Internal exception for request-shape or command-type problems at the REST boundary.
 */
final class CommandApiBadRequestException extends RuntimeException {

    CommandApiBadRequestException(String message) {
        super(message);
    }
}
