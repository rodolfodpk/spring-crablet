package com.crablet.eventpoller.internal.sharedfetch;

/**
 * Cursor positions to persist for a processor after one shared dispatch cycle.
 *
 * @param newHandledPosition  position of the last successfully handled matching event
 * @param newScannedPosition  how far this processor has safely considered in the event log
 * @param enterCatchingUp     true when the processor must run a bounded catch-up before
 *                            rejoining shared fan-out
 */
public record CursorUpdate(
        long newHandledPosition,
        long newScannedPosition,
        boolean enterCatchingUp
) {}
