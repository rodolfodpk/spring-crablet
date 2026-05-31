package com.example.loan.command;

import am.ik.yavi.arguments.Arguments4Validator;
import am.ik.yavi.core.ConstraintViolationsException;
import am.ik.yavi.validator.Yavi;

public record SubmitLoanApplication(
        String applicationId,
        String customerId,
        int requestedAmount,
        String purpose
) {
    private static final Arguments4Validator<String, String, Integer, String, SubmitLoanApplication> validator =
            Yavi.arguments()
                    ._string("applicationId", c -> c.notNull().notBlank())
                    ._string("customerId", c -> c.notNull().notBlank())
                    ._integer("requestedAmount", c -> c.greaterThan(0))
                    ._string("purpose", c -> c.notNull().notBlank())
                    .apply(SubmitLoanApplication::new);

    public SubmitLoanApplication {
        try {
            validator.lazy().validated(applicationId, customerId, requestedAmount, purpose);
        } catch (ConstraintViolationsException e) {
            throw new IllegalArgumentException("Invalid SubmitLoanApplication: " + e.getMessage(), e);
        }
    }
}
