package com.example.loan.command;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

public class LoanApplicationQueryPatterns {

    private LoanApplicationQueryPatterns() {}

    public static boolean isSubmitted(Optional<LoanApplicationState> state) {
        return state.isPresent() && state.get().isSubmitted();
    }

    public static boolean isApplicationValid(Optional<LoanApplicationState> state) {
        if (!state.isPresent()) {
            return false;
        }
        LoanApplicationState stateValue = state.get();
        return stateValue.requestedAmount() > 0 &&
               !stateValue.purpose().isEmpty() &&
               stateValue.isSubmitted();
    }

    public static int calculateDaysSinceSubmission(Optional<LoanApplicationState> state) {
        if (!isSubmitted(state)) {
            throw new IllegalArgumentException("Application not submitted yet");
        }
        return (int) state.get().submittedAt().until(Instant.now(), ChronoUnit.DAYS);
    }
}
