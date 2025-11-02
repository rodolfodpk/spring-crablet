package com.crablet.examples.wallet.testutils;

import com.crablet.eventstore.store.AppendEvent;
import com.crablet.eventstore.query.EventDeserializer;
import com.crablet.eventstore.store.StoredEvent;
import com.crablet.eventstore.store.Tag;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.crablet.examples.wallet.domain.event.WalletEvent;
import com.crablet.examples.wallet.domain.event.WalletOpened;
import com.crablet.examples.wallet.domain.event.MoneyTransferred;
import com.crablet.examples.wallet.domain.event.DepositMade;
import com.crablet.examples.wallet.domain.event.WithdrawalMade;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Test utilities for wallet domain tests.
 * Provides helper methods for creating test data and assertions.
 */
public class WalletTestUtils {

    // Static singleton ObjectMapper to avoid expensive creation on every call
    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    /**
     * Create a StoredEvent from a WalletEvent for testing.
     */
    public static StoredEvent createEvent(WalletEvent walletEvent) {
        try {
            byte[] data = OBJECT_MAPPER.writeValueAsBytes(walletEvent);

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
     * Create a list of Events from WalletEvents for testing.
     */
    public static List<StoredEvent> createEventList(WalletEvent... events) {
        return Arrays.stream(events)
                .map(WalletTestUtils::createEvent)
                .collect(Collectors.toList());
    }

    /**
     * Deserialize event data to a specific type.
     */
    public static <T> T deserializeEventData(byte[] data, Class<T> clazz) {
        try {
            return OBJECT_MAPPER.readValue(data, clazz);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize event data", e);
        }
    }

    /**
     * Deserialize event data from Object to a specific type.
     * If object is already of type T, cast it directly. Otherwise serialize and deserialize.
     */
    @SuppressWarnings("unchecked")
    public static <T> T deserializeEventData(Object data, Class<T> clazz) {
        try {
            if (clazz.isInstance(data)) {
                return (T) data;
            }
            return OBJECT_MAPPER.convertValue(data, clazz);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize event data", e);
        }
    }


    /**
     * Create a WorkflowScenario for parameterized tests.
     */
    public static WorkflowScenario createWorkflowScenario(String name, int fromBalance, int toBalance, int transferAmount) {
        return new WorkflowScenario(name, fromBalance, toBalance, transferAmount);
    }

    /**
     * Create an AppendEvent with tags for testing.
     * Passes the event object directly to AppendEvent for serialization by EventStore.
     */
    public static AppendEvent createInputEvent(String type, List<Tag> tags, WalletEvent walletEvent) {
        AppendEvent.Builder builder = AppendEvent.builder(type);
        for (Tag tag : tags) {
            builder.tag(tag.key(), tag.value());
        }
        return builder.data(walletEvent).build();
    }

    /**
     * Create a simple AppendEvent without tags for testing.
     */
    public static AppendEvent createInputEvent(String type, WalletEvent walletEvent) {
        return createInputEvent(type, List.of(), walletEvent);
    }

    /**
     * Create a Tag for testing.
     */
    public static Tag createTag(String key, String value) {
        return new Tag(key, value);
    }

    /**
     * Create a list of Tags for testing.
     */
    public static List<Tag> createTagList(Tag... tags) {
        return Arrays.asList(tags);
    }

    /**
     * Create a wallet ID for testing.
     */
    public static String createWalletId(String prefix) {
        return prefix + "_" + System.currentTimeMillis();
    }

    /**
     * Create a transfer ID for testing.
     */
    public static String createTransferId(String prefix) {
        return prefix + "_" + System.currentTimeMillis();
    }

    /**
     * Create a test owner name.
     */
    public static String createOwnerName(String prefix) {
        return prefix + "_Owner";
    }

    /**
     * Create a test description.
     */
    public static String createDescription(String prefix) {
        return prefix + "_Description";
    }

    /**
     * Create a test reason.
     */
    public static String createReason(String prefix) {
        return prefix + "_Reason";
    }

    // Note: WalletState assertion method removed as WalletState is now package-private
    // Tests should be moved to the same package or use public APIs

    /**
     * Create a test EventDeserializer for deserializing events.
     */
    public static EventDeserializer createEventDeserializer() {
        return new EventDeserializer() {
            @Override
            public <E> E deserialize(StoredEvent event, Class<E> eventType) {
                try {
                    return OBJECT_MAPPER.readValue(event.data(), eventType);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to deserialize event", e);
                }
            }
        };
    }
}
