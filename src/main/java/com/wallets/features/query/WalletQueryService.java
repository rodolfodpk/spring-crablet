package com.wallets.features.query;

import com.crablet.core.Event;
import com.crablet.core.EventStore;
import com.crablet.core.Query;
import com.crablet.core.QueryItem;
import com.crablet.core.Tag;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * WalletQueryService handles read operations with optimized queries.
 * 
 * This service handles:
 * - Build queries for read-only operations
 * - Coordinate event fetching and state projection
 * - Pagination and DTO conversion for history endpoint
 * - Uses WalletStateProjector for read-specific projections
 */
@Service
public class WalletQueryService {

    private static final Logger log = LoggerFactory.getLogger(WalletQueryService.class);
    
    private final EventStore eventStore;
    private final ObjectMapper objectMapper;
    private final WalletQueryRepository queryRepository;

    public WalletQueryService(EventStore eventStore, ObjectMapper objectMapper, WalletQueryRepository queryRepository) {
        this.eventStore = eventStore;
        this.objectMapper = objectMapper;
        this.queryRepository = queryRepository;
    }

    /**
     * Get current wallet state (current balance).
     * 
     * @param walletId The wallet ID
     * @return WalletResponse or null if wallet doesn't exist
     */
    public WalletResponse getWalletState(String walletId) {
        try {
            // Query events for this wallet using OR logic (go-crablet style)
            Query walletQuery = buildCompleteWalletEventsQuery(walletId);
            List<Event> events = eventStore.query(walletQuery, null);
            
            // Create projector for this specific wallet
            WalletStateProjector projector = new WalletStateProjector(walletId, objectMapper);
            
            // Use WalletStateProjector for read operations
            WalletState walletState = projector.getInitialState();
            for (Event event : events) {
                walletState = projector.transition(walletState, event);
            }

            if (walletState.isEmpty()) {
                return null;
            }

            return WalletResponse.from(walletState);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get historical wallet events with pagination.
     * 
     * @param walletId The wallet ID
     * @param timestamp The target timestamp (events up to this time)
     * @param page The page number (0-based)
     * @param size The page size
     * @return WalletHistoryResponse with paginated events
     */
    public WalletHistoryResponse getWalletHistory(String walletId, Instant timestamp, int page, int size) {
        try {
            // Query events for this wallet using OR logic (go-crablet style)
            Query walletHistoryQuery = buildWalletHistoryQuery(walletId);
            List<Event> walletEvents = eventStore.query(walletHistoryQuery, null);
            
            log.debug("Query for wallet {} returned {} events, timestamp: {}", walletId, walletEvents.size(), timestamp);
            
            // Filter events to only include those up to the target time
            List<Event> filteredEvents = filterEventsByTimestamp(walletEvents, timestamp);
            
            log.debug("After timestamp filtering: {} events", filteredEvents.size());

            // Apply pagination
            List<Event> paginatedEvents = paginateEvents(filteredEvents, page, size);
            int totalEvents = filteredEvents.size();
            boolean hasNext = (page + 1) * size < totalEvents;

            // Convert events to DTOs
            List<WalletEventDTO> eventDTOs = convertToEventDTOs(paginatedEvents);

            return new WalletHistoryResponse(
                eventDTOs,
                page,
                size,
                totalEvents,
                hasNext
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to get wallet history", e);
        }
    }

    /**
     * Get wallet commands with pagination and timestamp filtering.
     * 
     * @param walletId The wallet ID
     * @param timestamp Filter commands after this timestamp (optional)
     * @param page Page number (0-based)
     * @param size Page size
     * @return WalletCommandsResponse with paginated commands
     */
    public WalletCommandsResponse getWalletCommands(String walletId, Instant timestamp, int page, int size) {
        try {
            // Query commands for this wallet using repository
            List<CommandResponse> commands = queryRepository.getCommandsForWallet(walletId, timestamp, page, size);
            
            // Calculate pagination info (repository returns size+1 to detect hasNext)
            boolean hasNext = commands.size() > size;
            if (hasNext) {
                commands = commands.subList(0, size); // Remove the extra item
            }
            
            // Convert to DTOs
            List<WalletCommandDTO> commandDTOs = convertToCommandDTOs(commands);
            long totalCommands = queryRepository.getTotalCommandsForWallet(walletId, timestamp);
            
            return new WalletCommandsResponse(
                commandDTOs,
                page,
                size,
                totalCommands,
                hasNext
            );
            
        } catch (Exception e) {
            log.error("Error getting wallet commands for wallet: {}", walletId, e);
            throw new RuntimeException("Failed to get wallet commands", e);
        }
    }

    /**
     * Build complete wallet events query for all event types affecting this wallet.
     * 
     * @param walletId The wallet ID
     * @return Query for all wallet-related events
     */
    private Query buildCompleteWalletEventsQuery(String walletId) {
        return Query.of(List.of(
            QueryItem.of(
                List.of("WalletOpened", "MoneyTransferred", "DepositMade", "WithdrawalMade"),
                List.of(new Tag("wallet_id", walletId))
            ),
            QueryItem.of(
                List.of("MoneyTransferred"),
                List.of(new Tag("from_wallet_id", walletId))
            ),
            QueryItem.of(
                List.of("MoneyTransferred"),
                List.of(new Tag("to_wallet_id", walletId))
            )
        ));
    }

    /**
     * Build wallet history query using only tags (no event type filter) for comprehensive history.
     * 
     * @param walletId The wallet ID
     * @return Query for all events tagged with wallet-related tags
     */
    private Query buildWalletHistoryQuery(String walletId) {
        return Query.of(List.of(
            QueryItem.ofTags(List.of(new Tag("wallet_id", walletId))),
            QueryItem.ofTags(List.of(new Tag("from_wallet_id", walletId))),
            QueryItem.ofTags(List.of(new Tag("to_wallet_id", walletId)))
        ));
    }

    /**
     * Filter events by timestamp and sort by occurredAt.
     * 
     * @param events The events to filter
     * @param targetTime The target timestamp (null means no filtering)
     * @return Filtered and sorted events
     */
    private List<Event> filterEventsByTimestamp(List<Event> events, Instant targetTime) {
        log.debug("Filtering {} events with targetTime: {}", events.size(), targetTime);
        return events.stream()
            .filter(event -> {
                boolean include = targetTime == null || !event.occurredAt().isAfter(targetTime);
                log.debug("Event {} at {} - include: {} (targetTime: {})", 
                    event.type(), event.occurredAt(), include, targetTime);
                return include;
            })
            .sorted((e1, e2) -> e2.occurredAt().compareTo(e1.occurredAt()))
            .collect(Collectors.toList());
    }

    /**
     * Apply pagination to events.
     * 
     * @param events The events to paginate
     * @param page The page number (0-based)
     * @param size The page size
     * @return Paginated events
     */
    private List<Event> paginateEvents(List<Event> events, int page, int size) {
        int totalEvents = events.size();
        int startIndex = page * size;
        int endIndex = Math.min(startIndex + size, totalEvents);
        
        if (startIndex >= totalEvents) {
            return List.of();
        }
        
        return events.subList(startIndex, endIndex);
    }

    /**
     * Convert events to DTOs.
     * 
     * @param events The events to convert
     * @return List of WalletEventDTO
     */
    private List<WalletEventDTO> convertToEventDTOs(List<Event> events) {
        return events.stream()
            .map(event -> {
                try {
                    // Parse the JSON data from bytes
                    String jsonString = new String(event.data());
                    @SuppressWarnings("unchecked")
                    Map<String, Object> dataMap = objectMapper.readValue(jsonString, Map.class);
                    
                    return new WalletEventDTO(
                        event.type(),
                        event.occurredAt(),
                        dataMap
                    );
                } catch (Exception e) {
                    // Fallback to raw data if JSON parsing fails
                    return new WalletEventDTO(
                        event.type(),
                        event.occurredAt(),
                        Map.of("data", new String(event.data()))
                    );
                }
            })
            .collect(Collectors.toList());
    }

    /**
     * Convert CommandResponse list to WalletCommandDTO list with parsed JSON data.
     */
    private List<WalletCommandDTO> convertToCommandDTOs(List<CommandResponse> commands) {
        return commands.stream()
            .map(command -> {
                try {
                    // Parse the JSON data - data is a String, parse it to Map
                    Map<String, Object> dataMap;
                    if (command.data() instanceof String) {
                        dataMap = objectMapper.readValue((String) command.data(), Map.class);
                    } else {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> castedMap = (Map<String, Object>) command.data();
                        dataMap = castedMap;
                    }

                    // Convert events to DTOs
                    List<WalletEventDTO> eventDTOs = command.events().stream()
                        .map(event -> {
                            try {
                                // Event data might be a String, parse it to Map
                                Map<String, Object> eventDataMap;
                                if (event.data() instanceof String) {
                                    eventDataMap = objectMapper.readValue((String) event.data(), Map.class);
                                } else {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> castedMap = (Map<String, Object>) event.data();
                                    eventDataMap = castedMap;
                                }
                                return new WalletEventDTO(
                                    event.type(),
                                    event.occurredAt(),
                                    eventDataMap
                                );
                            } catch (Exception e) {
                                log.error("Error parsing event data for event type {}: {}", event.type(), e.getMessage(), e);
                                return new WalletEventDTO(
                                    event.type(),
                                    event.occurredAt(),
                                    Map.of("data", event.data())
                                );
                            }
                        })
                        .collect(Collectors.toList());

                    return new WalletCommandDTO(
                        command.transactionId(),
                        command.type(),
                        dataMap,
                        command.occurredAt(),
                        eventDTOs
                    );
                } catch (Exception e) {
                    // Fallback to raw data if JSON parsing fails
                    return new WalletCommandDTO(
                        command.transactionId(),
                        command.type(),
                        Map.of("data", command.data()),
                        command.occurredAt(),
                        List.of()
                    );
                }
            })
            .collect(Collectors.toList());
    }
}
