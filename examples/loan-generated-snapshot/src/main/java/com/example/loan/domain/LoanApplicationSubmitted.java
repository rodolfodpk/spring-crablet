package com.example.loan.domain;

import java.time.Instant;

public record LoanApplicationSubmitted(
        String applicationId,
        String customerId,
        int requestedAmount,
        String purpose,
        Instant submittedAt
) implements LoanApplicationEvent {
}
