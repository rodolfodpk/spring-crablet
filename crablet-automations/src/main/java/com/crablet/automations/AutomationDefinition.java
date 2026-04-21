package com.crablet.automations;

import com.crablet.eventpoller.EventSelection;

/**
 * Shared matching contract for automations.
 *
 * <p>{@link AutomationHandler} declares matching filters independently of runtime
 * tuning and reaction logic.
 */
public interface AutomationDefinition extends EventSelection {

    /** Unique name identifying this automation. */
    String getAutomationName();
}
