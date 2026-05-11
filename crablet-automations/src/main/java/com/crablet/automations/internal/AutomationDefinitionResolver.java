package com.crablet.automations.internal;

import com.crablet.automations.AutomationDefinition;
import com.crablet.automations.AutomationHandler;
import com.crablet.automations.ViewBackedAutomationHandler;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves effective {@link AutomationDefinition} instances for all registered handlers.
 *
 * <p>Plain {@link AutomationHandler} instances pass through unchanged.
 * {@link ViewBackedAutomationHandler} instances have their event types inferred from
 * referenced view subscriptions via {@link ViewSubscriptionLookup}.
 */
public class AutomationDefinitionResolver {

    private final Map<String, AutomationHandler> handlers;
    private final Optional<ViewSubscriptionLookup> viewLookup;

    public AutomationDefinitionResolver(
            Map<String, AutomationHandler> handlers,
            Optional<ViewSubscriptionLookup> viewLookup) {
        this.handlers = handlers;
        this.viewLookup = viewLookup;
    }

    /**
     * Returns a map from automation name to its effective {@link AutomationDefinition}.
     * Fails with {@link IllegalStateException} if any view-backed automation cannot be resolved.
     */
    public Map<String, AutomationDefinition> resolve() {
        Map<String, AutomationDefinition> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, AutomationHandler> entry : handlers.entrySet()) {
            String name = entry.getKey();
            AutomationHandler handler = entry.getValue();
            if (handler instanceof ViewBackedAutomationHandler viewBacked) {
                resolved.put(name, resolveViewBacked(name, viewBacked));
            } else {
                resolved.put(name, handler);
            }
        }
        return Map.copyOf(resolved);
    }

    private ResolvedAutomationDefinition resolveViewBacked(
            String automationName, ViewBackedAutomationHandler handler) {

        if (handler.getReadViewNames().isEmpty()) {
            throw new IllegalStateException(
                    "View-backed automation '" + automationName + "' declares no read view names. " +
                    "Implement getReadViewNames() to return at least one view name.");
        }

        ViewSubscriptionLookup lookup = viewLookup.orElseThrow(() ->
                new IllegalStateException(
                        "View-backed automation '" + automationName + "' requires crablet-views on the classpath, " +
                        "but no ViewSubscriptionLookup bean was found. " +
                        "Ensure crablet-views is on the classpath."));

        Set<String> wakeEvents = new LinkedHashSet<>();

        for (String viewName : handler.getReadViewNames()) {
            Set<String> viewEventTypes = lookup.eventTypesForView(viewName)
                    .orElseThrow(() -> new IllegalStateException(
                            "View-backed automation '" + automationName + "' references view '" + viewName + "', " +
                            "but no ViewSubscription was found with that name. " +
                            "Ensure the view subscription bean is registered."));

            if (viewEventTypes.isEmpty()) {
                throw new IllegalStateException(
                        "View-backed automation '" + automationName + "' references view '" + viewName + "', " +
                        "but that view has no event types declared. " +
                        "A view-backed automation requires at least one event type per referenced view.");
            }

            wakeEvents.addAll(viewEventTypes);
        }

        handler.getWakeEventsExtra().forEach(wakeEvents::add);
        handler.getWakeEventsExclude().forEach(wakeEvents::remove);

        if (wakeEvents.isEmpty()) {
            throw new IllegalStateException(
                    "View-backed automation '" + automationName + "' has no wake events after applying " +
                    "view subscriptions, wakeEventsExtra, and wakeEventsExclude. " +
                    "An empty wake-event set would match all events. " +
                    "Check that wakeEventsExclude does not remove all inferred events.");
        }

        return new ResolvedAutomationDefinition(handler, Set.copyOf(wakeEvents));
    }
}
