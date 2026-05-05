package com.crablet.automations;

import com.crablet.eventstore.Stable;
import com.crablet.eventpoller.EventSelection;
import org.jspecify.annotations.NonNull;

/**
 * Shared matching contract for automations.
 *
 * <p>{@link AutomationHandler} declares matching filters independently of runtime
 * tuning and reaction logic.
 */
@Stable
public interface AutomationDefinition extends EventSelection {

    /** Unique name identifying this automation. */
    @NonNull String getAutomationName();
}
