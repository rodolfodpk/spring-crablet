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
}
