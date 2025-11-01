package com.crablet.examples.wallet.domain.event;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Instant;

/**
 * Sealed interface for all wallet-related events.
 * This provides type safety and enables pattern matching.
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "eventType"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = WalletOpened.class, name = "WalletOpened"),
        @JsonSubTypes.Type(value = MoneyTransferred.class, name = "MoneyTransferred"),
        @JsonSubTypes.Type(value = DepositMade.class, name = "DepositMade"),
        @JsonSubTypes.Type(value = WithdrawalMade.class, name = "WithdrawalMade")
})
public sealed interface WalletEvent
        permits WalletOpened, MoneyTransferred, DepositMade, WithdrawalMade {

    /**
     * Get the event type identifier.
     */
    @JsonIgnore
    String getEventType();

    /**
     * Get the timestamp when the event occurred.
     */
    @JsonIgnore
    Instant getOccurredAt();
}