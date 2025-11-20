package com.crablet.eventstore.store;

/**
 * Utility for extracting event type names from event classes.
 * <p>
 * Event types are derived from the class simple name, which matches
 * the name used in @JsonSubTypes annotations.
 */
public final class EventType {
    
    private EventType() {
        throw new UnsupportedOperationException("Utility class");
    }
    
    /**
     * Extract event type string from event class.
     * Uses the class simple name, which matches @JsonSubTypes name.
     * 
     * @param eventClass the event class
     * @return the event type string (class simple name)
     */
    public static String type(Class<?> eventClass) {
        return eventClass.getSimpleName();
    }
}

