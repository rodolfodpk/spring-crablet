package com.crablet.automations;

import java.util.Set;

/**
 * Shared matching contract for automations.
 *
 * <p>Both webhook-based {@link AutomationSubscription} and in-process
 * {@link AutomationHandler} definitions declare the same event and tag filters.
 */
public interface AutomationDefinition {

    /** Unique name identifying this automation. */
    String getAutomationName();

    /** Event types that trigger this automation. Empty set means all events. */
    Set<String> getEventTypes();

    /** ALL of these tag keys must be present on the event to trigger this automation. */
    Set<String> getRequiredTags();

    /** At least ONE of these tag keys must be present on the event to trigger this automation. */
    Set<String> getAnyOfTags();
}
