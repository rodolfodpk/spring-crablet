package com.crablet.automations.internal;

import java.util.Optional;
import java.util.Set;

/**
 * Abstraction over crablet-views' ViewSubscription map, isolating the type reference
 * to the conditional configuration that activates only when views is on the classpath.
 */
public interface ViewSubscriptionLookup {

    /**
     * Returns the event types for the given view name, or empty if the view is not registered.
     */
    Optional<Set<String>> eventTypesForView(String viewName);
}
