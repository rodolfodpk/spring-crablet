package com.crablet.eventstore;

/**
 * Classifies a {@link DCBViolation}. Values are serialized to JSON via {@link #name()} at API
 * boundaries (e.g. the {@code violationCode} property in the commands-web 409 response), so the
 * constant names are a stable wire contract — see {@code crablet-eventstore/SCHEMA.md} and
 * {@code crablet-commands-web/README.md}.
 */
public enum DCBErrorCode {
    /** A concurrency/decision-model conflict, raised by the {@code append_events_if} PL/pgSQL function. */
    DCB_VIOLATION,
    /** A duplicate append matching an idempotency query, raised by {@code append_events_if}. */
    IDEMPOTENCY_VIOLATION,
    /** A commutative-guard (lifecycle) conflict, synthesized by {@code CommandExecutorImpl} from a DCB_VIOLATION. */
    GUARD_VIOLATION
}
