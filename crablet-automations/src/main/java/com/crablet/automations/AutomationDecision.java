package com.crablet.automations;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Decision returned by an {@link AutomationHandler}.
 *
 * <p>Automation handlers describe what should happen. The automation dispatcher
 * executes each decision with the framework-owned semantics for that decision type.
 */
public sealed interface AutomationDecision
        permits AutomationDecision.ExecuteCommand, AutomationDecision.NoOp {

    /**
     * Execute a command through {@code CommandExecutor}.
     *
     * @param command command to execute; must not be null
     */
    record ExecuteCommand(Object command) implements AutomationDecision {
        public ExecuteCommand {
            Objects.requireNonNull(command, "command must not be null");
        }
    }

    /**
     * Explicit no-op decision.
     *
     * @param reason optional reason for logging and tests
     */
    record NoOp(@Nullable String reason) implements AutomationDecision {
        /** Convenience factory when no reason is needed. */
        public static NoOp empty() {
            return new NoOp(null);
        }
    }
}
