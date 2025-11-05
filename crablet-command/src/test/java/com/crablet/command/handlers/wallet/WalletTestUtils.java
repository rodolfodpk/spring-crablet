package com.crablet.command.handlers.wallet;

import com.crablet.eventstore.store.StoredEvent;
import com.crablet.eventstore.store.Tag;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.crablet.examples.wallet.domain.event.WalletEvent;
import com.crablet.examples.wallet.domain.event.WalletOpened;
import com.crablet.examples.wallet.domain.event.MoneyTransferred;
import com.crablet.examples.wallet.domain.event.DepositMade;
import com.crablet.examples.wallet.domain.event.WithdrawalMade;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Test utilities for wallet command handler tests.
 * Provides helper methods for creating test data and assertions.
 */
@Component
public class WalletTestUtils {

    private final ObjectMapper objectMapper;

    public WalletTestUtils(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Create a StoredEvent from a WalletEvent for testing.
     */
    public StoredEvent createEvent(WalletEvent walletEvent) {
        try {
            byte[] data = objectMapper.writeValueAsBytes(walletEvent);

            // Extract event type, tags, and timestamp in one pattern match
            String eventType;
            List<Tag> tags;
            Instant occurredAt;
            
            switch (walletEvent) {
                case WalletOpened e -> {
                    eventType = "WalletOpened";
                    tags = List.of(new Tag("wallet_id", e.walletId()));
                    occurredAt = e.openedAt();
                }
                case MoneyTransferred e -> {
                    eventType = "MoneyTransferred";
                    tags = List.of(
                            new Tag("transfer_id", e.transferId()),
                            new Tag("from_wallet_id", e.fromWalletId()),
                            new Tag("to_wallet_id", e.toWalletId())
                    );
                    occurredAt = e.transferredAt();
                }
                case DepositMade e -> {
                    eventType = "DepositMade";
                    tags = List.of(new Tag("wallet_id", e.walletId()));
                    occurredAt = e.depositedAt();
                }
                case WithdrawalMade e -> {
                    eventType = "WithdrawalMade";
                    tags = List.of(new Tag("wallet_id", e.walletId()));
                    occurredAt = e.withdrawnAt();
                }
            }

            return new StoredEvent(
                    eventType,
                    tags,
                    data,
                    "1", // Mock transaction ID
                    1L, // Mock position
                    occurredAt
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test event", e);
        }
    }

    /**
     * Deserialize event data to a specific type.
     */
    public <T> T deserializeEventData(byte[] data, Class<T> clazz) {
        try {
            return objectMapper.readValue(data, clazz);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize event data", e);
        }
    }

    /**
     * Deserialize event data from Object to a specific type.
     * If object is already of type T, cast it directly. Otherwise serialize and deserialize.
     */
    @SuppressWarnings("unchecked")
    public <T> T deserializeEventData(Object data, Class<T> clazz) {
        try {
            if (clazz.isInstance(data)) {
                return (T) data;
            }
            return objectMapper.convertValue(data, clazz);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize event data", e);
        }
    }
}

