package com.crablet.core;

/**
 * Exception thrown when event serialization/deserialization fails.
 * 
 * This helps diagnose event schema issues and includes the problematic
 * event type for debugging.
 */
public class SerializationException extends RuntimeException {
    private final String eventType;
    private final Class<?> eventClass;
    
    public SerializationException(String message, String eventType, Throwable cause) {
        super(message + " (eventType: " + eventType + ")", cause);
        this.eventType = eventType;
        this.eventClass = null;
    }
    
    public SerializationException(String message, Class<?> eventClass, Throwable cause) {
        super(message + " (eventClass: " + eventClass.getName() + ")", cause);
        this.eventType = null;
        this.eventClass = eventClass;
    }
}
