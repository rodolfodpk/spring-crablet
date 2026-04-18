package com.crablet.eventpoller.internal.sharedfetch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ProcessorCursorStateMachine Unit Tests")
class ProcessorCursorStateMachineTest {

    private static final long HANDLED  = 100L;
    private static final long SCANNED  = 200L;
    private static final long WINDOW_END = 1000L;

    @Test
    @DisplayName("NoMatches: handledPosition unchanged, scannedPosition advances to windowEnd, no catch-up")
    void noMatches_advancesScannedToWindowEnd() {
        var update = ProcessorCursorStateMachine.compute(
                HANDLED, SCANNED, WINDOW_END, new DispatchOutcome.NoMatches());

        assertThat(update.newHandledPosition()).isEqualTo(HANDLED);
        assertThat(update.newScannedPosition()).isEqualTo(WINDOW_END);
        assertThat(update.enterCatchingUp()).isFalse();
    }

    @Test
    @DisplayName("Success: handledPosition advances to lastMatched, scannedPosition advances to windowEnd")
    void success_advancesBothCursors() {
        long lastMatched = 500L;
        var update = ProcessorCursorStateMachine.compute(
                HANDLED, SCANNED, WINDOW_END, new DispatchOutcome.Success(lastMatched));

        assertThat(update.newHandledPosition()).isEqualTo(lastMatched);
        assertThat(update.newScannedPosition()).isEqualTo(WINDOW_END);
        assertThat(update.enterCatchingUp()).isFalse();
    }

    @Test
    @DisplayName("Success: scannedPosition advances to windowEnd even when only one event matched early in window")
    void success_scannedAdvancesToWindowEndNotLastMatched() {
        long lastMatched = 150L; // early in the 100-1000 window
        var update = ProcessorCursorStateMachine.compute(
                HANDLED, SCANNED, WINDOW_END, new DispatchOutcome.Success(lastMatched));

        assertThat(update.newScannedPosition()).isEqualTo(WINDOW_END);
        assertThat(update.enterCatchingUp()).isFalse();
    }

    @Test
    @DisplayName("PartialDispatch: both cursors advance to lastDispatched, enters CATCHING_UP")
    void partialDispatch_entersCatchingUp() {
        long lastDispatched = 600L;
        var update = ProcessorCursorStateMachine.compute(
                HANDLED, SCANNED, WINDOW_END, new DispatchOutcome.PartialDispatch(lastDispatched));

        assertThat(update.newHandledPosition()).isEqualTo(lastDispatched);
        assertThat(update.newScannedPosition()).isEqualTo(lastDispatched);
        assertThat(update.enterCatchingUp()).isTrue();
    }

    @Test
    @DisplayName("PartialDispatch: scannedPosition equals handledPosition (not windowEnd)")
    void partialDispatch_scannedDoesNotJumpToWindowEnd() {
        long lastDispatched = 300L;
        var update = ProcessorCursorStateMachine.compute(
                HANDLED, SCANNED, WINDOW_END, new DispatchOutcome.PartialDispatch(lastDispatched));

        assertThat(update.newScannedPosition()).isEqualTo(lastDispatched);
        assertThat(update.newScannedPosition()).isNotEqualTo(WINDOW_END);
    }

    @Test
    @DisplayName("HandlerFailure: both cursors unchanged, enters CATCHING_UP")
    void handlerFailure_cursorsUnchanged_entersCatchingUp() {
        var update = ProcessorCursorStateMachine.compute(
                HANDLED, SCANNED, WINDOW_END, new DispatchOutcome.HandlerFailure());

        assertThat(update.newHandledPosition()).isEqualTo(HANDLED);
        assertThat(update.newScannedPosition()).isEqualTo(SCANNED);
        assertThat(update.enterCatchingUp()).isTrue();
    }

    @Test
    @DisplayName("HandlerFailure: scannedPosition stays at current value, not windowEnd")
    void handlerFailure_scannedDoesNotAdvance() {
        var update = ProcessorCursorStateMachine.compute(
                HANDLED, SCANNED, WINDOW_END, new DispatchOutcome.HandlerFailure());

        assertThat(update.newScannedPosition()).isEqualTo(SCANNED);
        assertThat(update.newScannedPosition()).isNotEqualTo(WINDOW_END);
    }

    @Test
    @DisplayName("NoMatches at position zero: scannedPosition advances to windowEnd")
    void noMatches_fromZero() {
        var update = ProcessorCursorStateMachine.compute(
                0L, 0L, WINDOW_END, new DispatchOutcome.NoMatches());

        assertThat(update.newHandledPosition()).isEqualTo(0L);
        assertThat(update.newScannedPosition()).isEqualTo(WINDOW_END);
        assertThat(update.enterCatchingUp()).isFalse();
    }
}
