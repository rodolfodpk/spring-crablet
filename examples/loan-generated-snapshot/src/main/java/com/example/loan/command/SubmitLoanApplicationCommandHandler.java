package com.example.loan.command;

import com.crablet.command.IdempotentCommandHandler;

/**
 * Handle the SubmitLoanApplication command.
 *
 * <p>Append strategy: idempotent — entity creation (first submission per applicationId).
 * Create a {@code @Component} class implementing this interface and provide {@code decide()}.
 *
 * <pre>
 *   LoanApplicationSubmitted event = new LoanApplicationSubmitted(
 *       command.applicationId(), command.customerId(),
 *       command.requestedAmount(), command.purpose(), clockProvider.now());
 *   AppendEvent appendEvent = AppendEvent.builder(type(LoanApplicationSubmitted.class))
 *       .tag(APPLICATION_ID, command.applicationId())
 *       .tag(CUSTOMER_ID, command.customerId())
 *       .data(event).build();
 *   return CommandDecision.Idempotent.of(appendEvent,
 *       type(LoanApplicationSubmitted.class), APPLICATION_ID, command.applicationId());
 * *&#47;
 */
public interface SubmitLoanApplicationCommandHandler
        extends IdempotentCommandHandler<SubmitLoanApplication> {
}
