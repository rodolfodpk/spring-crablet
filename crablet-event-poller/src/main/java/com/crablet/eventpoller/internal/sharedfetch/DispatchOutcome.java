package com.crablet.eventpoller.internal.sharedfetch;

/**
 * Result of dispatching a batch of matched events to a single processor in one shared cycle.
 */
public sealed interface DispatchOutcome
        permits DispatchOutcome.NoMatches,
                DispatchOutcome.Success,
                DispatchOutcome.PartialDispatch,
                DispatchOutcome.HandlerFailure {

    /** No events in the fetch window matched this processor's selection. */
    record NoMatches() implements DispatchOutcome {}

    /** All matched events were dispatched successfully. */
    record Success(long lastMatchedPosition) implements DispatchOutcome {}

    /**
     * Matched events exceeded processor batchSize; only the first batch was dispatched.
     * Processor must enter CATCHING_UP to handle the remainder.
     */
    record PartialDispatch(long lastDispatchedPosition) implements DispatchOutcome {}

    /**
     * Handler threw an exception. No partial progress is recorded —
     * {@code EventHandler} provides no intra-batch position signal.
     */
    record HandlerFailure() implements DispatchOutcome {}
}
