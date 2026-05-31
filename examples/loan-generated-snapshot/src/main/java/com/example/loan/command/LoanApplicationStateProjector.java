package com.example.loan.command;

import com.crablet.eventstore.StoredEvent;
import com.crablet.eventstore.query.EventDeserializer;
import com.crablet.eventstore.query.StateProjector;
import com.example.loan.domain.LoanApplicationEvent;
import com.example.loan.domain.LoanApplicationSubmitted;

import java.util.List;
import java.util.Optional;

import static com.crablet.eventstore.EventType.type;

public class LoanApplicationStateProjector implements StateProjector<Optional<LoanApplicationState>> {

    @Override
    public List<String> getEventTypes() {
        return List.of(type(LoanApplicationSubmitted.class));
    }

    @Override
    public Optional<LoanApplicationState> getInitialState() {
        return Optional.empty();
    }

    @Override
    public Optional<LoanApplicationState> transition(
            Optional<LoanApplicationState> currentState,
            StoredEvent storedEvent,
            EventDeserializer deserializer
    ) {
        LoanApplicationEvent event = deserializer.deserialize(storedEvent, LoanApplicationEvent.class);
        if (event instanceof LoanApplicationSubmitted submitted) {
            return Optional.of(new LoanApplicationState(
                    submitted.applicationId(),
                    submitted.customerId(),
                    submitted.submittedAt(),
                    submitted.purpose(),
                    submitted.requestedAmount(),
                    true,
                    false
            ));
        }
        return currentState;
    }
}
