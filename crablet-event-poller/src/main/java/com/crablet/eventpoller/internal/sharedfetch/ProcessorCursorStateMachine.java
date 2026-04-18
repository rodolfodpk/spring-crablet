package com.crablet.eventpoller.internal.sharedfetch;

/**
 * Pure function that computes cursor updates from a dispatch outcome.
 * Contains no I/O or Spring wiring — safe to unit-test in isolation.
 */
public final class ProcessorCursorStateMachine {

    private ProcessorCursorStateMachine() {}

    /**
     * @param currentHandledPosition  persisted handledPosition before this cycle
     * @param currentScannedPosition  persisted scannedPosition before this cycle
     * @param windowEnd               last position fetched by the module in this cycle
     * @param outcome                 what happened when the processor was dispatched
     * @return the cursor values to persist and whether CATCHING_UP should be set
     */
    public static CursorUpdate compute(
            long currentHandledPosition,
            long currentScannedPosition,
            long windowEnd,
            DispatchOutcome outcome) {
        return switch (outcome) {
            case DispatchOutcome.NoMatches() ->
                    new CursorUpdate(currentHandledPosition, windowEnd, false);
            case DispatchOutcome.Success(long lastMatchedPosition) ->
                    new CursorUpdate(lastMatchedPosition, windowEnd, false);
            case DispatchOutcome.PartialDispatch(long lastDispatchedPosition) ->
                    new CursorUpdate(lastDispatchedPosition, lastDispatchedPosition, true);
            case DispatchOutcome.HandlerFailure() ->
                    new CursorUpdate(currentHandledPosition, currentScannedPosition, true);
        };
    }
}
