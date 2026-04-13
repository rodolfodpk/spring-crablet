package com.crablet.command.web.internal;

/**
 * Internal exception for known-but-not-exposed commands.
 */
final class CommandNotExposedException extends RuntimeException {

    CommandNotExposedException(String commandType) {
        super("Command type is not exposed: " + commandType);
    }
}
