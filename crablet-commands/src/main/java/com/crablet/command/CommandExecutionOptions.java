package com.crablet.command;

import com.crablet.eventstore.Stable;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * Options for controlling command execution behaviour beyond the defaults.
 *
 * <p>Use {@link #builder()} to set only the values you need — both fields are independently
 * optional. Absence of a field means "use the default": no correlation context, no
 * client-supplied command ID.
 *
 * <pre>{@code
 * // correlation only
 * executor.execute(cmd, CommandExecutionOptions.builder()
 *         .correlationId(correlationId)
 *         .build());
 *
 * // idempotency only (UUID v7 recommended)
 * executor.execute(cmd, CommandExecutionOptions.builder()
 *         .commandId(commandId)
 *         .build());
 *
 * // both
 * executor.execute(cmd, CommandExecutionOptions.builder()
 *         .correlationId(correlationId)
 *         .commandId(commandId)
 *         .build());
 * }</pre>
 */
@Stable
public record CommandExecutionOptions(
        @Nullable UUID correlationId,
        @Nullable UUID commandId
) {
    public static CommandExecutionOptions defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private @Nullable UUID correlationId;
        private @Nullable UUID commandId;

        public Builder correlationId(UUID correlationId) {
            this.correlationId = Objects.requireNonNull(correlationId, "correlationId must not be null");
            return this;
        }

        public Builder commandId(UUID commandId) {
            this.commandId = Objects.requireNonNull(commandId, "commandId must not be null");
            return this;
        }

        public CommandExecutionOptions build() {
            return new CommandExecutionOptions(correlationId, commandId);
        }
    }
}
