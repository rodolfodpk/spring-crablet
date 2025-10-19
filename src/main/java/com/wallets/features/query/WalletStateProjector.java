package com.wallets.features.query;

import com.crablet.core.EventStore;
import com.crablet.core.Query;
import com.crablet.core.StateProjector;
import com.crablet.core.StoredEvent;
import com.crablet.core.Tag;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallets.domain.event.DepositMade;
import com.wallets.domain.event.MoneyTransferred;
import com.wallets.domain.event.WalletEvent;
import com.wallets.domain.event.WalletOpened;
import com.wallets.domain.event.WithdrawalMade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * WalletStateProjector reconstructs wallet state from events.
 * This handles both WalletOpened and MoneyTransferred events.
 */
public class WalletStateProjector implements StateProjector<WalletState> {

    private static final Logger log = LoggerFactory.getLogger(WalletStateProjector.class);

    private final String walletId;
    private final ObjectMapper objectMapper;

    public WalletStateProjector(String walletId, ObjectMapper objectMapper) {
        this.walletId = walletId;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getId() {
        return "wallet-" + walletId;
    }

    @Override
    public List<String> getEventTypes() {
        return List.of("WalletOpened", "MoneyTransferred", "DepositMade", "WithdrawalMade");
    }

    @Override
    public List<Tag> getTags() {
        return List.of(new Tag("wallet_id", walletId));
    }

    @Override
    public WalletState getInitialState() {
        return WalletState.empty();
    }

    /**
     * Project wallet state from EventStore using optimized batch deserialization.
     * This is the preferred method for better performance.
     *
     * @param store The event store to query
     * @param query The query to filter events
     * @return Final wallet state after projecting all events
     */
    public WalletState project(EventStore store, Query query) {
        try {
            // Use new batch deserialization method for better performance
            byte[] eventsJsonArray = store.queryAsJsonArray(query, null);
            WalletEvent[] walletEvents = objectMapper.readValue(eventsJsonArray, WalletEvent[].class);
            
            return projectFromWalletEvents(walletEvents);
        } catch (Exception e) {
            throw new RuntimeException("Failed to project wallet state from EventStore", e);
        }
    }

    /**
     * Project wallet state from multiple events efficiently using batch deserialization.
     * This is the optimized method that should be used instead of individual transition() calls.
     *
     * @param events List of events to project from
     * @return Final wallet state after projecting all events
     * @deprecated Use project(EventStore, Query) for better performance
     */
    @Deprecated
    public WalletState project(List<StoredEvent> events) {
        if (events.isEmpty()) {
            return getInitialState();
        }

        // Batch deserialize all events for better performance
        List<byte[]> eventDataList = events.stream()
                .map(StoredEvent::data)
                .toList();

        // Build JSON array manually for backward compatibility
        StringBuilder jsonArray = new StringBuilder("[");
        for (int i = 0; i < eventDataList.size(); i++) {
            if (i > 0) jsonArray.append(",");
            jsonArray.append(new String(eventDataList.get(i), java.nio.charset.StandardCharsets.UTF_8));
        }
        jsonArray.append("]");

        try {
            WalletEvent[] walletEvents = objectMapper.readValue(jsonArray.toString(), WalletEvent[].class);
            return projectFromWalletEvents(walletEvents);
        } catch (Exception e) {
            throw new RuntimeException("Failed to batch deserialize wallet events", e);
        }
    }

    /**
     * Project wallet state from deserialized WalletEvent array.
     *
     * @param walletEvents Array of deserialized wallet events
     * @return Final wallet state after projecting all events
     */
    private WalletState projectFromWalletEvents(WalletEvent[] walletEvents) {
        if (walletEvents.length == 0) {
            return getInitialState();
        }

        WalletState state = getInitialState();

        // Process each deserialized event
        for (WalletEvent walletEvent : walletEvents) {
            // Apply the same logic as transition() but with pre-deserialized events
            state = switch (walletEvent) {
                case WalletOpened opened when walletId.equals(opened.walletId()) -> new WalletState(
                        opened.walletId(),
                        opened.owner(),
                        opened.initialBalance(),
                        opened.openedAt(),
                        opened.openedAt()
                );

                case MoneyTransferred transfer when walletId.equals(transfer.fromWalletId()) ->
                        state.withBalance(transfer.fromBalance(), transfer.transferredAt());

                case MoneyTransferred transfer when walletId.equals(transfer.toWalletId()) ->
                        state.withBalance(transfer.toBalance(), transfer.transferredAt());

                case DepositMade deposit when walletId.equals(deposit.walletId()) ->
                        state.withBalance(deposit.newBalance(), deposit.depositedAt());

                case WithdrawalMade withdrawal when walletId.equals(withdrawal.walletId()) ->
                        state.withBalance(withdrawal.newBalance(), withdrawal.withdrawnAt());

                default -> state; // Event not relevant to this wallet
            };
        }

        return state;
    }

    @Override
    public WalletState transition(WalletState currentState, StoredEvent event) {
        // Parse event data as WalletEvent using sealed interface
        // Since we fail-fast on any deserialization error, no try-catch needed
        WalletEvent walletEvent;
        try {
            walletEvent = objectMapper.readValue(event.data(), WalletEvent.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize WalletEvent", e);
        }

        // Use pattern matching for type-safe event handling
        return switch (walletEvent) {
            case WalletOpened opened when walletId.equals(opened.walletId()) -> new WalletState(
                    opened.walletId(),
                    opened.owner(),
                    opened.initialBalance(),
                    opened.openedAt(),
                    opened.openedAt()
            );

            case MoneyTransferred transfer when walletId.equals(transfer.fromWalletId()) ->
                    currentState.withBalance(transfer.fromBalance(), transfer.transferredAt());

            case MoneyTransferred transfer when walletId.equals(transfer.toWalletId()) ->
                    currentState.withBalance(transfer.toBalance(), transfer.transferredAt());

            case DepositMade deposit when walletId.equals(deposit.walletId()) ->
                    currentState.withBalance(deposit.newBalance(), deposit.depositedAt());

            case WithdrawalMade withdrawal when walletId.equals(withdrawal.walletId()) ->
                    currentState.withBalance(withdrawal.newBalance(), withdrawal.withdrawnAt());


            default -> currentState; // Event not relevant to this wallet
        };
    }
}
