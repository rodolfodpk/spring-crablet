package com.crablet.eventstore;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Carries correlation and causation context down the call stack using {@link ScopedValue}.
 * <p>
 * {@link ScopedValue} is preferred over {@link ThreadLocal} for virtual-thread-heavy
 * workloads (Java 21+): values are immutable within a scope, garbage-collected when the
 * scope exits, and have zero overhead when unbound.
 * <p>
 * <strong>Usage patterns:</strong>
 * <pre>{@code
 * // HTTP layer — bind once per request, scope exits automatically:
 * ScopedValue.where(CorrelationContext.CORRELATION_ID, requestId)
 *            .run(() -> chain.doFilter(req, res));
 *
 * // Automation dispatcher — propagate + set causation for each event:
 * ScopedValue.where(CorrelationContext.CORRELATION_ID, event.correlationId())
 *            .where(CorrelationContext.CAUSATION_ID,   event.position())
 *            .run(() -> handler.react(event, commandExecutor));
 *
 * // EventStore append layer — read (null when unbound):
 * UUID cid  = CorrelationContext.correlationId();
 * Long caus = CorrelationContext.causationId();
 * }</pre>
 */
public final class CorrelationContext {

    /** The correlation ID for the current business operation thread. */
    public static final ScopedValue<UUID> CORRELATION_ID = ScopedValue.newInstance();

    /** The position of the event that directly caused the current operation. */
    public static final ScopedValue<Long> CAUSATION_ID = ScopedValue.newInstance();

    private CorrelationContext() {}

    /**
     * Returns the current correlation ID, or {@code null} if none is bound.
     */
    public static @Nullable UUID correlationId() {
        return CORRELATION_ID.isBound() ? CORRELATION_ID.get() : null;
    }

    /**
     * Returns the current causation ID (triggering event position), or {@code null} if none is bound.
     */
    public static @Nullable Long causationId() {
        return CAUSATION_ID.isBound() ? CAUSATION_ID.get() : null;
    }
}
