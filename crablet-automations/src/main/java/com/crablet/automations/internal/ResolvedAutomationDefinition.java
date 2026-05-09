package com.crablet.automations.internal;

import com.crablet.automations.AutomationDefinition;

import java.util.Set;

/**
 * Wraps an original AutomationDefinition with a resolved set of event types,
 * used when wake events are inferred from view subscriptions rather than declared directly.
 */
record ResolvedAutomationDefinition(
        AutomationDefinition original,
        Set<String> resolvedEventTypes
) implements AutomationDefinition {

    @Override
    public String getAutomationName() {
        return original.getAutomationName();
    }

    @Override
    public Set<String> getEventTypes() {
        return resolvedEventTypes;
    }

    @Override
    public Set<String> getRequiredTags() {
        return original.getRequiredTags();
    }

    @Override
    public Set<String> getAnyOfTags() {
        return original.getAnyOfTags();
    }
}
