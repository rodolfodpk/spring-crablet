package com.crablet.core;

/**
 * Generic command interface for the crablet library.
 * This interface should be implemented by domain-specific commands.
 */
public interface Command {

    /**
     * Get the command type identifier.
     */
    String getCommandType();

    /**
     * Get optional client-provided metadata as JSON string.
     * Clients can pass additional context like user_id, session_id, etc.
     *
     * @return JSON string with metadata, or null if no metadata provided
     */
    default String getMetadata() {
        return null;
    }
}
