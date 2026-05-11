package com.crablet.automations;

import com.crablet.eventstore.Stable;

import java.util.Set;

/**
 * Automation handler that declares which views it reads, so the framework can infer
 * wake events at startup from the matching {@code ViewSubscription} beans in the same JVM.
 *
 * <p>Implement this interface instead of {@link AutomationHandler} when your automation
 * reacts to state changes already tracked by one or more views. The framework resolves
 * the union of event types from the referenced view subscriptions, eliminating the need
 * to maintain a redundant {@code getEventTypes()} declaration.
 *
 * <p>Requirements:
 * <ul>
 *   <li>{@code crablet-views} must be on the classpath.</li>
 *   <li>Every name returned by {@link #getReadViewNames()} must match a registered
 *       {@code ViewSubscription} bean (by view name).</li>
 *   <li>Each referenced view must have at least one event type declared.</li>
 * </ul>
 * Startup fails with a descriptive message if any of these conditions are not met.
 *
 * <pre>{@code
 * @Component
 * public class EnrollmentAutomation implements EnrollmentAutomationHandler {
 *
 *     @Override
 *     public List<AutomationDecision> decide(StoredEvent event) {
 *         // read from enrollment_todo view, emit command
 *     }
 * }
 *
 * public interface EnrollmentAutomationHandler extends ViewBackedAutomationHandler {
 *
 *     @Override
 *     default String getAutomationName() { return "enrollment_automation"; }
 *
 *     @Override
 *     default Set<String> getReadViewNames() { return Set.of("enrollment_todo"); }
 * }
 * }</pre>
 *
 * <p>Do not override {@link #getEventTypes()} — it throws to prevent accidental direct use.
 * Use {@link #getWakeEventsExtra()} or {@link #getWakeEventsExclude()} for fine-grained control.
 */
@Stable
public interface ViewBackedAutomationHandler extends AutomationHandler {

    /** Names of views whose event subscriptions define this automation's wake events. */
    Set<String> getReadViewNames();

    /** Additional event types to wake on, beyond those inferred from referenced views. */
    default Set<String> getWakeEventsExtra() { return Set.of(); }

    /** Event types to exclude from the inferred wake-event set. Applied after extras. */
    default Set<String> getWakeEventsExclude() { return Set.of(); }

    /**
     * Do not call directly. Event types are resolved at startup from view subscriptions.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    default Set<String> getEventTypes() {
        throw new UnsupportedOperationException(
                "Event types for ViewBackedAutomationHandler are resolved from view subscriptions at startup. " +
                "Do not call getEventTypes() directly.");
    }
}
