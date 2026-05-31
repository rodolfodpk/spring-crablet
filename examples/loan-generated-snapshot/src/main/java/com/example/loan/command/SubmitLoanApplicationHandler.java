package com.example.loan.command;

import com.crablet.command.CommandDecision;
import com.crablet.eventstore.AppendEvent;
import com.crablet.eventstore.ClockProvider;
import com.crablet.eventstore.EventStore;
import com.example.loan.domain.LoanApplicationSubmitted;
import org.springframework.stereotype.Component;

import static com.crablet.eventstore.EventType.type;
import static com.example.loan.command.LoanApplicationTags.APPLICATION_ID;
import static com.example.loan.command.LoanApplicationTags.CUSTOMER_ID;

@Component
public class SubmitLoanApplicationHandler implements SubmitLoanApplicationCommandHandler {

    private final ClockProvider clockProvider;

    public SubmitLoanApplicationHandler(ClockProvider clockProvider) {
        this.clockProvider = clockProvider;
    }

    @Override
    public CommandDecision.Idempotent decide(EventStore eventStore, SubmitLoanApplication command) {
        LoanApplicationSubmitted submitted = new LoanApplicationSubmitted(
                command.applicationId(),
                command.customerId(),
                command.requestedAmount(),
                command.purpose(),
                clockProvider.now()
        );
        AppendEvent appendEvent = AppendEvent.builder(type(LoanApplicationSubmitted.class))
                .tag(APPLICATION_ID, command.applicationId())
                .tag(CUSTOMER_ID, command.customerId())
                .data(submitted)
                .build();
        return CommandDecision.Idempotent.of(
                appendEvent,
                type(LoanApplicationSubmitted.class),
                APPLICATION_ID,
                command.applicationId()
        );
    }
}
