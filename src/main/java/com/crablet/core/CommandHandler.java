package com.crablet.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * CommandHandler handles command execution and generates events.
 * Based on the Go implementation's CommandHandler interface.
 * 
 * Generic interface for type-safe command handling with self-identification.
 */
public interface CommandHandler<T extends Command> {
    
    /**
     * Handle a command following DCB pattern.
     * 
     * DCB Flow:
     * 1. Project decision model (read events via tags)
     * 2. Validate business rules from state
     * 3. Create events
     * 4. Build AppendCondition from projection
     * 5. Return events + condition
     * 
     * CommandExecutor will atomically append events with condition.
     * 
     * @param eventStore The event store for projections
     * @param command The command to handle
     * @return CommandResult with events and append condition
     */
    CommandResult handle(EventStore eventStore, T command);
    
    /**
     * Get the command type this handler processes.
     * Used for handler registry and routing.
     * 
     * @return Command type string (e.g., "open_wallet", "deposit")
     */
    String getCommandType();
    
    /**
     * Serialize an object to JSON string, converting JsonProcessingException to RuntimeException.
     * This default method provides a clean way to handle JSON serialization in command handlers
     * without try-catch blocks cluttering the business logic.
     * 
     * @param objectMapper The ObjectMapper to use for serialization
     * @param object The object to serialize
     * @return JSON string representation of the object
     * @throws RuntimeException if serialization fails (wraps JsonProcessingException)
     */
    static String serializeEvent(ObjectMapper objectMapper, Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize event", e);
        }
    }
}
