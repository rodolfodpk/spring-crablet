// Package and imports
package com.example.loan.command;
import java.time.Instant;

// Record definition
public record LoanApplicationState(
    String applicationId,
    String customerId,
    Instant submittedAt,
    String purpose,
    Integer requestedAmount,
    boolean isSubmitted,
    boolean isDecided
) {
    // Constructor for initial state creation
    public LoanApplicationState(
        String applicationId, 
        String customerId,
        Instant submittedAt, 
        String purpose, 
        Integer requestedAmount,
        boolean isSubmitted,
        boolean isDecided
    ) {
        // Assignment must be part of the parameter list
        this.applicationId = applicationId;
        this.customerId = customerId;
        this.submittedAt = submittedAt;
        this.purpose = purpose;
        this.requestedAmount = requestedAmount;
        this.isSubmitted = isSubmitted;
        this.isDecided = isDecided;
    }
}
