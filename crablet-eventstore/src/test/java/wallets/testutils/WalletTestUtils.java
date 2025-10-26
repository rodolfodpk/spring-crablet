package wallets.testutils;

import com.crablet.eventstore.AppendEvent;
import com.crablet.eventstore.EventDeserializer;
import com.crablet.eventstore.StoredEvent;
import com.crablet.eventstore.Tag;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.wallets.domain.event.WalletEvent;

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

            // Create appropriate tags based on event type
            List<Tag> tags = switch (walletEvent) {
                case com.wallets.domain.event.WalletOpened wo -> List.of(new Tag("wallet_id", wo.walletId()));
                case com.wallets.domain.event.MoneyTransferred mt -> List.of(
                        new Tag("transfer_id", mt.transferId()),
                        new Tag("from_wallet_id", mt.fromWalletId()),
                        new Tag("to_wallet_id", mt.toWalletId())
                );
                case com.wallets.domain.event.DepositMade dm -> List.of(new Tag("wallet_id", dm.walletId()));
                case com.wallets.domain.event.WithdrawalMade wm -> List.of(new Tag("wallet_id", wm.walletId()));
                default -> List.of();
            };

            return new StoredEvent(
                    walletEvent.getEventType(),
                    tags,
                    data,
                    "1", // Mock transaction ID
                    1L, // Mock position
                    walletEvent.getOccurredAt()
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

    /**
     * Assert that two wallet events are equal ignoring timestamps.
     */
    public static void assertWalletEventsEqual(WalletEvent expected, WalletEvent actual) {
        if (expected == null && actual == null) return;
        if (expected == null || actual == null) {
            throw new AssertionError("One wallet event is null");
        }

        if (!expected.getEventType().equals(actual.getEventType())) {
            throw new AssertionError("Event types differ: expected=" + expected.getEventType() + ", actual=" + actual.getEventType());
        }
    }
}
